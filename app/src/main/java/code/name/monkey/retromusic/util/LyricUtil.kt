/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package code.name.monkey.retromusic.util

import android.util.Log
import code.name.monkey.retromusic.App
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.model.lyrics.AbsSynchronizedLyrics
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.*

/**
 * Created by hefuyi on 2016/11/8.
 */
object LyricUtil {
    private val lrcRootPath =
        getExternalStorageDirectory().toString() + "/RetroMusic/lyrics/"
    private const val TAG = "LyricUtil"
    fun writeLrcToLoc(
        title: String, artist: String, lrcContext: String
    ): File? {
        var writer: FileWriter? = null
        return try {
            val file = File(getLrcPath(title, artist))
            if (file.parentFile?.exists() != true) {
                file.parentFile?.mkdirs()
            }
            writer = FileWriter(getLrcPath(title, artist))
            writer.write(lrcContext)
            file
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } finally {
            try {
                writer?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    //So in Retro, Lrc file can be same folder as Music File or in RetroMusic Folder
    // In this case we pass location of the file and Contents to write to file
    /**
     * Store app-fetched/edited LRC in app-specific external storage. Direct File writes
     * to shared music folders are blocked by scoped storage on Android 11+.
     */
    fun writeLrc(song: Song, lrcContext: String) {
        try {
            val file = getManagedLrcFile(song)
            file.parentFile?.mkdirs()
            file.writeText(lrcContext, Charsets.UTF_8)
        } catch (e: IOException) {
            Log.e(TAG, "Unable to save synchronized lyrics", e)
        }
    }

    fun isManagedLyricsFile(file: File): Boolean {
        val managedDirectory = getManagedLrcDirectory().canonicalFile
        return file.canonicalFile.parentFile == managedDirectory
    }

    private fun getManagedLrcDirectory(): File {
        val context = App.getContext()
        return context.getExternalFilesDir("lyrics") ?: File(context.filesDir, "lyrics")
    }

    private fun getManagedLrcFile(song: Song): File =
        File(getManagedLrcDirectory(), LyricsFileKey.create(song.data))

    private fun getLegacyManagedLrcFile(title: String, artist: String): File =
        File(getManagedLrcDirectory(), "$title - $artist.lrc")

    fun deleteLrcFile(title: String, artist: String): Boolean {
        val managedDeleted = getLegacyManagedLrcFile(title, artist).delete()
        val legacyDeleted = File(getLrcPath(title, artist)).delete()
        return managedDeleted || legacyDeleted
    }

    /** Delete only this audio file's managed LRC, avoiding removal of duplicate-track lyrics. */
    fun deleteLrcFile(song: Song): Boolean {
        val managedDeleted = getManagedLrcFile(song).delete()
        val sidecarDeleted = getLocalLyricOriginalFile(song.data)?.delete() == true
        return managedDeleted || sidecarDeleted
    }

    private fun isLrcFileExist(title: String, artist: String): Boolean {
        val file = File(getLrcPath(title, artist))
        return file.exists()
    }

    private fun isLrcOriginalFileExist(path: String): Boolean {
        val file = File(getLrcOriginalPath(path))
        return file.exists()
    }

    private fun getLocalLyricFile(title: String, artist: String): File? {
        val file = File(getLrcPath(title, artist))
        return if (file.exists()) {
            file
        } else {
            null
        }
    }

    private fun getLocalLyricOriginalFile(path: String): File? {
        val file = File(getLrcOriginalPath(path))
        return if (file.exists()) {
            file
        } else {
            null
        }
    }

    private fun getLrcPath(title: String, artist: String): String {
        return "$lrcRootPath$title - $artist.lrc"
    }

    private fun getLrcOriginalPath(filePath: String): String {
        return LrcSidecarPath.fromAudioPath(filePath)
    }

    @Throws(Exception::class)
    fun getStringFromFile(title: String, artist: String): String {
        val file = getLegacyManagedLrcFile(title, artist).takeIf { it.exists() }
            ?: File(getLrcPath(title, artist))
        return file.inputStream().use { convertStreamToString(it) }
    }

    @Throws(Exception::class)
    private fun convertStreamToString(inputStream: InputStream): String {
        return inputStream.bufferedReader().readLines().joinToString(separator = "\n")
    }

    fun getStringFromLrc(file: File?): String {
        try {
            val reader = BufferedReader(FileReader(file))
            return reader.readLines().joinToString(separator = "\n")
        } catch (e: Exception) {
            Log.i("Error", "Error Occurred")
        }
        return ""
    }

    fun getSyncedLyricsFile(song: Song): File? {
        val managedFile = getManagedLrcFile(song)
        val legacyManagedFile = getLegacyManagedLrcFile(song.title, song.artistName)
        return when {
            managedFile.exists() -> managedFile
            legacyManagedFile.exists() -> legacyManagedFile
            isLrcOriginalFileExist(song.data) -> getLocalLyricOriginalFile(song.data)
            isLrcFileExist(song.title, song.artistName) -> getLocalLyricFile(song.title, song.artistName)
            else -> null
        }
    }

    fun getEmbeddedSyncedLyrics(data: String): String? {
        val embeddedLyrics = try {
            AudioFileIO.read(File(data)).tagOrCreateDefault.getFirst(FieldKey.LYRICS)
        } catch (e: Exception) {
            return null
        }
        return if (AbsSynchronizedLyrics.isSynchronized(embeddedLyrics)) {
            embeddedLyrics
        } else {
            null
        }
    }
}