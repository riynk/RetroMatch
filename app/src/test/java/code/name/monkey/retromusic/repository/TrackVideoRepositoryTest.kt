package code.name.monkey.retromusic.repository

import code.name.monkey.retromusic.db.TrackIdentityDao
import code.name.monkey.retromusic.db.TrackIdentityEntity
import code.name.monkey.retromusic.db.TrackVideoDao
import code.name.monkey.retromusic.db.TrackVideoEntity
import code.name.monkey.retromusic.db.TrackVideoIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackVideoRepositoryTest {

    @Test
    fun metadataEditOnSameMediaStoreRowKeepsTheStableTrackKey() = runBlocking {
        val identities = InMemoryTrackIdentityDao()
        val repository = repository(identities)

        val original = repository.resolveTrackKey(
            mediaStoreSongId = 41L,
            title = "Original title",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L
        )
        val edited = repository.resolveTrackKey(
            mediaStoreSongId = 41L,
            title = "Corrected title",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L
        )

        assertNotNull(original)
        assertEquals(original, edited)
        assertEquals(
            TrackVideoIdentity.create("Corrected title", "Artist", "Album", 180_000L),
            identities.byTrackKey(original!!)?.metadataFingerprint
        )
    }

    @Test
    fun duplicateMediaStoreRowsWithIdenticalMetadataAreNotMerged() = runBlocking {
        val identities = InMemoryTrackIdentityDao()
        val repository = repository(identities)

        val first = repository.resolveTrackKey(
            41L, "Same title", "Same artist", "Same album", 180_000L
        )
        val duplicate = repository.resolveTrackKey(
            42L, "Same title", "Same artist", "Same album", 180_000L
        )

        assertNotNull(first)
        assertNotNull(duplicate)
        assertNotEquals(first, duplicate)
        assertEquals(2, identities.all().size)
    }

    @Test
    fun unclaimedMetadataIdentityCanBeBoundToItsFirstMediaStoreRow() = runBlocking {
        val identities = InMemoryTrackIdentityDao()
        val repository = repository(identities)

        val withoutMediaStoreId = repository.resolveTrackKey(
            null, "Title", "Artist", "Album", 180_000L
        )
        val withMediaStoreId = repository.resolveTrackKey(
            41L, "Title", "Artist", "Album", 180_000L
        )

        assertEquals(withoutMediaStoreId, withMediaStoreId)
        assertEquals(41L, identities.byTrackKey(withMediaStoreId!!)?.mediaStoreSongId)
    }

    @Test
    fun explicitRelinkMovesTheMediaStoreAliasWithoutChangingTrackKey() = runBlocking {
        val identities = InMemoryTrackIdentityDao()
        val repository = repository(identities)
        val key = repository.resolveTrackKey(
            41L, "Title", "Artist", "Album", 180_000L
        )!!

        val linked = repository.confirmMediaStoreRelink(
            trackKey = key,
            newMediaStoreSongId = 99L,
            title = "Updated title",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L
        )
        val resolved = repository.resolveTrackKey(
            99L, "Updated title", "Artist", "Album", 180_000L
        )

        assertTrue(linked)
        assertEquals(key, resolved)
    }

    @Test
    fun relinkCannotStealAMediaStoreRowOwnedByAnotherTrack() = runBlocking {
        val identities = InMemoryTrackIdentityDao()
        val repository = repository(identities)
        val first = repository.resolveTrackKey(
            41L, "Title", "Artist", "Album", 180_000L
        )!!
        repository.resolveTrackKey(42L, "Title", "Artist", "Album", 180_000L)

        assertFalse(
            repository.confirmMediaStoreRelink(
                first, 42L, "Title", "Artist", "Album", 180_000L
            )
        )
    }

    @Test
    fun missingMediaStoreIdAndInsufficientMetadataDoNotCreateAnIdentity() = runBlocking {
        val repository = repository(InMemoryTrackIdentityDao())

        assertNull(repository.resolveTrackKey(null, null, "Artist", null, 0L))
    }

    private fun repository(identities: InMemoryTrackIdentityDao) =
        RealTrackVideoRepository(NoopTrackVideoDao(), identities)

    private class InMemoryTrackIdentityDao : TrackIdentityDao {
        private val identities = linkedMapOf<String, TrackIdentityEntity>()

        fun all(): List<TrackIdentityEntity> = identities.values.toList()

        override suspend fun byMediaStoreSongId(songId: Long): TrackIdentityEntity? =
            identities.values.singleOrNull { it.mediaStoreSongId == songId }

        override suspend fun byMetadataFingerprint(fingerprint: String): List<TrackIdentityEntity> =
            identities.values.filter { it.metadataFingerprint == fingerprint }

        override suspend fun byTrackKey(trackKey: String): TrackIdentityEntity? =
            identities[trackKey]

        override suspend fun insert(identity: TrackIdentityEntity) {
            check(identity.trackKey !in identities)
            check(identity.mediaStoreSongId == null ||
                identities.values.none { it.mediaStoreSongId == identity.mediaStoreSongId })
            identities[identity.trackKey] = identity
        }

        override suspend fun update(identity: TrackIdentityEntity) {
            check(identity.mediaStoreSongId == null ||
                identities.values.none {
                    it.trackKey != identity.trackKey && it.mediaStoreSongId == identity.mediaStoreSongId
                })
            identities[identity.trackKey] = identity
        }
    }

    private class NoopTrackVideoDao : TrackVideoDao {
        override fun observeVideos(trackKey: String): Flow<List<TrackVideoEntity>> =
            flowOf(emptyList())

        override suspend fun videos(trackKey: String): List<TrackVideoEntity> = emptyList()

        override suspend fun video(videoId: Long): TrackVideoEntity? = null

        override suspend fun insert(video: TrackVideoEntity): Long = 1L

        override suspend fun update(video: TrackVideoEntity) = Unit

        override suspend fun delete(video: TrackVideoEntity) = Unit

        override suspend fun deleteById(videoId: Long) = Unit

        override suspend fun deleteForTrack(trackKey: String) = Unit
    }
}
