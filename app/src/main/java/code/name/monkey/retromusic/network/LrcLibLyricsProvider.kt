/*
 * RetroMatch LRCLIB integration.
 * Adapted from the LRCLIB integration approach in TimeLyr (MIT).
 * This file is a native Kotlin implementation; no Flutter code/runtime is used.
 */
package code.name.monkey.retromusic.network

import kotlinx.coroutines.delay
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import kotlin.math.round
import kotlin.random.Random

internal interface LrcLibApi {
    @GET("api/get")
    suspend fun getExact(
        @Query("artist_name") artist: String,
        @Query("track_name") title: String,
        @Query("album_name") album: String,
        @Query("duration") durationSeconds: Int,
    ): Response<LrcLibTrackDto>

    @GET("api/search")
    suspend fun search(@Query("q") query: String): Response<List<LrcLibTrackDto>>
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
    val lyricsfile: String? = null,
)

internal object LyricsText {
    private val timestampPattern = Regex("""\[\d+:[0-5]\d(?:\.\d{1,3})?]""")

    fun hasTimestamps(value: String): Boolean = timestampPattern.containsMatchIn(value)

    fun normalizeMusicText(value: String): String {
        val normalized = java.text.Normalizer.normalize(
            value.lowercase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD
        )
        return normalized.replace(Regex("\\p{Mn}"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun primaryArtist(value: String): String =
        value.split(Regex("\\s*(,|&|;|/| x | and | feat\\.? | ft\\.? )\\s*", RegexOption.IGNORE_CASE))
            .firstOrNull()
            .orEmpty()
            .trim()

    fun fieldScore(source: String, target: String, exact: Double, partial: Double): Double {
        if (source.isBlank() || target.isBlank()) return 0.0
        if (source == target) return exact
        if (source.contains(target) || target.contains(source)) return partial
        val sourceTokens = source.split(' ').filter { it.isNotBlank() }.toSet()
        val targetTokens = target.split(' ').filter { it.isNotBlank() }.toSet()
        if (sourceTokens.isEmpty() || targetTokens.isEmpty()) return 0.0
        return (sourceTokens intersect targetTokens).size.toDouble() /
            (sourceTokens union targetTokens).size * partial
    }

    /** Keep the album-qualified search, then retry without album if it is too restrictive. */
    fun searchQueries(artist: String, title: String, album: String): List<String> =
        listOf(
            listOf(artist, title, album).filter { it.isNotBlank() }.joinToString(" "),
            listOf(artist, title).filter { it.isNotBlank() }.joinToString(" "),
        ).filter { it.isNotBlank() }.distinct()

    /** Reject candidates that only happen to share an album or a duration. */
    fun isPlausibleCandidate(
        requestedArtist: String,
        requestedTitle: String,
        candidateArtist: String,
        candidateTitle: String,
    ): Boolean {
        val titleScore = fieldScore(
            normalizeMusicText(requestedTitle),
            normalizeMusicText(candidateTitle),
            1.0,
            0.65
        )
        val artistScore = fieldScore(
            normalizeMusicText(primaryArtist(requestedArtist)),
            normalizeMusicText(primaryArtist(candidateArtist)),
            1.0,
            0.65
        )
        return titleScore >= MIN_TITLE_MATCH && artistScore >= MIN_ARTIST_MATCH
    }

    private const val MIN_TITLE_MATCH = 0.35
    private const val MIN_ARTIST_MATCH = 0.35
}

data class FetchedLyrics(
    val sourceId: Int,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double,
    val plainLyrics: String,
    val syncedLyrics: String,
    val instrumental: Boolean,
    val lyricsfile: String? = null,
) {
    val preferredLyrics: String
        get() = syncedLyrics.takeIf { LyricsText.hasTimestamps(it) } ?: plainLyrics

    val hasLyrics: Boolean
        get() = !instrumental && (preferredLyrics.isNotBlank() || !lyricsfile.isNullOrBlank())
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

        if (durationSeconds in 1..3600) {
            val exactResponse = retryOnceAfterRateLimit {
                api.getExact(
                    artist = cleanArtist,
                    title = cleanTitle,
                    album = cleanAlbum,
                    durationSeconds = durationSeconds,
                )
            }
            if (exactResponse.isSuccessful) {
                exactResponse.body()?.toFetchedLyrics()?.takeIf { it.hasLyrics }?.let {
                    return it
                }
            } else {
                exactResponse.errorBody()?.close()
                if (exactResponse.code() !in setOf(400, 404)) {
                    exactResponse.code().throwIfTemporaryOrUnexpected()
                }
            }
            delay(250L)
        }

        // Search with album first, then retry using artist/title only. Wait between
        // calls as required by LRCLIB's public API guidance.
        val candidatesByIdentity = linkedMapOf<String, LrcLibTrackDto>()
        LyricsText.searchQueries(cleanArtist, cleanTitle, cleanAlbum)
            .forEachIndexed { index, query ->
                if (index > 0) delay(250L)
                val response = retryOnceAfterRateLimit { api.search(query) }
                val candidates = if (response.isSuccessful) {
                    response.body().orEmpty()
                } else {
                    response.errorBody()?.close()
                    if (response.code() == 400 || response.code() == 404) {
                        emptyList()
                    } else {
                        throw HttpException(response)
                    }
                }
                candidates.forEach { candidate ->
                    val identity = candidate.id.takeIf { it > 0 }?.let { "id:$it" }
                        ?: listOf(candidate.trackName, candidate.artistName, candidate.albumName)
                            .joinToString("|") { it.orEmpty().lowercase(java.util.Locale.ROOT) }
                    candidatesByIdentity.putIfAbsent(identity, candidate)
                }
            }

        val ranked = candidatesByIdentity.values
            .filter { candidate ->
                candidate.toFetchedLyrics().hasLyrics &&
                    LyricsText.isPlausibleCandidate(
                        cleanArtist,
                        cleanTitle,
                        candidate.artistName.orEmpty(),
                        candidate.trackName.orEmpty()
                    )
            }
            .map { candidate ->
                candidate to candidate.score(cleanArtist, cleanTitle, cleanAlbum, durationSeconds)
            }
            .sortedByDescending { it.second }

        return ranked
            .takeIf { results ->
                results.isNotEmpty() &&
                    results.first().second >= MIN_SCORE &&
                    (results.size == 1 || results.first().second - results[1].second >= MIN_SCORE_GAP)
            }
            ?.first()
            ?.first
            ?.toFetchedLyrics()
            ?.takeIf { it.hasLyrics }
    }

    private suspend fun <T> retryOnceAfterRateLimit(
        request: suspend () -> Response<T>
    ): Response<T> {
        val response = request()
        if (response.code() != 429) return response

        val retryAfterSeconds = response.headers()["Retry-After"]?.trim()?.toLongOrNull()
        response.errorBody()?.close()
        if (retryAfterSeconds == null || retryAfterSeconds <= 0L) {
            throw java.io.IOException("LRCLIB rate limited the request without a valid Retry-After header")
        }

        if (retryAfterSeconds > MAX_AUTOMATIC_RETRY_SECONDS) {
            throw java.io.IOException(
                "LRCLIB requested a $retryAfterSeconds-second retry delay; retry was not attempted early"
            )
        }
        val delayMs = retryAfterSeconds * 1000L
        val jitterLimit = (delayMs * 0.30).toLong().coerceAtLeast(1L)
        delay(delayMs + Random.nextLong(0L, jitterLimit))
        // Retry only once; a second 429 is surfaced to the caller instead of looping.
        return request()
    }

    private fun LrcLibTrackDto.score(
        artist: String,
        title: String,
        album: String,
        durationSeconds: Int,
    ): Double {
        var score = LyricsText.fieldScore(
            LyricsText.normalizeMusicText(title),
            LyricsText.normalizeMusicText(trackName.orEmpty()),
            0.55,
            0.35
        )
        score += LyricsText.fieldScore(
            LyricsText.normalizeMusicText(LyricsText.primaryArtist(artist)),
            LyricsText.normalizeMusicText(LyricsText.primaryArtist(artistName.orEmpty())),
            0.35,
            0.20
        )
        score += LyricsText.fieldScore(
            LyricsText.normalizeMusicText(album),
            LyricsText.normalizeMusicText(albumName.orEmpty()),
            0.10,
            0.04
        )

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
        lyricsfile = lyricsfile,
    )

    private fun Double.roundToSeconds(): Int = round(this).toInt()
    private fun Long.roundToSeconds(): Int = round(this.toDouble()).toInt()

    private fun Int.throwIfTemporaryOrUnexpected() {
        if (this == 429 || this >= 500) {
            throw java.io.IOException("LRCLIB temporary server error (HTTP $this)")
        }
        throw java.io.IOException("LRCLIB request failed (HTTP $this)")
    }

    private companion object {
        const val MIN_SCORE = 0.72
        const val MIN_SCORE_GAP = 0.08
        const val MAX_AUTOMATIC_RETRY_SECONDS = 120L
    }
}
