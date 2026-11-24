/*
 * RetroMatch TTML sidecar persistence.
 * TTML is stored separately from LRC because word-level timing must not be
 * flattened into the line-based synchronized-lyrics format.
 */
package code.name.monkey.retromusic.util

import code.name.monkey.retromusic.App
import code.name.monkey.retromusic.lyrics.ttml.TtmlParser
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics
import java.io.File
import java.io.IOException

internal class TtmlLyricsStore(private val directory: File) {

    fun save(audioPath: String, content: String): Boolean {
        if (audioPath.isBlank() || content.isBlank()) return false
        val parsed = TtmlParser.parseTtml(content)
        if (parsed.lines.isEmpty()) return false

        val target = fileFor(audioPath)
        val temporary = File(directory, "${target.name}.tmp")
        return try {
            if (!directory.exists() && !directory.mkdirs()) return false
            temporary.writeText(content, Charsets.UTF_8)
            if (target.exists() && !target.delete()) return false
            if (temporary.renameTo(target)) {
                true
            } else {
                target.writeText(content, Charsets.UTF_8)
                temporary.delete()
                true
            }
        } catch (_: IOException) {
            temporary.delete()
            false
        }
    }

    fun read(audioPath: String): TtmlLyrics? {
        if (audioPath.isBlank()) return null
        val file = fileFor(audioPath)
        if (!file.isFile) return null
        return try {
            TtmlParser.parseTtml(file.readText(Charsets.UTF_8))
                .takeIf { it.lines.isNotEmpty() }
        } catch (_: IOException) {
            null
        }
    }

    fun delete(audioPath: String): Boolean =
        audioPath.isNotBlank() && fileFor(audioPath).delete()

    private fun fileFor(audioPath: String): File =
        File(directory, LyricsFileKey.create(audioPath).removeSuffix(".lrc") + ".ttml")

    companion object {
        fun appStore(): TtmlLyricsStore {
            val context = App.getContext()
            val directory = context.getExternalFilesDir("lyrics")
                ?: File(context.filesDir, "lyrics")
            return TtmlLyricsStore(directory)
        }
    }
}
