package code.name.monkey.retromusic.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TrackIdentityDao {
    @Query("SELECT * FROM track_identity WHERE media_store_song_id = :songId LIMIT 1")
    suspend fun byMediaStoreSongId(songId: Long): TrackIdentityEntity?

    @Query("SELECT * FROM track_identity WHERE metadata_fingerprint = :fingerprint")
    suspend fun byMetadataFingerprint(fingerprint: String): List<TrackIdentityEntity>

    @Query("SELECT * FROM track_identity WHERE track_key = :trackKey LIMIT 1")
    suspend fun byTrackKey(trackKey: String): TrackIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(identity: TrackIdentityEntity)

    @Update
    suspend fun update(identity: TrackIdentityEntity)
}
