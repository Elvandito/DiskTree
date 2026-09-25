package com.disktree.app

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val rootAccess: RootAccess = RootAccess.UNKNOWN,
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
    private val sharedStoragePath = Environment.getExternalStorageDirectory().absolutePath
    private val _state = MutableStateFlow(
        DiskTreeUiState(capacity = readCapacity(File(sharedStoragePath))),
    )
    val state: StateFlow<DiskTreeUiState> = _state.asStateFlow()
    private var scanJob: Job? = null
    private var rootCheckJob: Job? = null

    fun selectScope(scope: ScanScope) {
        if (_state.value.phase == ScanPhase.Scanning) return
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
                rootAccess = RootAccess.UNKNOWN,
                capacity = readCapacity(File(path)),
                warning = null,
            )
        }
        if (scope == ScanScope.ROOT_DEVICE) checkRootAccess()
    }

    fun selectSizeMode(sizeMode: SizeMode) {
        if (
            _state.value.phase == ScanPhase.Scanning ||
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
        if (_state.value.phase == ScanPhase.Scanning) return
        val scope = _state.value.scope
        if (scope == ScanScope.SHARED_STORAGE && !hasStorageAccess) {
            _state.update {
                it.copy(phase = ScanPhase.Failed("File access is required before scanning shared storage."))
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
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanner.cancel()
    }

    fun selectNode(node: ScanNode) {
        _state.update { current ->
            if (!node.isDirectory) {
                current.copy(selectedPath = node.path)
            } else {
                val expanded = current.expandedPaths.toMutableSet()
                if (!expanded.add(node.path)) expanded.remove(node.path)
                current.copy(
                    selectedPath = node.path,
                    expandedPaths = expanded,
                )
            }
        }
    }

    override fun onCleared() {
        scanner.cancel()
        rootCheckJob?.cancel()
        super.onCleared()
    }

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
