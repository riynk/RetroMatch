package code.name.monkey.retromusic.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackVideoIdentityTest {
    @Test
    fun trackKeyIgnoresCaseAndRepeatedWhitespace() {
        val first = TrackVideoIdentity.create("  Blue  Train ", "John Coltrane", "Blue Train", 60000)
        val second = TrackVideoIdentity.create("blue train", " JOHN   COLTRANE ", "blue train", 60000)
        assertEquals(first, second)
    }

    @Test
    fun trackKeyChangesWhenDurationChanges() {
        val first = TrackVideoIdentity.create("Blue Train", "John Coltrane", "Blue Train", 60000)
        val second = TrackVideoIdentity.create("Blue Train", "John Coltrane", "Blue Train", 61000)
        assertNotEquals(first, second)
    }

    @Test
    fun trackKeyRequiresTitleAndArtist() {
        assertNull(TrackVideoIdentity.create("", "Artist", "Album", 60000))
        assertNull(TrackVideoIdentity.create("Title", null, "Album", 60000))
    }

    @Test
    fun remoteVideoRequiresHttpsHost() {
        assertTrue(TrackVideoUriValidator.isSupported("https://example.com/watch/1", "REMOTE"))
        assertFalse(TrackVideoUriValidator.isSupported("http://example.com/watch/1", "REMOTE"))
        assertFalse(TrackVideoUriValidator.isSupported("file:///sdcard/video.mp4", "REMOTE"))
    }

    @Test
    fun localVideoRequiresContentUri() {
        assertTrue(TrackVideoUriValidator.isSupported("content://media/external/video/1", "LOCAL"))
        assertFalse(TrackVideoUriValidator.isSupported("/sdcard/video.mp4", "LOCAL"))
        assertFalse(TrackVideoUriValidator.isSupported("file:///sdcard/video.mp4", "LOCAL"))
    }
}
