package com.disktree.app

import android.os.Build
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class RawEntry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

internal class TreeBuilder(
    private val rootPath: String,
    private val sizeMode: SizeMode = SizeMode.LOGICAL,
    private val hideAndroidAppDirs: Boolean = false,
) {
    private data class MutableNode(
        val path: String,
        var isDirectory: Boolean,
        val ownSizeBytes: Long,
        val reportedSizeIncludesChildren: Boolean,
        val children: MutableList<MutableNode> = mutableListOf(),
    )

    private val nodes = linkedMapOf(
        rootPath to MutableNode(
            path = rootPath,
            isDirectory = true,
            ownSizeBytes = 0L,
            reportedSizeIncludesChildren = false,
        ),
    )

    fun accept(line: String): RawEntry? {
        val delimiter = line.indexOfFirst { it == '\t' || it == ' ' }
        if (delimiter <= 0) return null

        val rawSize = line.substring(0, delimiter).toLongOrNull()?.coerceAtLeast(0L) ?: return null
        val path = normalize(line.substring(delimiter + 1).trimStart(' '))
        if (!isWithinRoot(path)) return null
        if (hideAndroidAppDirs && isAndroidAppDir(path)) {
            if (path.endsWith("/Android/data") || path.endsWith("/Android/obb")) {
                parentOf(path)?.let { parent ->
                    nodes.getOrPut(parent) {
                        MutableNode(
                            path = parent,
                            isDirectory = true,
                            ownSizeBytes = 0L,
                            reportedSizeIncludesChildren = false,
                        )
                    }.isDirectory = true
                }
            }
            return null
        }

        val reportedSizeIncludesChildren = sizeMode == SizeMode.ALLOCATED
        val sizeBytes = if (reportedSizeIncludesChildren) rawSize * 1024L else rawSize
        val isDirectory = path == rootPath
        nodes[path] = MutableNode(path, isDirectory, sizeBytes, reportedSizeIncludesChildren)
        return RawEntry(path, isDirectory, sizeBytes)
    }

    fun build(): ScanNode {
        nodes.values.toList()
            .filterNot { it.path == rootPath }
            .forEach { node ->
                var parent = parentOf(node.path)
                while (parent != null && parent != rootPath) {
                    nodes.getOrPut(parent) {
                        MutableNode(
                            path = parent,
                            isDirectory = true,
                            ownSizeBytes = 0L,
                            reportedSizeIncludesChildren = false,
                        )
                    }.isDirectory = true
                    parent = parentOf(parent)
                }
            }
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
        val childBytes = children.sumOf(ScanNode::sizeBytes)
        val sizeBytes = if (node.isDirectory && node.reportedSizeIncludesChildren) {
            maxOf(node.ownSizeBytes, childBytes)
        } else {
            node.ownSizeBytes + childBytes
        }
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

data class ScanProgress(
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

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder("su", "-c", "id -u")
                .redirectErrorStream(true)
                .start()
        } catch (_: IOException) {
            return@withContext false
        } catch (_: SecurityException) {
            return@withContext false
        }

        try {
            if (!process.waitFor(8L, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@withContext false
            }
            process.exitValue() == 0 && process.inputStream.bufferedReader().use {
                isRootIdOutput(it.readText())
            }
        } catch (_: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            false
        } catch (_: IOException) {
            process.destroyForcibly()
            false
        }
    }

    suspend fun scan(
        useRoot: Boolean,
        sharedStoragePath: String,
        sizeMode: SizeMode,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        val rootPath = if (useRoot) "/data" else sharedStoragePath
        val hideAndroidAppDirs = !useRoot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val tree = TreeBuilder(rootPath, sizeMode, hideAndroidAppDirs)
        val process = startProcess(useRoot, sharedStoragePath, sizeMode)
        activeProcess = process

        var entryCount = 0L
        var scannedBytes = 0L
        var expectedProtection = false
        val errors = mutableListOf<String>()

        try {
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue

                    val entry = tree.accept(line)
                    if (entry == null) {
                        if (isExpectedProtectionMessage(line)) {
                            expectedProtection = true
                        } else if (errors.size < 4) {
                            errors += line.take(180)
                        }
                        continue
                    }

                    entryCount++
                    if (sizeMode == SizeMode.LOGICAL) {
                        scannedBytes += entry.sizeBytes
                    }
                    if (entryCount % 128L == 0L) {
                        onProgress(ScanProgress(entry.path, entryCount, scannedBytes))
                    }
                }
            }

            val exitCode = process.waitFor()
            currentCoroutineContext().ensureActive()
            val rootNode = tree.build()
            val detail = errors.firstOrNull().orEmpty()
            if (entryCount == 0L || (exitCode != 0 && rootNode.children.isEmpty())) {
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
                root = rootNode,
                warning = if (shouldWarnAboutSkippedPaths(exitCode, errors.size, expectedProtection)) {
                    "Some paths could not be read."
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

    private fun startProcess(
        useRoot: Boolean,
        sharedStoragePath: String,
        sizeMode: SizeMode,
    ): Process {
        return if (sizeMode == SizeMode.ALLOCATED) {
            startAllocatedScan(useRoot, sharedStoragePath)
        } else {
            startLogicalScan(useRoot, sharedStoragePath)
        }
    }

    private fun startAllocatedScan(useRoot: Boolean, sharedStoragePath: String): Process {
        return if (useRoot) {
            ProcessBuilder(
                "su",
                "-c",
                "exec /system/bin/du -a -k /data",
            ).redirectErrorStream(true).start()
        } else {
            ProcessBuilder(
                "/system/bin/du",
                "-a",
                "-k",
                sharedStoragePath,
            ).redirectErrorStream(true).start()
        }
    }

    private fun startLogicalScan(useRoot: Boolean, sharedStoragePath: String): Process {
        val format = "%s\\t%p\\n"
        return if (useRoot) {
            ProcessBuilder(
                "su",
                "-c",
                "exec /system/bin/find /data -printf '$format'",
            ).redirectErrorStream(true).start()
        } else {
            ProcessBuilder(
                "/system/bin/find",
                sharedStoragePath,
                "-printf",
                format,
            ).redirectErrorStream(true).start()
        }
    }
}

internal fun isAndroidAppDir(path: String): Boolean {
    return path.endsWith("/Android/data") || path.contains("/Android/data/") ||
        path.endsWith("/Android/obb") || path.contains("/Android/obb/")
}

internal fun isExpectedProtectionMessage(line: String): Boolean {
    return line.contains("/Android/data") || line.contains("/Android/obb")
}

internal fun shouldWarnAboutSkippedPaths(
    exitCode: Int,
    unexpectedErrorCount: Int,
    expectedProtectionError: Boolean,
): Boolean = unexpectedErrorCount > 0 || (exitCode != 0 && !expectedProtectionError)

internal fun isRootIdOutput(output: String): Boolean = output.trim().toLongOrNull() == 0L

internal class ScanException(message: String) : Exception(message)
