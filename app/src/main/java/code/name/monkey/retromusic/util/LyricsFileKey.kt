package code.name.monkey.retromusic.util

import java.security.MessageDigest

/**
 * Produces a stable, filesystem-safe private LRC filename per audio file.
 *
 * Title/artist-only filenames collide for duplicate releases and multi-disc albums.
 * The audio path distinguishes those tracks without relying on transient MediaStore IDs.
 */
internal object LyricsFileKey {
    fun create(audioPath: String): String {
        val normalizedPath = audioPath.trim().replace('\\', '/')
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizedPath.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte) }
        return "track-$digest.lrc"
    }
}
