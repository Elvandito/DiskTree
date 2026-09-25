package com.disktree.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskTreeTest {
    @Test
    fun buildsTreeFromFindSizeOutput() {
        val builder = TreeBuilder("/data")

        assertNotNull(builder.accept("4096\t/data"))
        builder.accept("4096\t/data/app")
        builder.accept("1024\t/data/app/base.apk")
        builder.accept("2048\t/data/app/readme.txt")
        builder.accept("5000\t/data/media.bin")
        builder.accept("7\t/data/tab\tname.txt")

        val root = builder.build()

        assertEquals("/data", root.path)
        assertEquals(16_271L, root.sizeBytes)
        assertTrue(root.isDirectory)
        assertEquals(listOf("app", "media.bin", "tab\tname.txt"), root.children.map { it.name })
        assertTrue(root.children[0].isDirectory)
        assertEquals(7_168L, root.children[0].sizeBytes)
        assertEquals(5_000L, root.children[1].sizeBytes)
        assertEquals(7L, root.children[2].sizeBytes)
    }

    @Test
    fun scalesDuKilobytesToBytes() {
        val builder = TreeBuilder("/data", SizeMode.ALLOCATED)

        assertNotNull(builder.accept("12288 /data"))
        builder.accept("12288 /data/app")
        builder.accept("8192 /data/app/base.apk")

        val root = builder.build()

        assertEquals(12_582_912L, root.sizeBytes)
        assertTrue(root.children.single().isDirectory)
        assertEquals(12_582_912L, root.children.single().sizeBytes)
        val baseApk = root.children.single().children.single()
        assertEquals("base.apk", baseApk.name)
        assertEquals(8_388_608L, baseApk.sizeBytes)
    }

    @Test
    fun buildsTreeFromLogicalCompactOutput() {
        val builder = TreeBuilder("/data")

        builder.accept("1024 /data")
        builder.accept("2048 /data/app")
        builder.accept("4096 /data/app/base.apk")

        val root = builder.build()

        assertEquals(7_168L, root.sizeBytes)
        assertEquals(6_144L, root.children.single().sizeBytes)
    }

    @Test
    fun rejectsMalformedAndOutsidePaths() {
        val builder = TreeBuilder("/data")

        assertNull(builder.accept("find: permission denied"))
        assertNull(builder.accept("1024\t/outside/file"))
        assertNull(builder.accept("f\t1\t/outside"))
        assertNull(builder.accept("f\t1\t1024\t/data"))
        assertNull(builder.accept("f\tbad\t1024\t/data/file"))
    }

    @Test
    fun recognizesRootIdOutput() {
        assertTrue(isRootIdOutput("0\n"))
        assertFalse(isRootIdOutput("1000\n"))
        assertFalse(isRootIdOutput("permission denied"))
    }

    @Test
    fun flattensOnlyExpandedBranches() {
        val nested = ScanNode("/data/app/cache", "cache", 500L, isDirectory = true)
        val app = ScanNode("/data/app", "app", 750L, isDirectory = true, children = listOf(nested))
        val file = ScanNode("/data/video.mp4", "video.mp4", 250L, isDirectory = false)
        val root = ScanNode("/data", "data", 1_000L, isDirectory = true, children = listOf(app, file))

        val rootExpanded = flattenTree(root, setOf(root.path))
        assertEquals(listOf("/data", "/data/app", "/data/video.mp4"), rootExpanded.map { it.node.path })
        assertEquals(0.75f, rootExpanded[1].share, 0.001f)

        val appExpanded = flattenTree(root, setOf(root.path, app.path))
        assertEquals(4, appExpanded.size)
        assertEquals(2, appExpanded[2].depth)
        assertEquals(500f / 750f, appExpanded[2].share, 0.001f)
    }

    @Test
    fun hidesAndroidAppDirectoriesWhenPlatformBlocksThem() {
        val builder = TreeBuilder("/storage/emulated/0", SizeMode.LOGICAL, hideAndroidAppDirs = true)

        builder.accept("4096\t/storage/emulated/0")
        builder.accept("4096\t/storage/emulated/0/Android")
        builder.accept("4096\t/storage/emulated/0/Android/data")
        builder.accept("100\t/storage/emulated/0/Android/data/other.app")
        builder.accept("4096\t/storage/emulated/0/Android/obb")
        builder.accept("9000\t/storage/emulated/0/Download/app.bin")

        val root = builder.build()

        assertEquals(listOf("Download", "Android"), root.children.map { it.name })
        assertTrue(root.children[0].isDirectory)
        assertEquals(9_000L, root.children[0].sizeBytes)
        assertEquals(4_096L, root.children[1].sizeBytes)
        assertTrue(root.children[1].children.isEmpty())
    }

    @Test
    fun keepsAndroidAppDirectoriesWhenPlatformAllowsThem() {
        val builder = TreeBuilder("/storage/emulated/0")

        builder.accept("4096\t/storage/emulated/0")
        builder.accept("4096\t/storage/emulated/0/Android")
        builder.accept("100\t/storage/emulated/0/Android/data/other.app")

        val root = builder.build()

        assertEquals(listOf("Android"), root.children.map { it.name })
        assertEquals(4_196L, root.children.single().sizeBytes)
        val data = root.children.single().children.single()
        assertEquals("data", data.name)
        assertEquals("other.app", data.children.single().name)
    }

    @Test
    fun classifiesExpectedProtectionMessages() {
        assertTrue(isExpectedProtectionMessage("find: /storage/emulated/0/Android/data: Permission denied"))
        assertTrue(isExpectedProtectionMessage("du: cannot read directory '/storage/emulated/0/Android/obb/other.app': Permission denied"))
        assertFalse(isExpectedProtectionMessage("find: /storage/emulated/0/.nomedia: Permission denied"))
    }

    @Test
    fun warnsOnlyForUnexpectedSkippedPaths() {
        assertFalse(shouldWarnAboutSkippedPaths(exitCode = 1, unexpectedErrorCount = 0, expectedProtectionError = true))
        assertTrue(shouldWarnAboutSkippedPaths(exitCode = 1, unexpectedErrorCount = 0, expectedProtectionError = false))
        assertTrue(shouldWarnAboutSkippedPaths(exitCode = 0, unexpectedErrorCount = 1, expectedProtectionError = true))
        assertFalse(shouldWarnAboutSkippedPaths(exitCode = 0, unexpectedErrorCount = 0, expectedProtectionError = false))
    }

    private fun sampleTree(): ScanNode {
        val video = ScanNode("/sdcard/Movies/clip.mp4", "clip.mp4", 2_000L, isDirectory = false)
        val movies = ScanNode("/sdcard/Movies", "Movies", 2_000L, isDirectory = true, children = listOf(video))
        val cache = ScanNode("/sdcard/Android/cache.tmp", "cache.tmp", 600L, isDirectory = false)
        val download = ScanNode("/sdcard/Download", "Download", 3_000L, isDirectory = true, children = listOf(cache))
        return ScanNode("/sdcard", "sdcard", 5_000L, isDirectory = true, children = listOf(movies, download))
    }

    @Test
    fun filtersTreeByNameKeepingAncestors() {
        val filtered = filterTree(sampleTree(), "clip")

        assertNotNull(filtered)
        assertEquals(listOf("Movies"), filtered!!.children.map { it.name })
        assertEquals("clip.mp4", filtered.children[0].children[0].name)
    }

    @Test
    fun keepsRootWhenNothingMatches() {
        val noMatch = filterTree(sampleTree(), "nothing-here")

        assertNotNull(noMatch)
        assertTrue(noMatch!!.children.isEmpty())
        assertNotNull(filterTree(sampleTree(), "   "))
    }

    @Test
    fun listsLargestFilesWithSortModes() {
        val root = sampleTree()

        assertEquals(
            listOf("clip.mp4", "cache.tmp"),
            largestFiles(root, SortMode.SIZE).map { it.name },
        )
        assertEquals(
            listOf("cache.tmp", "clip.mp4"),
            largestFiles(root, SortMode.NAME).map { it.name },
        )
        assertEquals(
            listOf("cache.tmp", "clip.mp4"),
            largestFiles(root, SortMode.PATH).map { it.name },
        )
        assertEquals(1, largestFiles(root, SortMode.SIZE, limit = 1).size)
    }

    @Test
    fun buildsRealSpaceInsights() {
        val insights = buildInsights(sampleTree())

        assertTrue(insights.any { it.kind == InsightKind.TOP_FOLDER && it.title.contains("Download") })
        assertTrue(insights.none { it.kind == InsightKind.BIG_FILES })
        assertTrue(insights.all { it.sizeBytes > 0L })
    }

    @Test
    fun reportsExpandablePaths() {
        assertEquals(
            setOf("/sdcard", "/sdcard/Movies", "/sdcard/Download"),
            allExpandablePaths(sampleTree()),
        )
    }

    @Test
    fun writesCsvReportWithEscapedPaths() {
        val csv = reportCsv(sampleTree(), "shared storage", "file size")

        assertTrue(csv.startsWith("path,type,size_bytes,percent_of_root,size_view,scope"))
        assertTrue(csv.contains("\"/sdcard/Movies/clip.mp4\",file,2000,40.00,file size,shared storage"))
        assertEquals(6, csv.trim().lines().size)
    }
}
