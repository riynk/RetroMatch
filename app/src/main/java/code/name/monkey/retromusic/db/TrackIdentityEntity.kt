package code.name.monkey.retromusic.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stable app-owned identity for a recording.
 *
 * MediaStore IDs and normalized metadata are aliases used to find this row; neither
 * is the canonical identity. Keeping the UUID stable prevents tag edits from
 * orphaning a track's video associations.
 */
@Entity(
    tableName = "track_identity",
    indices = [
        Index(
            value = ["media_store_song_id"],
            unique = true,
            name = "index_track_identity_media_store_song_id"
        ),
        Index(
            value = ["metadata_fingerprint"],
            name = "index_track_identity_metadata_fingerprint"
        )
    ]
)
data class TrackIdentityEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_key")
    val trackKey: String,
    @ColumnInfo(name = "media_store_song_id")
    val mediaStoreSongId: Long? = null,
    @ColumnInfo(name = "metadata_fingerprint")
    val metadataFingerprint: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
