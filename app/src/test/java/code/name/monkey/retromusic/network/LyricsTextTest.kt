package code.name.monkey.retromusic.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTextTest {

    @Test
    fun normalizeMusicTextPreservesNonLatinScripts() {
        assertEquals(
            "beyonce deja vu 東京 夜",
            LyricsText.normalizeMusicText("Beyoncé — Déjà Vu 東京 夜")
        )
    }

    @Test
    fun normalizeMusicTextUsesLocaleIndependentCaseConversion() {
        assertEquals("istanbul", LyricsText.normalizeMusicText("ISTANBUL"))
    }

    @Test
    fun primaryArtistDropsFeaturedArtists() {
        assertEquals("Aimer", LyricsText.primaryArtist("Aimer feat. milet"))
    }

    @Test
    fun timestampDetectionAcceptsLongMinuteFields() {
        assertTrue(LyricsText.hasTimestamps("[1234:56.789] Long recording"))
        assertTrue(LyricsText.hasTimestamps("[01:02] Short recording"))
    }

    @Test
    fun timestampDetectionRejectsMalformedTimestamp() {
        assertFalse(LyricsText.hasTimestamps("[1:2] Not a timestamp"))
        assertFalse(LyricsText.hasTimestamps("[01:90] Invalid seconds"))
        assertFalse(LyricsText.hasTimestamps("No timestamps here"))
    }

    @Test
    fun fieldScorePrefersExactMetadataMatch() {
        assertEquals(
            0.55,
            LyricsText.fieldScore("midnight city", "midnight city", 0.55, 0.35),
            0.0001
        )
        assertTrue(
            LyricsText.fieldScore("midnight city", "midnight", 0.55, 0.35) <
                LyricsText.fieldScore("midnight city", "midnight city", 0.55, 0.35)
        )
    }

    @Test
    fun searchQueriesRetryWithoutAlbumAndAvoidDuplicates() {
        assertEquals(
            listOf("Aimer Ref:rain After Dark", "Aimer Ref:rain"),
            LyricsText.searchQueries("Aimer", "Ref:rain", "After Dark")
        )
        assertEquals(
            listOf("Aimer Ref:rain"),
            LyricsText.searchQueries("Aimer", "Ref:rain", "")
        )
    }

    @Test
    fun candidateMatchingRequiresBothTitleAndArtist() {
        assertTrue(
            LyricsText.isPlausibleCandidate(
                "Aimer feat. milet",
                "Ref:rain",
                "Aimer",
                "Ref rain"
            )
        )
        assertFalse(
            LyricsText.isPlausibleCandidate(
                "Aimer",
                "Ref:rain",
                "YOASOBI",
                "Ref:rain"
            )
        )
        assertFalse(
            LyricsText.isPlausibleCandidate(
                "Aimer",
                "Ref:rain",
                "Aimer",
                "Brave Shine"
            )
        )
    }

    @Test
    fun candidateMatchingNormalizesAccentsAndCase() {
        assertTrue(
            LyricsText.isPlausibleCandidate(
                "Beyoncé",
                "Déjà Vu",
                "Beyonce",
                "Deja Vu"
            )
        )
    }
}
