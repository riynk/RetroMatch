package code.name.monkey.retromusic.db

import java.net.URI
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Metadata fingerprint independent of MediaStore's row ID or file path.
 * This is not an immutable recording ID: changes to title, artist, album, or
 * duration produce a different key. Returns null when title/artist are missing.
 */
object TrackVideoIdentity {
    fun create(
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long
    ): String? {
        val normalizedTitle = normalize(title)
        val normalizedArtist = normalize(artist)
        if (normalizedTitle.isEmpty() || normalizedArtist.isEmpty()) return null

        val canonical = listOf(
            normalizedTitle,
            normalizedArtist,
            normalize(album),
            durationMs.coerceAtLeast(0L).toString()
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun normalize(value: String?): String =
        Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKC)
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
}

/** Validates supported video references before they enter the database. */
object TrackVideoUriValidator {
    fun isSupported(value: String, sourceType: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return when (sourceType) {
            "REMOTE" -> uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank()
            "LOCAL" -> uri.scheme.equals("content", ignoreCase = true) &&
                !uri.rawAuthority.isNullOrBlank()
            else -> false
        }
    }
}
