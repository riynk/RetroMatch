package code.name.monkey.retromusic.lyrics.ttml

import org.junit.Assert.*
import org.junit.Test

class LyricsFileParserTest {

    @Test
    fun parsesWordSyncedLyricsAndPreservesExplicitTiming() {
        val lyricsfile = """
            version: '1.0'
            metadata:
              title: 'Small Hours'
              artist: 'Example Artist'
              duration_ms: 245000
              language: 'en'
            lines:
              - text: 'A synchronized lyric line'
                start_ms: 12000
                end_ms: 15500
                words:
                  - text: 'A '
                    start_ms: 12000
                    end_ms: 12800
                  - text: 'synchronized '
                    start_ms: 12800
                    end_ms: 14000
                  - text: 'lyric '
                    start_ms: 14000
                    end_ms: 14800
                  - text: 'line'
                    start_ms: 14800
                    end_ms: 15500
            plain: |
              A synchronized lyric line
        """.trimIndent()

        val lyrics = LyricsFileParser.parse(lyricsfile)!!
        val line = lyrics.lines.single()
        assertEquals("A synchronized lyric line", line.text)
        assertEquals(12_000L, line.timeMs)
        assertEquals(3_500L, line.durationMs)
        assertEquals(listOf("A ", "synchronized ", "lyric ", "line"), line.syllabus.map { it.text })
        assertEquals(listOf(800L, 1_200L, 800L, 700L), line.syllabus.map { it.durationMs })
        assertEquals("Small Hours", lyrics.metadata.title)
        assertEquals("en", lyrics.metadata.language)
    }

    @Test
    fun leavesUnspecifiedEndTimesUnsetAndDoesNotApplyUnresolvedOffset() {
        val lyricsfile = """
            version: '1.0'
            metadata:
              title: 'Offset test'
              artist: 'Example Artist'
              offset_ms: -100
            lines:
              - text: 'Hello world'
                start_ms: 1000
                words:
                  - text: 'Hello '
                    start_ms: 1000
                  - text: 'world'
                    start_ms: 1600
        """.trimIndent()

        val line = LyricsFileParser.parse(lyricsfile)!!.lines.single()
        assertEquals(1_000L, line.timeMs)
        assertEquals(0L, line.durationMs)
        assertEquals(1_000L, line.syllabus[0].timeMs)
        assertEquals(0L, line.syllabus[0].durationMs)
        assertEquals(1_600L, line.syllabus[1].timeMs)
        assertEquals(0L, line.syllabus[1].durationMs)
    }

    @Test
    fun acceptsLineSyncedLyricsWithoutWordArray() {
        val lyricsfile = """
            version: "1.0"
            metadata:
              title: "Line synced"
              artist: "Example Artist"
            lines:
              - text: "A line"
                start_ms: 1000
                end_ms: 3000
        """.trimIndent()

        val lyrics = LyricsFileParser.parse(lyricsfile)!!
        assertEquals("Line", lyrics.type)
        assertEquals("A line", lyrics.lines.single().text)
        assertTrue(lyrics.lines.single().syllabus.isEmpty())
    }

    @Test
    fun rejectsUnsupportedVersionMalformedYamlAndInstrumentalRecords() {
        assertNull(LyricsFileParser.parse("version: '2.0'\nmetadata: {}\nlines: []"))
        assertNull(LyricsFileParser.parse("version: ["))
        assertNull(
            LyricsFileParser.parse(
                "version: '1.0'\nmetadata:\n  title: x\n  artist: y\n  instrumental: true\nlines: []"
            )
        )
    }

    @Test
    fun rejectsMissingRequiredMetadataNegativeTimesAndInvalidWordOrder() {
        assertNull(
            LyricsFileParser.parse(
                "version: '1.0'\nmetadata: {}\nlines: []"
            )
        )
        assertNull(
            LyricsFileParser.parse(
                """
                    version: '1.0'
                    metadata: {title: T, artist: A}
                    lines:
                      - text: Bad
                        start_ms: -1
                """.trimIndent()
            )
        )
        assertNull(
            LyricsFileParser.parse(
                """
                    version: '1.0'
                    metadata: {title: T, artist: A}
                    lines:
                      - text: 'Hello world'
                        start_ms: 0
                        words:
                          - {text: 'Hello ', start_ms: 100}
                          - {text: 'world', start_ms: 50}
                """.trimIndent()
            )
        )
    }
}
