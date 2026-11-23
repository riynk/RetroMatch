package code.name.monkey.retromusic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFileKeyTest {

    @Test
    fun sameAudioPathProducesStableFileName() {
        assertEquals(
            LyricsFileKey.create("/storage/emulated/0/Music/track.flac"),
            LyricsFileKey.create("/storage/emulated/0/Music/track.flac")
        )
    }

    @Test
    fun differentAudioPathsDoNotShareLyricsFile() {
        assertNotEquals(
            LyricsFileKey.create("/music/album-a/track.flac"),
            LyricsFileKey.create("/music/album-b/track.flac")
        )
    }

    @Test
    fun fileNameIsSafeForFilesystem() {
        val fileName = LyricsFileKey.create("/music/artist/track:?.flac")
        assertTrue(fileName.matches(Regex("""track-[a-f0-9]{16}\.lrc""")))
        assertTrue('/' !in fileName)
        assertTrue('\\' !in fileName)
    }
}
