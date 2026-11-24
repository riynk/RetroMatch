/*
 * RetroMatch Lyricsfile 1.0 parser.
 * Lyricsfile is a public-domain open lyrics format (CC0 1.0).
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
 * Converts the documented Lyricsfile 1.0 word timings to RetroMatch's shared
 * timed-lyrics model. Unknown versions and malformed documents are rejected.
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
            }
            val document = Yaml(SafeConstructor(options)).load<Any?>(content)
                as? Map<*, *> ?: return null
            if (document["version"]?.toString() != FORMAT_VERSION) return null

            val metadata = document["metadata"] as? Map<*, *>
            if (metadata?.get("instrumental") == true) return null
            val offsetMs = metadata?.get("offset_ms").asLongOrNull() ?: 0L
            val sourceLines = document["lines"] as? List<*> ?: return null
            if (sourceLines.isEmpty() || sourceLines.size > MAX_LINES) return null

            val lines = sourceLines.mapNotNull { source ->
                val line = source as? Map<*, *> ?: return@mapNotNull null
                val sourceText = line["text"] as? String ?: return@mapNotNull null
                val rawStart = line["start_ms"].asLongOrNull() ?: return@mapNotNull null
                val startMs = (rawStart + offsetMs).coerceAtLeast(0L)
                val rawEnd = line["end_ms"].asLongOrNull()
                val endMs = rawEnd?.let { (it + offsetMs).coerceAtLeast(startMs) }

                val rawWords = line["words"] as? List<*>
                val words = rawWords
                    ?.take(MAX_WORDS_PER_LINE)
                    ?.mapNotNull { word ->
                        val item = word as? Map<*, *> ?: return@mapNotNull null
                        val text = item["text"] as? String ?: return@mapNotNull null
                        val wordStart = item["start_ms"].asLongOrNull()
                            ?: return@mapNotNull null
                        if (text.isEmpty()) return@mapNotNull null
                        WordTiming(
                            text = text,
                            startMs = (wordStart + offsetMs).coerceAtLeast(0L),
                            endMs = item["end_ms"].asLongOrNull()
                                ?.let { (it + offsetMs).coerceAtLeast(0L) }
                        )
                    }
                    .orEmpty()
                if (words.zipWithNext().any { (first, second) -> first.startMs > second.startMs }) {
                    return@mapNotNull null
                }

                val syllables = words.mapIndexed { index, word ->
                    val inferredEnd = words.getOrNull(index + 1)?.startMs ?: endMs
                    val wordEnd = word.endMs ?: inferredEnd
                    TtmlSyllable(
                        text = word.text,
                        timeMs = word.startMs,
                        durationMs = wordEnd?.minus(word.startMs)?.coerceAtLeast(0L) ?: 0L
                    )
                }

                val inferredLineEnd = endMs ?: syllables.lastOrNull()?.let {
                    it.timeMs + it.durationMs
                }
                TtmlLine(
                    timeMs = startMs,
                    durationMs = inferredLineEnd?.minus(startMs)?.coerceAtLeast(0L) ?: 0L,
                    text = sourceText,
                    syllabus = syllables
                )
            }

            if (lines.isEmpty()) return null
            TtmlLyrics(
                type = if (lines.any { it.syllabus.isNotEmpty() }) "Word" else "Line",
                metadata = TtmlMetadata(
                    source = "LRCLIB Lyricsfile",
                    title = metadata?.get("title") as? String ?: "",
                    language = metadata?.get("language") as? String ?: ""
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
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }
}
