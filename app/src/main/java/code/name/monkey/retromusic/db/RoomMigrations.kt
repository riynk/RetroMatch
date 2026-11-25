package code.name.monkey.retromusic.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE LyricsEntity")
        database.execSQL("DROP TABLE BlackListStoreEntity")
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS track_video (
                video_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                track_key TEXT NOT NULL,
                media_store_song_id INTEGER,
                video_uri TEXT NOT NULL,
                source_type TEXT NOT NULL,
                video_type TEXT NOT NULL,
                provider TEXT NOT NULL,
                is_official INTEGER NOT NULL,
                language_tag TEXT,
                sync_offset_ms INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_track_video_track_key ON track_video(track_key)"
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS track_identity (
                track_key TEXT NOT NULL,
                media_store_song_id INTEGER,
                metadata_fingerprint TEXT,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(track_key)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
            index_track_identity_media_store_song_id
            ON track_identity(media_store_song_id)
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_track_identity_metadata_fingerprint
            ON track_identity(metadata_fingerprint)
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
            index_track_video_track_key_video_uri
            ON track_video(track_key, video_uri)
            """.trimIndent()
        )
    }
}
