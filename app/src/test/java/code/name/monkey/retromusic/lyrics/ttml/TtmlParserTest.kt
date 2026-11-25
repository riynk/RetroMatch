package code.name.monkey.retromusic.lyrics.ttml

import org.junit.Assert.*
import org.junit.Test

class TtmlParserTest {

    @Test
    fun parsesWordTimingAndExplicitWhitespace() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word">
              <body><div>
                <p begin="00:00:10.000" end="00:00:14.000">
                  <span begin="00:00:10.000" end="00:00:10.500">Hello </span>
                  <span begin="00:00:10.500" end="00:00:11.000">world</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val line = TtmlParser.parseTtml(ttml).lines.single()
        assertEquals("Hello world", line.text)
        assertEquals("Hello ", line.syllabus[0].text)
        assertFalse(line.syllabus[0].continuesWord)
        assertEquals("world", line.syllabus[1].text)
        assertTrue(line.syllabus[1].continuesWord)
    }

    @Test
    fun recordsExactCharacterRangesForTimedSpans() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
              <p begin="00:00:01.000" end="00:00:02.000">
                <span begin="00:00:01.000" end="00:00:01.300">Hel</span>
                <span begin="00:00:01.300" end="00:00:01.600">lo </span>
                <span begin="00:00:01.600" end="00:00:02.000">world</span>
              </p>
            </div></body></tt>
        """.trimIndent()

        val line = TtmlParser.parseTtml(ttml).lines.single()
        assertEquals("Hello world", line.text)
        assertEquals("Hel", line.text.substring(line.syllabus[0].startIndex, line.syllabus[0].endIndex))
        assertEquals("lo ", line.text.substring(line.syllabus[1].startIndex, line.syllabus[1].endIndex))
        assertEquals("world", line.text.substring(line.syllabus[2].startIndex, line.syllabus[2].endIndex))
    }

    @Test
    fun preservesSyllableFragmentsWithoutInventingWordSpaces() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
              <p begin="00:00:01.000" end="00:00:02.000">
                <span begin="00:00:01.000" end="00:00:01.300">Hel</span>
                <span begin="00:00:01.300" end="00:00:01.600">lo </span>
                <span begin="00:00:01.600" end="00:00:02.000">world</span>
              </p>
            </div></body></tt>
        """.trimIndent()

        val line = TtmlParser.parseTtml(ttml).lines.single()
        assertEquals("Hello world", line.text)
        assertEquals(listOf("Hel", "lo ", "world"), line.syllabus.map { it.text })
        assertTrue(line.syllabus[0].continuesWord)
        assertFalse(line.syllabus[1].continuesWord)
    }

    @Test
    fun preservesBackgroundVocalTimingAndCharacterRanges() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
              <p begin="00:00:01.000" end="00:00:03.000">
                <span begin="00:00:01.000" end="00:00:02.000">Hello </span>
                <span begin="00:00:02.000" end="00:00:03.000">(oh)</span>
              </p>
            </div></body></tt>
        """.trimIndent()

        val line = TtmlParser.parseTtml(ttml).lines.single()
        assertEquals("Hello oh", line.text)
        val backingVocal = line.syllabus.last()
        assertTrue(backingVocal.isBackground)
        assertEquals("oh", line.text.substring(backingVocal.startIndex, backingVocal.endIndex))
        assertEquals(2_000L, backingVocal.timeMs)
    }

    @Test
    fun parsesEmptyAndMalformedDocumentsAsEmptyLyrics() {
        assertTrue(TtmlParser.parseTtml("").lines.isEmpty())
        assertTrue(TtmlParser.parseTtml("<tt><body>").lines.isEmpty())
    }

    @Test
    fun parsesClockTimesAndMillisecondDurations() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
              <p begin="01:30:45.500" end="01:30:50.000">
                <span begin="01:30:45.500" end="01:30:46.000">Long clock</span>
              </p>
              <p begin="05:30.250" end="05:35.000">
                <span begin="05:30.250" dur="1500ms">Millisecond duration</span>
              </p>
            </div></body></tt>
        """.trimIndent()

        val result = TtmlParser.parseTtml(ttml)
        assertEquals(2, result.lines.size)
        assertEquals(5_445_500L, result.lines[0].timeMs)
        assertEquals(330_250L, result.lines[1].timeMs)
        assertEquals(1_500L, result.lines[1].syllabus.single().durationMs)
    }

    @Test
    fun parsesOptionalTranslationAndTransliterationWithoutChangingTimedSource() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
              <p begin="00:00:01.000" end="00:00:02.000"
                 translation="Hello world" transliteration="Konnichiwa sekai">
                <span begin="00:00:01.000" end="00:00:01.500">こんにちは </span>
                <span begin="00:00:01.500" end="00:00:02.000">世界</span>
              </p>
            </div></body></tt>
        """.trimIndent()

        val line = TtmlParser.parseTtml(ttml).lines.single()
        assertEquals("こんにちは 世界", line.text)
        assertEquals("Hello world", line.translation)
        assertEquals("Konnichiwa sekai", line.transliteration)
        assertEquals(2, line.syllabus.size)
        assertEquals(1_000L, line.syllabus.first().timeMs)
    }

    @Test
    fun marksWordContinuationAcrossAdjacentLines() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
              <p begin="00:00:10.000" end="00:00:12.000">
                <span begin="00:00:10.000" end="00:00:11.000">Ro</span>
              </p>
              <p begin="00:00:12.000" end="00:00:14.000">
                <span begin="00:00:12.000" end="00:00:13.000">mance</span>
              </p>
            </div></body></tt>
        """.trimIndent()

        val result = TtmlParser.parseTtml(ttml)
        assertEquals(2, result.lines.size)
        assertTrue(result.lines[1].syllabus.single().continuesWord)
    }
}
