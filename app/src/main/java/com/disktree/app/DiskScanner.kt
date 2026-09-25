package com.disktree.app

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class RawEntry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

internal class TreeBuilder(private val rootPath: String) {
    private data class MutableNode(
        val path: String,
        val isDirectory: Boolean,
        val ownSizeBytes: Long,
        val children: MutableList<MutableNode> = mutableListOf(),
    )

    private val nodes = linkedMapOf(
        rootPath to MutableNode(rootPath, isDirectory = true, ownSizeBytes = 0L),
    )

    fun accept(line: String): RawEntry? {
        val fields = line.split('\t', limit = 4)
        if (fields.size != 4 || fields[0].length != 1) return null

        val allocatedBlocks = fields[1].toLongOrNull()?.coerceAtLeast(0L) ?: return null
        val logicalSize = fields[2].toLongOrNull()?.coerceAtLeast(0L) ?: return null
        val sizeBytes = (allocatedBlocks * 512L).takeIf { it > 0L } ?: logicalSize
        val path = normalize(fields[3])
        if (!isWithinRoot(path)) return null

        val isDirectory = fields[0] == "d"
        if (path == rootPath && !isDirectory) return null
        nodes[path] = MutableNode(path, isDirectory, sizeBytes)
        return RawEntry(path, isDirectory, sizeBytes)
    }

    fun build(): ScanNode {
        nodes.values
            .filterNot { it.path == rootPath }
            .forEach { node ->
                parentOf(node.path)?.let(nodes::get)?.children?.add(node)
            }

        return freeze(nodes.getValue(rootPath))
    }

    private fun freeze(node: MutableNode): ScanNode {
        val children = node.children
            .map(::freeze)
            .sortedWith(
                compareByDescending<ScanNode> { it.sizeBytes }
                    .thenBy { it.name.lowercase() },
            )
        val sizeBytes = node.ownSizeBytes + children.sumOf(ScanNode::sizeBytes)
        return ScanNode(
            path = node.path,
            name = node.path.substringAfterLast('/').ifEmpty { node.path },
            sizeBytes = sizeBytes,
            isDirectory = node.isDirectory,
            children = children,
        )
    }

    private fun isWithinRoot(path: String): Boolean {
        if (path == rootPath) return true
        if (rootPath == "/") return path.startsWith('/')
        return path.startsWith("$rootPath/")
    }

    private fun parentOf(path: String): String? {
        val parent = path.substringBeforeLast('/')
        return parent.ifEmpty { "/" }.takeIf { it != path }
    }

    private fun normalize(path: String): String {
        if (path.length > 1 && path.endsWith('/')) return path.dropLast(1)
        return path
    }
}

internal data class ScanProgress(
    val currentPath: String = "",
    val entryCount: Long = 0L,
    val scannedBytes: Long = 0L,
)

internal data class ScanResult(
    val root: ScanNode,
    val warning: String? = null,
)

internal class DiskScanner {
    @Volatile
    private var activeProcess: Process? = null

    suspend fun scan(
        useRoot: Boolean,
        sharedStoragePath: String,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        val rootPath = if (useRoot) "/data" else sharedStoragePath
        val tree = TreeBuilder(rootPath)
        val process = startProcess(useRoot, sharedStoragePath)
        activeProcess = process

        var entryCount = 0L
        var fileCount = 0L
        var scannedBytes = 0L
        val errors = mutableListOf<String>()

        try {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue

                    val entry = tree.accept(line)
                    if (entry == null) {
                        if (errors.size < 4) errors += line.take(180)
                        continue
                    }

                    entryCount++
                    if (!entry.isDirectory) {
                        fileCount++
                        scannedBytes += entry.sizeBytes
                    }
                    if (entryCount % 128L == 0L) {
                        onProgress(ScanProgress(entry.path, entryCount, scannedBytes))
                    }
                }
            }

            val exitCode = process.waitFor()
            currentCoroutineContext().ensureActive()
            val detail = errors.firstOrNull().orEmpty()
            if (entryCount == 0L || (useRoot && fileCount == 0L && exitCode != 0)) {
                throw ScanException(
                    if (useRoot) {
                        detail.ifBlank { "Root access was denied. Approve DiskTree in your root manager and try again." }
                    } else {
                        detail.ifBlank { "Shared storage could not be read. Grant file access and try again." }
                    },
                )
            }

            onProgress(ScanProgress(rootPath, entryCount, scannedBytes))
            ScanResult(
                root = tree.build(),
                warning = if (exitCode != 0 || errors.isNotEmpty()) {
                    "Some protected paths were skipped."
                } else {
                    null
                },
            )
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw ScanException(error.message ?: "The storage scan could not be completed.")
        } finally {
            activeProcess = null
        }
    }

    fun cancel() {
        activeProcess?.destroy()
    }

    private fun startProcess(useRoot: Boolean, sharedStoragePath: String): Process {
        val format = "%y\\t%b\\t%s\\t%p\\n"
        return if (useRoot) {
            ProcessBuilder(
                "su",
                "-c",
                "exec /system/bin/find /data -printf '$format'",
            ).redirectErrorStream(true).start()
        } else {
            ProcessBuilder(
                "/system/bin/find",
                "-printf",
                format,
                sharedStoragePath,
            ).redirectErrorStream(true).start()
        }
    }
}

internal class ScanException(message: String) : Exception(message)
