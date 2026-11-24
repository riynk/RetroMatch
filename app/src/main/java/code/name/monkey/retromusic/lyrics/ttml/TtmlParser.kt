/*
 * Adapted from LocalPlayer (MIT License).
 * Copyright (c) 2026 Christian (cvc953).
 * Original project license: https://github.com/riynk/RetroMatch/blob/integration/localplayer/LICENSE
 */
package code.name.monkey.retromusic.lyrics.ttml

import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLine
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlMetadata
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlSyllable
import org.xmlpull.v1.XmlPullParser
import org.kxml2.io.KXmlParser

/**
 * Parses TTML lyrics while preserving the source whitespace.
 *
 * A span's trailing whitespace is significant: it separates words. A span
 * without trailing whitespace may continue into the next timed span (for
 * example, syllable-level karaoke). The parser must not guess word boundaries
 * by inserting spaces where the TTML document has none.
 */
object TtmlParser {

    private const val NS_ITUNES = "http://music.apple.com/lyric-ttml-internal"

    fun parseTtml(content: String): TtmlLyrics {
        if (content.isBlank()) return TtmlLyrics()
        return try {
            val parser = KXmlParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(content.reader())
            }
            parseTtmlDocument(parser)
        } catch (_: Exception) {
            TtmlLyrics()
        }
    }

    private fun parseTtmlDocument(parser: XmlPullParser): TtmlLyrics {
        val lines = mutableListOf<TtmlLine>()
        var timingMode = "Word"

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "tt" -> timingMode =
                        parser.getAttributeValue(NS_ITUNES, "timing") ?: "Word"
                    "div" -> lines.addAll(parseDiv(parser))
                }
            }
            eventType = parser.next()
        }

        // A word may be split across adjacent paragraph lines. Preserve the
        // source's text and mark only the continuation metadata on the next line.
        for (i in 1 until lines.size) {
            val previous = lines[i - 1]
            val current = lines[i]
            val lastPrevious = previous.syllabus.lastOrNull()
            val firstCurrent = current.syllabus.firstOrNull()
            if (lastPrevious != null && firstCurrent != null &&
                !lastPrevious.text.endsWithWhitespace() &&
                !firstCurrent.text.startsWithWhitespace()
            ) {
                val updated = current.syllabus.toMutableList()
                updated[0] = firstCurrent.copy(continuesWord = true)
                lines[i] = current.copy(syllabus = updated)
            }
        }

        return TtmlLyrics(type = timingMode, metadata = TtmlMetadata(), lines = lines)
    }

    private fun parseDiv(parser: XmlPullParser): List<TtmlLine> {
        val lines = mutableListOf<TtmlLine>()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "p") {
                        parseParagraph(parser)?.let(lines::add)
                    } else {
                        depth++
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "div") depth--
            }
        }
        return lines
    }

    private fun parseParagraph(parser: XmlPullParser): TtmlLine? {
        val begin = parser.getAttributeValue(null, "begin")
        val end = parser.getAttributeValue(null, "end")
        if (begin == null || end == null) {
            consumeElement(parser, "p")
            return null
        }

        val timeMs = parseTime(begin)
        val endMs = parseTime(end)
        val syllabus = mutableListOf<TtmlSyllable>()
        val textBuilder = StringBuilder()
        var insideParenthesis = false
        var depth = 1

        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "span") {
                        val syllable = parseSpan(parser, insideParenthesis)
                        if (syllable != null) {
                            if (syllable.text.startsWith("(")) insideParenthesis = true
                            val backgroundSyllable = if (insideParenthesis) {
                                syllable.copy(isBackground = true)
                            } else {
                                syllable
                            }
                            val cleanedText = backgroundSyllable.text
                                .replace("(", "")
                                .replace(")", "")
                            if (cleanedText.isNotEmpty()) {
                                val continuesWord = !cleanedText.endsWithWhitespace()
                                val cleaned = backgroundSyllable.copy(
                                    text = cleanedText,
                                    continuesWord = continuesWord
                                )
                                syllabus.add(cleaned)
                                textBuilder.append(cleanedText)
                            }
                            if (syllable.text.endsWith(")")) insideParenthesis = false
                        }
                    } else {
                        depth++
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    // Ignore formatting indentation between spans, but preserve
                    // whitespace that is part of a timed span's own text.
                    if (!text.isNullOrBlank()) textBuilder.append(text)
                }
                XmlPullParser.END_TAG -> if (parser.name == "p") depth--
            }
        }

        val lineText = textBuilder.toString().trim()
        if (lineText.isEmpty() && syllabus.isEmpty()) return null

        return TtmlLine(
            timeMs = timeMs,
            durationMs = (endMs - timeMs).coerceAtLeast(0L),
            text = lineText,
            syllabus = syllabus
        )
    }

    private fun parseSpan(
        parser: XmlPullParser,
        isBackground: Boolean = false
    ): TtmlSyllable? {
        val begin = parser.getAttributeValue(null, "begin")
        val end = parser.getAttributeValue(null, "end")
        val duration = parser.getAttributeValue(null, "dur")

        if (begin == null || (end == null && duration == null)) {
            consumeElement(parser, "span")
            return null
        }

        val timeMs = parseTime(begin)
        val durationMs = when {
            end != null -> (parseTime(end) - timeMs).coerceAtLeast(0L)
            else -> parseDuration(duration!!)
        }

        val text = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT -> parser.text?.let(text::append)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> if (parser.name == "span") depth--
            }
        }

        val value = text.toString()
        if (value.isBlank()) return null
        return TtmlSyllable(
            text = value,
            timeMs = timeMs,
            durationMs = durationMs,
            isBackground = isBackground
        )
    }

    private fun consumeElement(parser: XmlPullParser, elementName: String) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth == 0 && parser.name != elementName) return
                }
            }
        }
    }

    private fun String.endsWithWhitespace(): Boolean =
        isNotEmpty() && last().isWhitespace()

    private fun String.startsWithWhitespace(): Boolean =
        isNotEmpty() && first().isWhitespace()

    /** Supports TTML clock values in HH:MM:SS.mmm and MM:SS.mmm forms. */
    private fun parseTime(value: String): Long {
        val parts = value.split(":")
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return 0L
                val minutes = parts[1].toLongOrNull() ?: return 0L
                val seconds = parts[2].toDoubleOrNull() ?: return 0L
                if (minutes !in 0..59 || seconds < 0.0 || seconds >= 60.0) return 0L
                hours * 3_600_000L + minutes * 60_000L + (seconds * 1000).toLong()
            }
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return 0L
                val seconds = parts[1].toDoubleOrNull() ?: return 0L
                if (seconds < 0.0 || seconds >= 60.0) return 0L
                minutes * 60_000L + (seconds * 1000).toLong()
            }
            else -> 0L
        }
    }

    /** Supports numeric milliseconds and TTML duration suffixes such as 1.5s/1500ms. */
    private fun parseDuration(value: String): Long {
        val duration = when {
            value.endsWith("ms") ->
                value.removeSuffix("ms").toDoubleOrNull()?.let { it }
            value.endsWith("s") ->
                value.removeSuffix("s").toDoubleOrNull()?.times(1000.0)
            else -> value.toDoubleOrNull()
        } ?: return 0L
        if (!duration.isFinite() || duration < 0.0) return 0L
        return duration.toLong()
    }
}
