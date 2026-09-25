package com.disktree.app

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanNode(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val children: List<ScanNode> = emptyList(),
)

data class VisibleNode(
    val node: ScanNode,
    val depth: Int,
    val share: Float,
)

enum class ScanScope {
    SHARED_STORAGE,
    ROOT_DEVICE,
}

enum class RootAccess {
    UNKNOWN,
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
}

enum class SizeMode {
    LOGICAL,
    ALLOCATED,
}

sealed interface ScanPhase {
    data object Idle : ScanPhase
    data object Scanning : ScanPhase
    data object Complete : ScanPhase
    data class Failed(val message: String) : ScanPhase
}

sealed interface FileActionPhase {
    data object Idle : FileActionPhase
    data object Working : FileActionPhase
    data class Failed(val message: String) : FileActionPhase
}

data class StorageCapacity(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
)

data class DiskTreeUiState(
    val scope: ScanScope = ScanScope.SHARED_STORAGE,
    val sizeMode: SizeMode = SizeMode.LOGICAL,
    val phase: ScanPhase = ScanPhase.Idle,
    val progress: ScanProgress = ScanProgress(),
    val root: ScanNode? = null,
    val expandedPaths: Set<String> = emptySet(),
    val selectedPath: String? = null,
    val selectedNode: ScanNode? = null,
    val rootAccess: RootAccess = RootAccess.UNKNOWN,
    val fileAction: FileActionPhase = FileActionPhase.Idle,
    val capacity: StorageCapacity? = null,
    val warning: String? = null,
)

fun flattenTree(root: ScanNode, expandedPaths: Set<String>): List<VisibleNode> {
    val result = mutableListOf<VisibleNode>()
    appendNode(root, 0, 1f, expandedPaths, result)
    return result
}

private fun appendNode(
    node: ScanNode,
    depth: Int,
    share: Float,
    expandedPaths: Set<String>,
    result: MutableList<VisibleNode>,
) {
    result += VisibleNode(node, depth, share)
    if (!expandedPaths.contains(node.path)) return
    node.children.forEach { child ->
        val childShare = if (node.sizeBytes > 0L) {
            child.sizeBytes.toDouble() / node.sizeBytes.toDouble()
        } else {
            0f
        }
        appendNode(child, depth + 1, childShare.toFloat(), expandedPaths, result)
    }
}

class DiskTreeViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = DiskScanner()
    private val appDataPath = application.dataDir
    private val sharedStoragePath = Environment.getExternalStorageDirectory().absolutePath
    private var lastStorageAccess = false
    private val _state = MutableStateFlow(
        DiskTreeUiState(capacity = readCapacity(File(sharedStoragePath))),
    )
    val state: StateFlow<DiskTreeUiState> = _state.asStateFlow()
    private var scanJob: Job? = null
    private var rootCheckJob: Job? = null

    fun selectScope(scope: ScanScope) {
        if (_state.value.phase == ScanPhase.Scanning || _state.value.fileAction == FileActionPhase.Working) return
        if (_state.value.scope == scope) {
            if (scope == ScanScope.ROOT_DEVICE && _state.value.rootAccess != RootAccess.AVAILABLE) {
                checkRootAccess()
            }
            return
        }
        val path = if (scope == ScanScope.ROOT_DEVICE) "/data" else sharedStoragePath
        _state.update {
            it.copy(
                scope = scope,
                phase = ScanPhase.Idle,
                progress = ScanProgress(),
                root = null,
                expandedPaths = emptySet(),
                selectedPath = null,
                selectedNode = null,
                rootAccess = RootAccess.UNKNOWN,
                fileAction = FileActionPhase.Idle,
                capacity = readCapacity(File(path)),
                warning = null,
            )
        }
        if (scope == ScanScope.ROOT_DEVICE) checkRootAccess()
    }

    fun selectSizeMode(sizeMode: SizeMode) {
        if (
            _state.value.phase == ScanPhase.Scanning ||
            _state.value.fileAction == FileActionPhase.Working ||
            _state.value.sizeMode == sizeMode
        ) return
        _state.update {
            it.copy(
                sizeMode = sizeMode,
                phase = ScanPhase.Idle,
                progress = ScanProgress(),
                root = null,
                expandedPaths = emptySet(),
                selectedPath = null,
                selectedNode = null,
                fileAction = FileActionPhase.Idle,
                warning = null,
            )
        }
    }

    fun checkRootAccess() {
        val current = _state.value
        if (
            current.scope != ScanScope.ROOT_DEVICE ||
            current.phase == ScanPhase.Scanning ||
            current.rootAccess == RootAccess.CHECKING
        ) {
            return
        }
        rootCheckJob?.cancel()
        _state.update { it.copy(rootAccess = RootAccess.CHECKING) }
        rootCheckJob = viewModelScope.launch {
            try {
                val available = scanner.isRootAvailable()
                _state.update { state ->
                    if (state.scope == ScanScope.ROOT_DEVICE) {
                        state.copy(
                            rootAccess = if (available) RootAccess.AVAILABLE else RootAccess.UNAVAILABLE,
                        )
                    } else {
                        state
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(rootAccess = RootAccess.UNAVAILABLE) }
            }
        }
    }

    fun scan(hasStorageAccess: Boolean) {
        lastStorageAccess = hasStorageAccess
        if (_state.value.phase == ScanPhase.Scanning || _state.value.fileAction == FileActionPhase.Working) return
        val scope = _state.value.scope
        if (scope == ScanScope.SHARED_STORAGE && !hasStorageAccess) {
            _state.update {
                it.copy(
                    phase = ScanPhase.Failed("File access is required before scanning shared storage."),
                    fileAction = FileActionPhase.Idle,
                )
            }
            return
        }
        if (scope == ScanScope.ROOT_DEVICE && _state.value.rootAccess != RootAccess.AVAILABLE) {
            checkRootAccess()
            return
        }
        val sizeMode = _state.value.sizeMode

        scanJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    phase = ScanPhase.Scanning,
                    progress = ScanProgress(),
                    root = null,
                    expandedPaths = emptySet(),
                    selectedPath = null,
                    selectedNode = null,
                    fileAction = FileActionPhase.Idle,
                    warning = null,
                )
            }
            try {
                val result = scanner.scan(
                    useRoot = scope == ScanScope.ROOT_DEVICE,
                    sharedStoragePath = sharedStoragePath,
                    sizeMode = sizeMode,
                ) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                _state.update {
                    it.copy(
                        phase = ScanPhase.Complete,
                        root = result.root,
                        expandedPaths = setOf(result.root.path),
                        selectedPath = null,
                        selectedNode = null,
                        fileAction = FileActionPhase.Idle,
                        warning = result.warning,
                    )
                }
            } catch (error: CancellationException) {
                _state.update { it.copy(phase = ScanPhase.Idle, progress = ScanProgress()) }
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        phase = ScanPhase.Failed(error.message?.lineSequence()?.firstOrNull()?.take(180) ?: "The storage scan failed."),
                        progress = ScanProgress(),
                        fileAction = FileActionPhase.Idle,
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanner.cancel()
        scanJob?.cancel()
    }

    fun selectNode(node: ScanNode) {
        _state.update { current ->
            if (!node.isDirectory) {
                current.copy(
                    selectedPath = node.path,
                    selectedNode = node,
                    fileAction = FileActionPhase.Idle,
                )
            } else {
                val expanded = current.expandedPaths.toMutableSet()
                if (!expanded.add(node.path)) expanded.remove(node.path)
                current.copy(
                    selectedPath = node.path,
                    selectedNode = node,
                    expandedPaths = expanded,
                    fileAction = FileActionPhase.Idle,
                )
            }
        }
    }

    fun renameSelected(newName: String) {
        val current = _state.value
        val node = current.selectedNode ?: return
        if (current.fileAction == FileActionPhase.Working) return
        if (!validName(newName)) {
            setActionError("Enter a valid file or folder name.")
            return
        }
        val oldFile = File(node.path)
        val parent = oldFile.parentFile
        if (parent == null) {
            setActionError("This item cannot be renamed.")
            return
        }
        val target = File(parent, newName)
        if (!safePath(node.path, current.root?.path) || !safePath(target.path, current.root?.path)) {
            setActionError("This item is outside the scanned storage root.")
            return
        }
        if (isProtectedPath(node.path) || isProtectedPath(target.path)) {
            setActionError("DiskTree cannot modify its own application data.")
            return
        }
        if (target.exists()) {
            setActionError("A file or folder with that name already exists.")
            return
        }
        _state.update { it.copy(fileAction = FileActionPhase.Working) }
        viewModelScope.launch {
            try {
                if (current.scope == ScanScope.ROOT_DEVICE) {
                    if (rootPathExists(target.path)) {
                        setActionError("A file or folder with that name already exists.")
                        return@launch
                    }
                    runRootCommand(
                        "/system/bin/mv ${shellQuote(node.path)} ${shellQuote(target.path)}",
                    )
                } else if (!oldFile.renameTo(target)) {
                    throw IOException("The item could not be renamed.")
                }
                _state.update {
                    it.copy(
                        selectedPath = null,
                        selectedNode = null,
                        fileAction = FileActionPhase.Idle,
                    )
                }
                scan(lastStorageAccess)
            } catch (error: CancellationException) {
                _state.update { it.copy(fileAction = FileActionPhase.Idle) }
                throw error
            } catch (error: Exception) {
                setActionError(error.message ?: "The item could not be renamed.")
            }
        }
    }

    fun deleteSelected() {
        val current = _state.value
        val node = current.selectedNode ?: return
        if (current.fileAction == FileActionPhase.Working) return
        if (!safePath(node.path, current.root?.path)) {
            setActionError("This item is outside the scanned storage root.")
            return
        }
        if (isProtectedPath(node.path)) {
            setActionError("DiskTree cannot delete its own application data.")
            return
        }
        _state.update { it.copy(fileAction = FileActionPhase.Working) }
        viewModelScope.launch {
            try {
                if (current.scope == ScanScope.ROOT_DEVICE) {
                    runRootCommand("/system/bin/rm -rf ${shellQuote(node.path)}")
                } else if (!File(node.path).deleteRecursively()) {
                    throw IOException("The item could not be deleted.")
                }
                _state.update {
                    it.copy(
                        selectedPath = null,
                        selectedNode = null,
                        fileAction = FileActionPhase.Idle,
                    )
                }
                scan(lastStorageAccess)
            } catch (error: CancellationException) {
                _state.update { it.copy(fileAction = FileActionPhase.Idle) }
                throw error
            } catch (error: Exception) {
                setActionError(error.message ?: "The item could not be deleted.")
            }
        }
    }

    override fun onCleared() {
        scanner.cancel()
        rootCheckJob?.cancel()
        super.onCleared()
    }

    private fun setActionError(message: String) {
        _state.update { it.copy(fileAction = FileActionPhase.Failed(message.take(180))) }
    }

    private fun validName(name: String): Boolean {
        return name.isNotBlank() &&
            '/' !in name &&
            '\u0000' !in name &&
            name != "." &&
            name != ".." &&
            name.toByteArray().size <= 255
    }

    private fun safePath(path: String, rootPath: String?): Boolean {
        if (rootPath == null) return false
        val canonicalPath = runCatching { File(path).canonicalPath }.getOrNull() ?: return false
        val canonicalRoot = runCatching { File(rootPath).canonicalPath }.getOrNull() ?: return false
        return canonicalPath != canonicalRoot && canonicalPath.startsWith("$canonicalRoot/")
    }

    private fun isProtectedPath(path: String): Boolean {
        val canonicalPath = runCatching { File(path).canonicalPath }.getOrNull() ?: return true
        val canonicalAppData = runCatching { appDataPath.canonicalPath }.getOrNull() ?: return true
        return canonicalPath == canonicalAppData || canonicalPath.startsWith("$canonicalAppData/")
    }

    private suspend fun rootPathExists(path: String): Boolean {
        val output = runRootCommand(
            "if [ -e ${shellQuote(path)} ] || [ -L ${shellQuote(path)} ]; then printf 1; else printf 0; fi",
        )
        return output.trim() == "1"
    }

    private suspend fun runRootCommand(command: String): String = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
        } catch (error: IOException) {
            throw IOException(error.message ?: "Root command could not start.")
        }
        try {
            if (!process.waitFor(30L, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw IOException("Root command timed out.")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.exitValue() != 0) {
                throw IOException(output.trim().ifBlank { "Root command failed." }.take(180))
            }
            output
        } catch (error: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw IOException("Root command was interrupted.")
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun readCapacity(file: File): StorageCapacity? = runCatching {
        val stats = StatFs(file.absolutePath)
        val total = stats.blockCountLong * stats.blockSizeLong
        val free = stats.availableBlocksLong * stats.blockSizeLong
        StorageCapacity(
            totalBytes = total,
            usedBytes = (total - free).coerceAtLeast(0L),
            freeBytes = free,
        )
    }.getOrNull()
}
