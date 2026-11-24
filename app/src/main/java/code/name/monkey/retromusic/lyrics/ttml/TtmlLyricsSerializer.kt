/*
 * Serializes the shared timed-lyrics model to TTML for existing sidecar storage.
 */
package code.name.monkey.retromusic.lyrics.ttml

import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics
import java.util.Locale

object TtmlLyricsSerializer {

    fun serialize(lyrics: TtmlLyrics): String? {
        if (lyrics.lines.isEmpty()) return null
        return buildString {
            val timingMode = if (lyrics.lines.any { it.syllabus.isNotEmpty() }) "Word" else "Line"
            append("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="$timingMode"><body><div>""")
            lyrics.lines.forEach { line ->
                val lineEnd = line.timeMs + line.durationMs
                append("<p begin=\"")
                    .append(formatTime(line.timeMs))
                    .append("\" end=\"")
                    .append(formatTime(lineEnd))
                    .append("\">")
                if (line.syllabus.isEmpty()) {
                    append(escapeXml(line.text))
                } else {
                    line.syllabus.forEach { word ->
                        val wordEnd = word.timeMs + word.durationMs
                        append("<span begin=\"")
                            .append(formatTime(word.timeMs))
                            .append("\" end=\"")
                            .append(formatTime(wordEnd))
                            .append("\">")
                            .append(escapeXml(word.text))
                            .append("</span>")
                    }
                }
                append("</p>")
            }
            append("</div></body></tt>")
        }
    }

    private fun formatTime(timeMs: Long): String {
        val safeTime = timeMs.coerceAtLeast(0L)
        val hours = safeTime / 3_600_000L
        val minutes = (safeTime / 60_000L) % 60L
        val seconds = (safeTime / 1_000L) % 60L
        val millis = safeTime % 1_000L
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
