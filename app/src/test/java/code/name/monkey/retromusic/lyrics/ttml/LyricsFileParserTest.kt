package code.name.monkey.retromusic.lyrics.ttml

import org.junit.Assert.*
import org.junit.Test

class LyricsFileParserTest {

    @Test
    fun parsesWordSyncedLyricsAndInfersMissingWordEnds() {
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
                  - text: 'synchronized '
                    start_ms: 12800
                  - text: 'lyric '
                    start_ms: 14000
                  - text: 'line'
                    start_ms: 14800
            plain: |
              A synchronized lyric line
        """.trimIndent()

        val line = LyricsFileParser.parse(lyricsfile)!!.lines.single()
        assertEquals("A synchronized lyric line", line.text)
        assertEquals(12_000L, line.timeMs)
        assertEquals(3_500L, line.durationMs)
        assertEquals(listOf("A ", "synchronized ", "lyric ", "line"), line.syllabus.map { it.text })
        assertEquals(800L, line.syllabus[0].durationMs)
        assertEquals(1_200L, line.syllabus[1].durationMs)
        assertEquals(700L, line.syllabus[2].durationMs)
        assertEquals(700L, line.syllabus[3].durationMs)
        assertEquals("Small Hours", LyricsFileParser.parse(lyricsfile)!!.metadata.title)
        assertEquals("en", LyricsFileParser.parse(lyricsfile)!!.metadata.language)
    }

    @Test
    fun appliesGlobalOffsetToLineAndWordTimestamps() {
        val lyricsfile = """
            version: '1.0'
            metadata:
              offset_ms: -100
            lines:
              - text: 'Hello'
                start_ms: 1000
                end_ms: 2000
                words:
                  - text: 'Hello'
                    start_ms: 1000
                    end_ms: 2000
        """.trimIndent()

        val line = LyricsFileParser.parse(lyricsfile)!!.lines.single()
        assertEquals(900L, line.timeMs)
        assertEquals(900L, line.syllabus.single().timeMs)
        assertEquals(1_000L, line.syllabus.single().durationMs)
    }

    @Test
    fun acceptsLineSyncedLyricsWithoutWordArray() {
        val lyricsfile = """
            version: "1.0"
            metadata:
              title: "Line synced"
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
        assertNull(LyricsFileParser.parse("version: '2.0'\nlines: []"))
        assertNull(LyricsFileParser.parse("version: ["))
        assertNull(
            LyricsFileParser.parse(
                "version: '1.0'\nmetadata:\n  instrumental: true\nlines: []"
            )
        )
    }

    @Test
    fun serializedWordSyncedLyricsRoundTripThroughTtmlParser() {
        val lyricsfile = """
            version: '1.0'
            metadata:
              title: "Escape & <"
            lines:
              - text: "Hello & <world>"
                start_ms: 1000
                end_ms: 2500
                words:
                  - text: "Hello "
                    start_ms: 1000
                    end_ms: 1700
                  - text: "& <world>"
                    start_ms: 1700
                    end_ms: 2500
        """.trimIndent()

        val source = LyricsFileParser.parse(lyricsfile)!!
        val ttml = TtmlLyricsSerializer.serialize(source)!!
        val restored = TtmlParser.parseTtml(ttml)
        assertEquals("Hello & <world>", restored.lines.single().text)
        assertEquals(listOf("Hello ", "& <world>"), restored.lines.single().syllabus.map { it.text })
        assertEquals(1_700L, restored.lines.single().syllabus[1].timeMs)
    }
}
