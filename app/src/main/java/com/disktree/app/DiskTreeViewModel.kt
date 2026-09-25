package com.disktree.app

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SCAN_COOLDOWN_MS = 20_000L

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

enum class ListMode {
    TREE,
    LARGEST,
}

enum class SortMode {
    SIZE,
    NAME,
    PATH,
}

enum class InsightKind {
    BIG_FILES,
    TOP_FOLDER,
    CACHE,
    OLD_INSTALLS,
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
    val listMode: ListMode = ListMode.TREE,
    val sortMode: SortMode = SortMode.SIZE,
    val query: String = "",
    val phase: ScanPhase = ScanPhase.Idle,
    val progress: ScanProgress = ScanProgress(),
    val root: ScanNode? = null,
    val expandedPaths: Set<String> = emptySet(),
    val selectedPath: String? = null,
    val rootAccess: RootAccess = RootAccess.UNKNOWN,
    val capacity: StorageCapacity? = null,
    val warning: String? = null,
    val notice: String? = null,
    val scanCooldown: Boolean = false,
)

fun flattenTree(root: ScanNode, expandedPaths: Set<String>): List<VisibleNode> {
    val result = mutableListOf<VisibleNode>()
    appendNode(root, 0, 1f, expandedPaths, result)
    return result
}

fun filterTree(root: ScanNode, query: String): ScanNode? {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return root
    fun walk(node: ScanNode): ScanNode? {
        val children = node.children.mapNotNull(::walk)
        if (node != root && node.name.lowercase().contains(needle)) {
            return node
        }
        if (node == root || children.isNotEmpty()) {
            return node.copy(children = children)
        }
        return null
    }
    return walk(root)
}

fun allNodes(root: ScanNode): List<ScanNode> {
    val result = mutableListOf<ScanNode>()
    fun walk(node: ScanNode) {
        result += node
        node.children.forEach(::walk)
    }
    walk(root)
    return result
}

fun allExpandablePaths(root: ScanNode): Set<String> {
    val result = linkedSetOf<String>()
    fun walk(node: ScanNode) {
        if (node.isDirectory && node.children.isNotEmpty()) {
            result += node.path
            node.children.forEach(::walk)
        }
    }
    walk(root)
    return result
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1_000L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1_000.0 && unitIndex < units.lastIndex) {
        value /= 1_000.0
        unitIndex++
    }
    val pattern = if (value < 10.0) "%.1f %s" else "%.0f %s"
    return String.format(java.util.Locale.getDefault(), pattern, value, units[unitIndex])
}

fun largestFiles(root: ScanNode, sortMode: SortMode, limit: Int = 100): List<ScanNode> {
    val comparator = when (sortMode) {
        SortMode.SIZE -> compareByDescending<ScanNode> { it.sizeBytes }.thenBy { it.name.lowercase() }
        SortMode.NAME -> compareBy<ScanNode> { it.name.lowercase() }.thenByDescending { it.sizeBytes }
        SortMode.PATH -> compareBy<ScanNode> { it.path }.thenByDescending { it.sizeBytes }
    }
    return allNodes(root).filter { !it.isDirectory }.sortedWith(comparator).take(limit)
}

private val CACHE_NAMES = setOf("cache", ".cache", "code_cache", ".thumbnails", "temp", ".temp")

data class SpaceInsight(
    val kind: InsightKind,
    val title: String,
    val detail: String,
    val sizeBytes: Long,
    val share: Float,
    val targetPath: String?,
)

fun buildInsights(root: ScanNode, minShare: Float = 0.04f): List<SpaceInsight> {
    val insights = mutableListOf<SpaceInsight>()
    val total = root.sizeBytes
    val shareOf = { bytes: Long ->
        if (total > 0L) (bytes.toDouble() / total.toDouble()).toFloat() else 0f
    }

    val bigFiles = allNodes(root).filter { !it.isDirectory && it.sizeBytes >= 500L * 1024L * 1024L }
    if (bigFiles.isNotEmpty()) {
        val bigTotal = bigFiles.sumOf(ScanNode::sizeBytes)
        insights += SpaceInsight(
            kind = InsightKind.BIG_FILES,
            title = "${bigFiles.size} files over 500 MB",
            detail = "Review these first, they use ${formatShareText(shareOf(bigTotal))} of the scanned storage",
            sizeBytes = bigTotal,
            share = shareOf(bigTotal),
            targetPath = null,
        )
    }

    root.children.filter { it.isDirectory }.maxByOrNull { it.sizeBytes }?.let { top ->
        val share = shareOf(top.sizeBytes)
        if (share >= minShare) {
            insights += SpaceInsight(
                kind = InsightKind.TOP_FOLDER,
                title = "${top.name} uses ${formatShareText(share)}",
                detail = "The largest top-level folder in this scan",
                sizeBytes = top.sizeBytes,
                share = share,
                targetPath = top.path,
            )
        }
    }

    val cacheDirs = allNodes(root).filter { it.isDirectory && it.name.lowercase() in CACHE_NAMES }
    if (cacheDirs.isNotEmpty()) {
        val cacheTotal = cacheDirs.sumOf(ScanNode::sizeBytes)
        insights += SpaceInsight(
            kind = InsightKind.CACHE,
            title = "${cacheDirs.size} cache folders use ${formatBytes(cacheTotal)}",
            detail = "Cache folders are usually safe to clear from the owning app",
            sizeBytes = cacheTotal,
            share = shareOf(cacheTotal),
            targetPath = cacheDirs.maxByOrNull(ScanNode::sizeBytes)?.path,
        )
    }

    val oldInstalls = allNodes(root).filter { node ->
        !node.isDirectory && node.path.contains("/data/app/", ignoreCase = true) &&
            node.name.endsWith(".apk", ignoreCase = true)
    }
    if (oldInstalls.isNotEmpty()) {
        val oldTotal = oldInstalls.sumOf(ScanNode::sizeBytes)
        insights += SpaceInsight(
            kind = InsightKind.OLD_INSTALLS,
            title = "${oldInstalls.size} installed app packages use ${formatBytes(oldTotal)}",
            detail = "Root scans include /data/app, the size of installed apps",
            sizeBytes = oldTotal,
            share = shareOf(oldTotal),
            targetPath = "/data/app",
        )
    }

    return insights
}

internal fun reportCsv(root: ScanNode, scopeLabel: String, sizeLabel: String): String {
    val total = root.sizeBytes
    return buildString {
        appendLine("path,type,size_bytes,percent_of_root,size_view,scope")
        appendLine("${csvCell(root.path)},root,$total,100,$sizeLabel,$scopeLabel")
        allNodes(root).filter { it.path != root.path }.forEach { node ->
            val percent = if (total > 0L) node.sizeBytes * 100.0 / total else 0.0
            appendLine(
                "${csvCell(node.path)},${if (node.isDirectory) "folder" else "file"}," +
                    "${node.sizeBytes},${"%.2f".format(percent)},$sizeLabel,$scopeLabel",
            )
        }
    }
}

private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

private fun formatShareText(share: Float): String {
    val percent = (share * 100f).toInt()
    return if (percent >= 1) "$percent%" else "under 1%"
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
    private var cooldownJob: Job? = null

    fun selectScope(scope: ScanScope) {
        if (_state.value.phase == ScanPhase.Scanning) return
        if (_state.value.scope == scope) {
            if (scope == ScanScope.ROOT_DEVICE && _state.value.rootAccess != RootAccess.AVAILABLE) {
                checkRootAccess()
            }
            return
        }
        val path = if (scope == ScanScope.ROOT_DEVICE) "/data" else sharedStoragePath
        cooldownJob?.cancel()
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
                scanCooldown = false,
            )
        }
        if (scope == ScanScope.ROOT_DEVICE) checkRootAccess()
    }

    fun selectSizeMode(sizeMode: SizeMode) {
        if (
            _state.value.phase == ScanPhase.Scanning ||
            _state.value.sizeMode == sizeMode
        ) return
        cooldownJob?.cancel()
        _state.update {
            it.copy(
                sizeMode = sizeMode,
                phase = ScanPhase.Idle,
                progress = ScanProgress(),
                root = null,
                expandedPaths = emptySet(),
                selectedPath = null,
                warning = null,
                scanCooldown = false,
            )
        }
    }

    fun setListMode(listMode: ListMode) {
        _state.update { it.copy(listMode = listMode) }
    }

    fun setSortMode(sortMode: SortMode) {
        _state.update { it.copy(sortMode = sortMode) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun revealPath(path: String) {
        val root = _state.value.root ?: return
        val target = allNodes(root).firstOrNull { it.path == path } ?: return
        val reveal = linkedSetOf<String>()
        var current = target.path
        while (current.isNotEmpty()) {
            reveal += current
            val next = current.substringBeforeLast('/', "")
            if (next == current) break
            current = next
        }
        _state.update {
            it.copy(
                listMode = ListMode.TREE,
                selectedPath = target.path,
                expandedPaths = it.expandedPaths + reveal,
            )
        }
    }

    fun selectedNode(): ScanNode? {
        val current = _state.value
        val root = current.root ?: return null
        val path = current.selectedPath ?: return null
        return allNodes(root).firstOrNull { it.path == path }
    }

    fun exportReport() {
        val current = _state.value
        val root = current.root
        if (root == null) {
            _state.update { it.copy(notice = "Run a scan before exporting a report.") }
            return
        }
        val scopeLabel = if (current.scope == ScanScope.ROOT_DEVICE) "/data" else "shared storage"
        val sizeLabel = if (current.sizeMode == SizeMode.LOGICAL) "file size" else "disk usage"
        val content = reportCsv(root, scopeLabel, sizeLabel)
        val outcome = getApplication<Application>().contentResolver.let { resolver ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, "disktree-report.csv")
                        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Downloads folder is unavailable")
                    resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        ?: error("Could not open the report file")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Saved to Downloads/disktree-report.csv"
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    dir.mkdirs()
                    val file = File(dir, "disktree-report.csv")
                    file.writeText(content)
                    "Saved to ${file.absolutePath}"
                }
            }
        }
        _state.update {
            it.copy(
                notice = outcome.getOrElse { error -> "Export failed: ${error.message ?: "unknown error"}" },
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
        if (_state.value.phase == ScanPhase.Scanning || _state.value.scanCooldown) return
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
                startCooldown()
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
                startCooldown()
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
        cooldownJob?.cancel()
        super.onCleared()
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        _state.update { it.copy(scanCooldown = true) }
        cooldownJob = viewModelScope.launch {
            delay(SCAN_COOLDOWN_MS)
            _state.update { it.copy(scanCooldown = false) }
        }
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
