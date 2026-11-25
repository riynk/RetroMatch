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
        var temporary: File? = null
        return try {
            if (!directory.exists() && !directory.mkdirs()) return false
            val tempFile = File.createTempFile("${target.name}.", ".tmp", directory)
            temporary = tempFile
            tempFile.outputStream().use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            // Keep the previous file intact unless the complete replacement can be renamed
            // into place. The temporary file is in the same directory for an atomic rename.
            tempFile.renameTo(target)
        } catch (_: IOException) {
            false
        } finally {
            temporary?.delete()
        }
    }

    fun read(audioPath: String): TtmlLyrics? {
        if (audioPath.isBlank()) return null

        // Prefer the app-managed copy. It remains available even when Android does
        // not grant direct access to the audio folder's neighboring files.
        val managedFile = fileFor(audioPath)
        readTtmlFile(managedFile)?.let { return it }

        // LocalPlayer source behavior: discover a same-basename TTML sidecar next
        // to the audio file automatically. Scoped-storage/SAF restrictions may
        // make this unavailable; in that case the managed copy remains the source.
        val audioFile = File(audioPath)
        val parent = audioFile.parentFile ?: return null
        val sidecar = File(parent, "${audioFile.nameWithoutExtension}.ttml")
        val parsed = readTtmlFile(sidecar) ?: return null

        // Cache a readable sidecar in app-private storage so subsequent playback
        // does not depend on continued access to the shared music directory.
        runCatching { save(audioPath, sidecar.readText(Charsets.UTF_8)) }
        return parsed
    }

    private fun readTtmlFile(file: File): TtmlLyrics? {
        if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
        return try {
            TtmlParser.parseTtml(file.readText(Charsets.UTF_8))
                .takeIf { it.lines.isNotEmpty() }
        } catch (_: Exception) {
            // A neighboring sidecar may be blocked by scoped storage or revoked access.
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
