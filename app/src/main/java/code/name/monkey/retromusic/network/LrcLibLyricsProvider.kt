/*
 * RetroMatch LRCLIB integration.
 * Adapted from the LRCLIB integration approach in TimeLyr (MIT).
 * This file is a native Kotlin implementation; no Flutter code/runtime is used.
 */
package code.name.monkey.retromusic.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

internal interface LrcLibApi {
    @GET("api/get")
    suspend fun getExact(
        @Query("artist_name") artist: String,
        @Query("track_name") title: String,
        @Query("album_name") album: String,
        @Query("duration") durationSeconds: Int,
    ): Response<LrcLibTrackDto>

    @GET("api/search")
    suspend fun search(@Query("q") query: String): List<LrcLibTrackDto>
}

internal data class LrcLibTrackDto(
    val id: Int = 0,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

data class FetchedLyrics(
    val sourceId: Int,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double,
    val plainLyrics: String,
    val syncedLyrics: String,
    val instrumental: Boolean,
) {
    /** Prefer synchronized lyrics; fall back to plain text. */
    val preferredLyrics: String
        get() = syncedLyrics.takeIf { it.hasTimestamp() } ?: plainLyrics

    val hasLyrics: Boolean
        get() = preferredLyrics.isNotBlank()
}

interface LyricsProvider {
    suspend fun fetchLyrics(
        artist: String,
        title: String,
        album: String,
        durationMillis: Long,
    ): FetchedLyrics?
}

internal class LrcLibLyricsProvider internal constructor(
    private val api: LrcLibApi,
) : LyricsProvider {

    override suspend fun fetchLyrics(
        artist: String,
        title: String,
        album: String,
        durationMillis: Long,
    ): FetchedLyrics? {
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        val cleanAlbum = album.trim()
        if (cleanArtist.isBlank() || cleanTitle.isBlank()) return null

        val durationSeconds = if (durationMillis > 0L) {
            (durationMillis / 1000.0).roundToSeconds()
        } else {
            0
        }

        val exactResponse = api.getExact(
            artist = cleanArtist,
            title = cleanTitle,
            album = cleanAlbum,
            durationSeconds = durationSeconds,
        )
        if (exactResponse.isSuccessful) {
            exactResponse.body()?.toFetchedLyrics()?.takeIf { it.hasLyrics }?.let {
                return it
            }
        } else if (exactResponse.code() !in setOf(400, 404)) {
            exactResponse.errorBody()?.close()
            exactResponse.code().throwIfTemporaryOrUnexpected()
        }

        val query = listOf(cleanArtist, cleanTitle, cleanAlbum)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val candidates = api.search(query)
        return candidates
            .asSequence()
            .map { candidate -> candidate to candidate.score(cleanArtist, cleanTitle, cleanAlbum, durationSeconds) }
            .sortedByDescending { it.second }
            .toList()
            .takeIf { ranked ->
                ranked.isNotEmpty() &&
                    ranked.first().second >= MIN_SCORE &&
                    (ranked.size == 1 || ranked.first().second - ranked[1].second >= MIN_SCORE_GAP)
            }
            ?.first()
            ?.first
            ?.toFetchedLyrics()
            ?.takeIf { it.hasLyrics }
    }

    private fun LrcLibTrackDto.score(
        artist: String,
        title: String,
        album: String,
        durationSeconds: Int,
    ): Double {
        var score = fieldScore(title.normalizeMusicText(), trackName.orEmpty().normalizeMusicText(), 0.55, 0.35)
        score += fieldScore(artist.primaryArtist().normalizeMusicText(), artistName.orEmpty().primaryArtist().normalizeMusicText(), 0.35, 0.20)
        score += fieldScore(album.normalizeMusicText(), albumName.orEmpty().normalizeMusicText(), 0.10, 0.04)

        val candidateDuration = duration?.roundToSeconds() ?: 0
        if (durationSeconds > 0 && candidateDuration > 0) {
            val difference = kotlin.math.abs(durationSeconds - candidateDuration)
            score += when {
                difference <= 2 -> 0.20
                difference <= 6 -> 0.12
                difference <= 12 -> 0.06
                else -> -0.15
            }
        }
        return score
    }

    private fun LrcLibTrackDto.toFetchedLyrics() = FetchedLyrics(
        sourceId = id,
        title = trackName.orEmpty(),
        artist = artistName.orEmpty(),
        album = albumName.orEmpty(),
        durationSeconds = duration ?: 0.0,
        plainLyrics = plainLyrics.orEmpty(),
        syncedLyrics = syncedLyrics.orEmpty(),
        instrumental = instrumental ?: false,
    )

    private fun fieldScore(source: String, target: String, exact: Double, partial: Double): Double {
        if (source.isBlank() || target.isBlank()) return 0.0
        if (source == target) return exact
        if (source.contains(target) || target.contains(source)) return partial
        val sourceTokens = source.split(' ').filter { it.isNotBlank() }.toSet()
        val targetTokens = target.split(' ').filter { it.isNotBlank() }.toSet()
        if (sourceTokens.isEmpty() || targetTokens.isEmpty()) return 0.0
        return (sourceTokens intersect targetTokens).size.toDouble() /
            (sourceTokens union targetTokens).size * partial
    }

    private fun String.normalizeMusicText(): String {
        val normalized = java.text.Normalizer.normalize(this.lowercase(), java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{Mn}"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.primaryArtist(): String =
        split(Regex("\\s*(,|&|;|/| x | and | feat\\.? | ft\\.? )\\s*", RegexOption.IGNORE_CASE))
            .firstOrNull()
            .orEmpty()
            .trim()

    private fun Double.roundToSeconds(): Int = kotlin.math.round(this).toInt()
    private fun Long.roundToSeconds(): Int = kotlin.math.round(this.toDouble()).toInt()

    private fun Int.throwIfTemporaryOrUnexpected() {
        if (this == 429 || this >= 500) {
            throw java.io.IOException("LRCLIB temporary server error (HTTP $this)")
        }
        throw java.io.IOException("LRCLIB request failed (HTTP $this)")
    }

    private fun String.hasTimestamp(): Boolean =
        Regex("\\[\\d{1,3}:\\d{2}(?:\\.\\d{1,3})?]").containsMatchIn(this)

    private companion object {
        const val MIN_SCORE = 0.72
        const val MIN_SCORE_GAP = 0.08
    }
}
