package code.name.monkey.retromusic.util

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TtmlLyricsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val validTtml = """
        <tt xmlns="http://www.w3.org/ns/ttml">
          <body><div>
            <p begin="00:00:01.000" end="00:00:02.000">
              <span begin="00:00:01.000" end="00:00:01.500">Hello </span>
              <span begin="00:00:01.500" end="00:00:02.000">world</span>
            </p>
          </div></body>
        </tt>
    """.trimIndent()

    @Test
    fun savesAndReadsTtmlByAudioPath() {
        val store = TtmlLyricsStore(temporaryFolder.root)
        assertTrue(store.save("/music/track.flac", validTtml))
        val lyrics = store.read("/music/track.flac")
        assertNotNull(lyrics)
        assertEquals("Hello world", lyrics!!.lines.single().text)
        assertEquals(2, lyrics.lines.single().syllabus.size)
    }

    @Test
    fun discoversAndCachesAdjacentTtmlSidecarAutomatically() {
        val musicDirectory = temporaryFolder.newFolder("music")
        val cacheDirectory = temporaryFolder.newFolder("lyrics-cache")
        val audioFile = java.io.File(musicDirectory, "sidecar-track.flac")
        assertTrue(audioFile.createNewFile())
        val sidecar = java.io.File(musicDirectory, "sidecar-track.ttml")
        sidecar.writeText(validTtml, Charsets.UTF_8)

        val store = TtmlLyricsStore(cacheDirectory)
        val lyrics = store.read(audioFile.absolutePath)

        assertNotNull(lyrics)
        assertEquals("Hello world", lyrics!!.lines.single().text)
        // A second read must succeed from the managed cache even after the sidecar
        // is no longer available to the app.
        assertTrue(sidecar.delete())
        assertEquals("Hello world", store.read(audioFile.absolutePath)!!.lines.single().text)
        assertEquals(1, cacheDirectory.listFiles()?.size)
    }

    @Test
    fun preservesSyllableTimingForAWordSplitAcrossSpans() {
        val store = TtmlLyricsStore(temporaryFolder.root)
        val ttml = validTtml.replace("Hello ", "Hel").replace("world", "lo world")
        assertTrue(store.save("/music/syllables.flac", ttml))
        val line = store.read("/music/syllables.flac")!!.lines.single()
        assertEquals("Hello world", line.text)
        assertEquals("Hel", line.syllabus[0].text)
        assertTrue(line.syllabus[0].continuesWord)
    }

    @Test
    fun separatesTracksWithIdenticalMetadataButDifferentPaths() {
        val store = TtmlLyricsStore(temporaryFolder.root)
        assertTrue(store.save("/music/album-a/track.flac", validTtml))
        assertTrue(store.save("/music/album-b/track.flac", validTtml))
        assertNotEquals(
            storeFileName("/music/album-a/track.flac"),
            storeFileName("/music/album-b/track.flac")
        )
        assertNotNull(store.read("/music/album-a/track.flac"))
        assertNotNull(store.read("/music/album-b/track.flac"))
    }

    @Test
    fun replacesExistingLyricsWithoutLeavingTemporaryFiles() {
        val store = TtmlLyricsStore(temporaryFolder.root)
        val path = "/music/replace.flac"
        assertTrue(store.save(path, validTtml))

        val replacement = validTtml.replace("Hello ", "Goodbye ")
        assertTrue(store.save(path, replacement))
        assertEquals("Goodbye world", store.read(path)!!.lines.single().text)
        assertEquals(1, temporaryFolder.root.listFiles()?.size)
    }

    @Test
    fun rejectsEmptyOrInvalidTtmlWithoutCreatingAFile() {
        val store = TtmlLyricsStore(temporaryFolder.root)
        assertFalse(store.save("/music/track.flac", ""))
        assertFalse(store.save("/music/track.flac", "<tt><body/></tt>"))
        assertFalse(temporaryFolder.root.listFiles()?.isNotEmpty() == true)
    }

    @Test
    fun deletesOnlyTheRequestedTrack() {
        val store = TtmlLyricsStore(temporaryFolder.root)
        assertTrue(store.save("/music/a.flac", validTtml))
        assertTrue(store.save("/music/b.flac", validTtml))
        assertTrue(store.delete("/music/a.flac"))
        assertNull(store.read("/music/a.flac"))
        assertNotNull(store.read("/music/b.flac"))
    }

    private fun storeFileName(path: String) =
        LyricsFileKey.create(path).removeSuffix(".lrc") + ".ttml"
}
