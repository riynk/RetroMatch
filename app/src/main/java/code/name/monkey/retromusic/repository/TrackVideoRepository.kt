package code.name.monkey.retromusic.repository

import code.name.monkey.retromusic.db.TrackIdentityDao
import code.name.monkey.retromusic.db.TrackIdentityEntity
import code.name.monkey.retromusic.db.TrackVideoDao
import code.name.monkey.retromusic.db.TrackVideoEntity
import code.name.monkey.retromusic.db.TrackVideoIdentity
import code.name.monkey.retromusic.db.TrackVideoUriValidator
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TrackVideoRepository {
    fun observeVideos(trackKey: String): Flow<List<TrackVideoEntity>>
    suspend fun videos(trackKey: String): List<TrackVideoEntity>
    suspend fun add(video: TrackVideoEntity): Long
    suspend fun update(video: TrackVideoEntity)
    suspend fun delete(video: TrackVideoEntity)

    /**
     * Returns a stable app-owned key. MediaStore ID and metadata fingerprint are
     * aliases only, so metadata edits do not replace the key.
     */
    suspend fun resolveTrackKey(
        mediaStoreSongId: Long?,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long
    ): String?

    /** Explicitly links a newly discovered MediaStore row to an existing recording. */
    suspend fun confirmMediaStoreRelink(
        trackKey: String,
        newMediaStoreSongId: Long,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long
    ): Boolean
}

class RealTrackVideoRepository(
    private val trackVideoDao: TrackVideoDao,
    private val trackIdentityDao: TrackIdentityDao
) : TrackVideoRepository {

    private val identityMutex = Mutex()

    override fun observeVideos(trackKey: String): Flow<List<TrackVideoEntity>> =
        trackVideoDao.observeVideos(requireTrackKey(trackKey))

    override suspend fun videos(trackKey: String): List<TrackVideoEntity> =
        trackVideoDao.videos(requireTrackKey(trackKey))

    override suspend fun add(video: TrackVideoEntity): Long {
        validate(video)
        return trackVideoDao.insert(video)
    }

    override suspend fun update(video: TrackVideoEntity) {
        validate(video)
        trackVideoDao.update(video)
    }

    override suspend fun delete(video: TrackVideoEntity) {
        trackVideoDao.delete(video)
    }

    override suspend fun resolveTrackKey(
        mediaStoreSongId: Long?,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long
    ): String? = identityMutex.withLock {
        val songId = mediaStoreSongId?.takeIf { it > 0L }
        val fingerprint = TrackVideoIdentity.fingerprint(title, artist, album, durationMs)

        // The same MediaStore row survives ordinary tag edits. Keep its UUID and
        // refresh the metadata alias rather than creating a new track identity.
        if (songId != null) {
            trackIdentityDao.byMediaStoreSongId(songId)?.let { existing ->
                trackIdentityDao.update(
                    existing.copy(
                        metadataFingerprint = fingerprint ?: existing.metadataFingerprint,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return@withLock existing.trackKey
            }
        }

        // A fingerprint can safely claim an identity only when it has not yet
        // been bound to a different MediaStore row. Exact metadata collisions
        // are possible, so never auto-merge two already-indexed song rows.
        if (fingerprint != null) {
            val unclaimed = trackIdentityDao.byMetadataFingerprint(fingerprint)
                .singleOrNull()
                ?.takeIf { it.mediaStoreSongId == null }
            if (unclaimed != null) {
                trackIdentityDao.update(
                    unclaimed.copy(
                        mediaStoreSongId = songId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return@withLock unclaimed.trackKey
            }
        }

        if (songId == null && fingerprint == null) return@withLock null

        val newKey = UUID.randomUUID().toString()
        trackIdentityDao.insert(
            TrackIdentityEntity(
                trackKey = newKey,
                mediaStoreSongId = songId,
                metadataFingerprint = fingerprint
            )
        )
        newKey
    }

    override suspend fun confirmMediaStoreRelink(
        trackKey: String,
        newMediaStoreSongId: Long,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long
    ): Boolean = identityMutex.withLock {
        requireTrackKey(trackKey)
        if (newMediaStoreSongId <= 0L) return@withLock false

        val identity = trackIdentityDao.byTrackKey(trackKey) ?: return@withLock false
        val occupied = trackIdentityDao.byMediaStoreSongId(newMediaStoreSongId)
        if (occupied != null && occupied.trackKey != trackKey) return@withLock false

        val fingerprint = TrackVideoIdentity.create(title, artist, album, durationMs)
        trackIdentityDao.update(
            identity.copy(
                mediaStoreSongId = newMediaStoreSongId,
                metadataFingerprint = fingerprint ?: identity.metadataFingerprint,
                updatedAt = System.currentTimeMillis()
            )
        )
        true
    }

    private fun validate(video: TrackVideoEntity) {
        requireTrackKey(video.trackKey)
        require(TrackVideoUriValidator.isSupported(video.videoUri, video.sourceType)) {
            "Unsupported video URI for source type ${video.sourceType}"
        }
    }

    private fun requireTrackKey(trackKey: String): String {
        require(TRACK_KEY_PATTERN.matches(trackKey)) {
            "trackKey must be a UUID allocated by the track identity repository"
        }
        return trackKey
    }

    private companion object {
        val TRACK_KEY_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
