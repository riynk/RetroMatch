package code.name.monkey.retromusic.activities.tageditor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagWriterCacheFileTest {

    @Test
    fun duplicateBasenamesProduceDistinctCacheFilesWithAudioExtension() {
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "retromatch-cache-test-${System.nanoTime()}")
        assertTrue(cacheDir.mkdirs())

        try {
            val first = createTagCacheFile(cacheDir, File("/music/album-a/track.mp3"))
            val second = createTagCacheFile(cacheDir, File("/music/album-b/track.mp3"))

            assertNotEquals(first.absolutePath, second.absolutePath)
            assertEquals("mp3", first.extension)
            assertEquals("mp3", second.extension)
            assertTrue(first.exists())
            assertTrue(second.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun extensionlessSourceUsesSafeTemporaryExtension() {
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "retromatch-cache-test-${System.nanoTime()}")
        assertTrue(cacheDir.mkdirs())

        try {
            val cacheFile = createTagCacheFile(cacheDir, File("/music/track"))
            assertEquals("tmp", cacheFile.extension)
            assertTrue(cacheFile.exists())
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
