package code.name.monkey.retromusic.util

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val validLyricsFile = """
        version: '1.0'
        metadata:
          title: 'Example'
          artist: 'Artist'
        lines:
          - text: 'Hello world'
            start_ms: 1000
            words:
              - text: 'Hello '
                start_ms: 1000
              - text: 'world'
                start_ms: 1600
    """.trimIndent()

    @Test
    fun storesAndReadsOriginalLyricsfileWithoutRewritingTiming() {
        val store = LyricsFileStore(temporaryFolder.root)
        assertTrue(store.save("/music/track.flac", validLyricsFile))
        assertEquals(validLyricsFile, store.read("/music/track.flac"))
        assertTrue(temporaryFolder.root.listFiles()?.single()?.name?.endsWith(".lyricsfile.yaml") == true)
    }

    @Test
    fun keepsTracksWithSameFileNameInDifferentFoldersSeparate() {
        val store = LyricsFileStore(temporaryFolder.root)
        assertTrue(store.save("/music/album-a/track.flac", validLyricsFile))
        assertTrue(store.save("/music/album-b/track.flac", validLyricsFile))
        assertNotEquals(
            LyricsFileKey.create("/music/album-a/track.flac"),
            LyricsFileKey.create("/music/album-b/track.flac")
        )
        assertNotNull(store.read("/music/album-a/track.flac"))
        assertNotNull(store.read("/music/album-b/track.flac"))
    }

    @Test
    fun replacesExistingLyricsWithoutLeavingTemporaryFiles() {
        val store = LyricsFileStore(temporaryFolder.root)
        assertTrue(store.save("/music/track.flac", validLyricsFile))
        val replacement = validLyricsFile.replace("Hello world", "Goodbye world")
        assertTrue(store.save("/music/track.flac", replacement))
        assertEquals(replacement, store.read("/music/track.flac"))
        assertEquals(1, temporaryFolder.root.listFiles()?.size)
    }

    @Test
    fun rejectsMalformedLyricsAndOversizedPayloads() {
        val store = LyricsFileStore(temporaryFolder.root)
        assertFalse(store.save("/music/track.flac", "not yaml"))
        assertFalse(store.save("/music/track.flac", validLyricsFile + " ".repeat(2 * 1024 * 1024)))
        assertNull(store.read("/music/track.flac"))
        assertTrue(temporaryFolder.root.listFiles()?.isEmpty() != false)
    }

    @Test
    fun deletesOnlyRequestedTrack() {
        val store = LyricsFileStore(temporaryFolder.root)
        assertTrue(store.save("/music/a.flac", validLyricsFile))
        assertTrue(store.save("/music/b.flac", validLyricsFile))
        assertTrue(store.delete("/music/a.flac"))
        assertNull(store.read("/music/a.flac"))
        assertNotNull(store.read("/music/b.flac"))
    }
}
