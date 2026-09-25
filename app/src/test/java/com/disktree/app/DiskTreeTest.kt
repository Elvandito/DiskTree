package com.disktree.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskTreeTest {
    @Test
    fun buildsSortedTreeFromFindOutput() {
        val builder = TreeBuilder("/data")

        assertFalse(builder.accept("d\t8\t4096\t/data").isNull())
        builder.accept("d\t8\t4096\t/data/app")
        builder.accept("f\t16\t1024\t/data/app/base.apk")
        builder.accept("f\t0\t2048\t/data/app/readme.txt")
        builder.accept("f\t64\t1024\t/data/media.bin")

        val root = builder.build()

        assertEquals("/data", root.path)
        assertEquals(49_152L, root.sizeBytes)
        assertTrue(root.isDirectory)
        assertEquals(listOf("media.bin", "app"), root.children.map { it.name })
        assertEquals(12_288L, root.children[1].sizeBytes)
        assertEquals("readme.txt", root.children[1].children[1].name)
        assertEquals(2_048L, root.children[1].children[1].sizeBytes)
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
        assertEquals(2, appExpanded.last().depth)
        assertEquals(500f / 750f, appExpanded.last().share, 0.001f)
    }
}
