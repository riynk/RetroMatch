package code.name.monkey.retromusic.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcSidecarPathTest {
    @Test
    fun replacesOnlyAudioExtension() {
        assertEquals(
            "/storage/music.mp3 archive/song.lrc",
            LrcSidecarPath.fromAudioPath("/storage/music.mp3 archive/song.mp3")
        )
    }

    @Test
    fun addsLrcExtensionWhenAudioPathHasNoExtension() {
        assertEquals(
            "/storage/music/live recording.lrc",
            LrcSidecarPath.fromAudioPath("/storage/music/live recording")
        )
    }
}
