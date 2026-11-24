package code.name.monkey.retromusic.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackVideoDao {
    @Query(
        """
        SELECT * FROM track_video
        WHERE track_key = :trackKey
        ORDER BY is_official DESC, created_at ASC
        """
    )
    fun observeVideos(trackKey: String): Flow<List<TrackVideoEntity>>

    @Query("SELECT * FROM track_video WHERE track_key = :trackKey ORDER BY is_official DESC, created_at ASC")
    suspend fun videos(trackKey: String): List<TrackVideoEntity>

    @Query("SELECT * FROM track_video WHERE video_id = :videoId LIMIT 1")
    suspend fun video(videoId: Long): TrackVideoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(video: TrackVideoEntity): Long

    @Update
    suspend fun update(video: TrackVideoEntity)

    @Delete
    suspend fun delete(video: TrackVideoEntity)

    @Query("DELETE FROM track_video WHERE video_id = :videoId")
    suspend fun deleteById(videoId: Long)

    @Query("DELETE FROM track_video WHERE track_key = :trackKey")
    suspend fun deleteForTrack(trackKey: String)
}
