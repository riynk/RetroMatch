package code.name.monkey.retromusic.util

import java.io.File

/**
 * Resolves an LRC sidecar next to an audio file without modifying parent-directory names.
 */
internal object LrcSidecarPath {
    fun fromAudioPath(audioPath: String): String {
        val audioFile = File(audioPath)
        val baseName = audioFile.name.substringBeforeLast('.', audioFile.name)
        return File(audioFile.parentFile, "$baseName.lrc").path
    }
}
