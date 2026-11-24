/*
 * RetroMatch Lyricsfile 1.0 parser.
 * Lyricsfile 1.0 draft: https://github.com/tranxuanthang/lyricsfile
 * Parser dependency: SnakeYAML (Apache License 2.0), SafeConstructor only.
 */
package code.name.monkey.retromusic.lyrics.ttml

import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLine
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlMetadata
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlSyllable
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Reads Lyricsfile 1.0 into RetroMatch's shared timed-lyrics model.
 *
 * Do not apply metadata.offset_ms: the 1.0 draft explicitly leaves its meaning
 * unresolved. Missing end_ms values remain absent (represented as duration 0)
 * rather than being inferred and written back as if they were source data.
 */
object LyricsFileParser {

    private const val FORMAT_VERSION = "1.0"
    private const val MAX_DOCUMENT_CHARS = 2 * 1024 * 1024
    private const val MAX_LINES = 10_000
    private const val MAX_WORDS_PER_LINE = 500

    fun parse(content: String): TtmlLyrics? {
        if (content.isBlank() || content.length > MAX_DOCUMENT_CHARS) return null

        return try {
            val options = LoaderOptions().apply {
                setAllowDuplicateKeys(false)
                setMaxAliasesForCollections(0)
                setCodePointLimit(MAX_DOCUMENT_CHARS)
                setNestingDepthLimit(50)
            }
            val document = Yaml(SafeConstructor(options)).load<Any?>(content)
                as? Map<*, *> ?: return null
            if (document["version"]?.toString() != FORMAT_VERSION) return null

            val metadata = document["metadata"] as? Map<*, *> ?: return null
            if (metadata["title"] !is String || metadata["artist"] !is String) return null
            if (metadata["instrumental"] == true) return null

            val sourceLines = document["lines"] as? List<*> ?: return null
            if (sourceLines.isEmpty() || sourceLines.size > MAX_LINES) return null

            val lines = sourceLines.mapNotNull { source ->
                val line = source as? Map<*, *> ?: return@mapNotNull null
                val sourceText = line["text"] as? String ?: return@mapNotNull null
                val startMs = line["start_ms"].asLongOrNull() ?: return@mapNotNull null
                if (startMs < 0L) return@mapNotNull null
                val endMs = line["end_ms"].asLongOrNull()
                if (endMs != null && endMs < startMs) return@mapNotNull null

                val rawWords = line["words"] as? List<*> ?: emptyList<Any?>()
                if (rawWords.size > MAX_WORDS_PER_LINE) return@mapNotNull null
                val words = rawWords.mapNotNull { word ->
                    val item = word as? Map<*, *> ?: return@mapNotNull null
                    val text = item["text"] as? String ?: return@mapNotNull null
                    val wordStart = item["start_ms"].asLongOrNull()
                        ?: return@mapNotNull null
                    val wordEnd = item["end_ms"].asLongOrNull()
                    if (text.isEmpty() || wordStart < 0L ||
                        (wordEnd != null && wordEnd < wordStart)
                    ) {
                        return@mapNotNull null
                    }
                    WordTiming(text = text, startMs = wordStart, endMs = wordEnd)
                }

                // Preserve the source order. Out-of-order words indicate invalid
                // timing for the current draft; overlapping but ordered words are allowed.
                if (words.size != rawWords.size ||
                    words.zipWithNext().any { (first, second) -> first.startMs > second.startMs }
                ) {
                    return@mapNotNull null
                }

                val syllables = words.map { word ->
                    TtmlSyllable(
                        text = word.text,
                        timeMs = word.startMs,
                        durationMs = word.endMs?.minus(word.startMs) ?: 0L
                    )
                }

                TtmlLine(
                    timeMs = startMs,
                    durationMs = endMs?.minus(startMs) ?: 0L,
                    text = sourceText,
                    syllabus = syllables
                )
            }

            if (lines.size != sourceLines.size || lines.isEmpty()) return null
            TtmlLyrics(
                type = if (lines.any { it.syllabus.isNotEmpty() }) "Word" else "Line",
                metadata = TtmlMetadata(
                    source = "LRCLIB Lyricsfile",
                    title = metadata["title"] as String,
                    language = metadata["language"] as? String ?: ""
                ),
                lines = lines
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class WordTiming(
        val text: String,
        val startMs: Long,
        val endMs: Long?
    )

    private fun Any?.asLongOrNull(): Long? = when (this) {
        is Byte, is Short, is Int, is Long -> (this as Number).toLong()
        else -> null
    }
}
