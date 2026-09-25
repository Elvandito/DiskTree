package com.disktree.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskTreeTest {
    @Test
    fun buildsSortedTreeFromFindOutput() {
        val builder = TreeBuilder("/data")

        assertNotNull(builder.accept("d\t8\t4096\t/data"))
        builder.accept("d\t8\t4096\t/data/app")
        builder.accept("f\t16\t1024\t/data/app/base.apk")
        builder.accept("f\t0\t2048\t/data/app/readme.txt")
        builder.accept("f\t64\t1024\t/data/media.bin")

        val root = builder.build()

        assertEquals("/data", root.path)
        assertEquals(51_200L, root.sizeBytes)
        assertTrue(root.isDirectory)
        assertEquals(listOf("media.bin", "app"), root.children.map { it.name })
        assertEquals(14_336L, root.children[1].sizeBytes)
        assertEquals("readme.txt", root.children[1].children[1].name)
        assertEquals(2_048L, root.children[1].children[1].sizeBytes)
    }

    @Test
    fun buildsTreeFromDuOutput() {
        val builder = TreeBuilder("/data", compactSizeIncludesChildren = true)

        assertNotNull(builder.accept("12288 /data"))
        builder.accept("12288 /data/app")
        builder.accept("8192 /data/app/base.apk")

        val root = builder.build()

        assertEquals(12_288L, root.sizeBytes)
        assertTrue(root.children.single().isDirectory)
        assertEquals("base.apk", root.children.single().children.single().name)
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
}
