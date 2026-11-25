package code.name.monkey.retromusic.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A video associated with a canonical track identity.
 *
 * [videoUri] is an HTTPS URL for remote sources or a persisted content:// URI
 * for local videos. Local file paths must not be stored here.
 */
@Entity(
    tableName = "track_video",
    indices = [
        Index(value = ["track_key"], name = "index_track_video_track_key"),
        Index(
            value = ["track_key", "video_uri"],
            unique = true,
            name = "index_track_video_track_key_video_uri"
        )
    ]
)
data class TrackVideoEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "video_id")
    val videoId: Long = 0L,
    @ColumnInfo(name = "track_key")
    val trackKey: String,
    @ColumnInfo(name = "media_store_song_id")
    val mediaStoreSongId: Long? = null,
    @ColumnInfo(name = "video_uri")
    val videoUri: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "video_type")
    val videoType: String,
    val provider: String,
    @ColumnInfo(name = "is_official")
    val isOfficial: Boolean = false,
    @ColumnInfo(name = "language_tag")
    val languageTag: String? = null,
    @ColumnInfo(name = "sync_offset_ms")
    val syncOffsetMs: Long = 0L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
