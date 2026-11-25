package code.name.monkey.retromusic.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMigrationsTest {

    @Test
    fun migration24To25CreatesVideoAndStableIdentityTablesWithoutDroppingExistingData() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args?.isNotEmpty() == true) {
                statements += args[0] as String
            }
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                else -> null
            }
        } as SupportSQLiteDatabase

        MIGRATION_24_25.migrate(database)

        val sql = statements.joinToString("\n").lowercase()
        assertTrue(sql.contains("create table if not exists track_video"))
        assertTrue(sql.contains("create table if not exists track_identity"))
        assertTrue(sql.contains("primary key(track_key)"))
        assertTrue(sql.contains("index_track_video_track_key"))
        assertTrue(sql.contains("index_track_video_track_key_video_uri"))
        assertTrue(sql.contains("index_track_identity_media_store_song_id"))
        assertTrue(sql.contains("index_track_identity_metadata_fingerprint"))
        assertFalse(sql.contains("drop table"))
    }
}
