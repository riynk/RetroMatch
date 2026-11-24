/*
 * Stores original Lyricsfile YAML in app-private storage, preserving the source document.
 */
package code.name.monkey.retromusic.util

import code.name.monkey.retromusic.App
import code.name.monkey.retromusic.lyrics.ttml.LyricsFileParser
import java.io.File
import java.io.IOException

internal class LyricsFileStore(private val directory: File) {

    fun save(audioPath: String, content: String): Boolean {
        if (audioPath.isBlank() || content.isBlank()) return false
        if (content.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) return false
        val parsed = LyricsFileParser.parse(content) ?: return false
        if (parsed.lines.isEmpty()) return false

        val target = fileFor(audioPath)
        var temporary: File? = null
        return try {
            if (!directory.exists() && !directory.mkdirs()) return false
            val tempFile = File.createTempFile("${target.name}.", ".tmp", directory)
            temporary = tempFile
            tempFile.outputStream().use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            tempFile.renameTo(target)
        } catch (_: IOException) {
            false
        } finally {
            temporary?.delete()
        }
    }

    fun read(audioPath: String): String? {
        if (audioPath.isBlank()) return null
        val file = fileFor(audioPath)
        if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
        return try {
            file.readText(Charsets.UTF_8).takeIf { LyricsFileParser.parse(it) != null }
        } catch (_: IOException) {
            null
        }
    }

    fun delete(audioPath: String): Boolean =
        audioPath.isNotBlank() && fileFor(audioPath).delete()

    private fun fileFor(audioPath: String): File =
        File(directory, LyricsFileKey.create(audioPath).removeSuffix(".lrc") + ".lyricsfile.yaml")

    companion object {
        private const val MAX_FILE_BYTES = 2L * 1024L * 1024L

        fun appStore(): LyricsFileStore {
            val context = App.getContext()
            val directory = context.getExternalFilesDir("lyrics")
                ?: File(context.filesDir, "lyrics")
            return LyricsFileStore(directory)
        }
    }
}
