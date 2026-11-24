/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.fragments.lyrics

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.widget.Toast
import android.view.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.transition.Fade
import code.name.monkey.appthemehelper.common.ATHToolbarActivity
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.activities.tageditor.TagWriter
import code.name.monkey.retromusic.databinding.FragmentLyricsBinding
import code.name.monkey.retromusic.extensions.accentColor
import code.name.monkey.retromusic.extensions.materialDialog
import code.name.monkey.retromusic.extensions.openUrl
import code.name.monkey.retromusic.extensions.uri
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.helper.MusicProgressViewUpdateHelper
import code.name.monkey.retromusic.lyrics.LrcView
import code.name.monkey.retromusic.util.TtmlLyricsStore
import code.name.monkey.retromusic.model.AudioTagInfo
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.network.FetchedLyrics
import code.name.monkey.retromusic.network.LyricsProvider
import code.name.monkey.retromusic.util.FileUtils
import code.name.monkey.retromusic.util.LyricUtil
import code.name.monkey.retromusic.util.UriUtil
import com.afollestad.materialdialogs.input.input
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.*
import org.koin.android.ext.android.inject
import kotlin.collections.set

class LyricsFragment : AbsMainActivityFragment(R.layout.fragment_lyrics),
    MusicProgressViewUpdateHelper.Callback {

    private var _binding: FragmentLyricsBinding? = null
    private val binding get() = _binding!!
    private lateinit var song: Song

    private lateinit var normalLyricsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var editSyncedLyricsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var importTtmlLauncher: ActivityResultLauncher<Array<String>>
    private var pendingTtmlImportSong: Song? = null

    private var pendingNormalLyricsCacheFile: File? = null
    private var pendingNormalLyricsUri: Uri? = null
    private var pendingSyncedLyrics: String? = null
    private var pendingSyncedFileUri: Uri? = null

    private var lyricsType: LyricsType = LyricsType.NORMAL_LYRICS

    private val googleSearchLrcUrl: String
        get() {
            var baseUrl = "http://www.google.com/search?"
            var query = song.title + "+" + song.artistName
            query = "q=" + query.replace(" ", "+") + " lyrics"
            baseUrl += query
            return baseUrl
        }

    private lateinit var updateHelper: MusicProgressViewUpdateHelper
    private val lyricsProvider: LyricsProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Normal lyrics launcher
        normalLyricsLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                val targetUri = pendingNormalLyricsUri
                val sourceFile = pendingNormalLyricsCacheFile
                if (it.resultCode == Activity.RESULT_OK && targetUri != null && sourceFile != null) {
                    FileUtils.copyFileToUri(requireContext(), sourceFile, targetUri)
                    if (_binding != null) {
                        updateTitleSong()
                        loadLyrics()
                    }
                }
                sourceFile?.delete()
                pendingNormalLyricsUri = null
                pendingNormalLyricsCacheFile = null
            }
        importTtmlLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val targetSong = pendingTtmlImportSong
            pendingTtmlImportSong = null
            if (uri == null || targetSong == null || !isAdded) return@registerForActivityResult

            val contentResolver = requireContext().contentResolver
            lifecycleScope.launch {
                val imported = try {
                    withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                            val content = StringBuilder()
                            val buffer = CharArray(8192)
                            var totalChars = 0
                            while (true) {
                                val count = reader.read(buffer)
                                if (count < 0) break
                                totalChars += count
                                if (totalChars > 2 * 1024 * 1024) return@use null
                                content.append(buffer, 0, count)
                            }
                            content.toString()
                        }
                    }
                } catch (_: Exception) {
                    null
                }

                val saved = withContext(Dispatchers.IO) {
                    imported?.let { TtmlLyricsStore.appStore().save(targetSong.data, it) } == true
                }
                if (!isAdded) return@launch
                if (saved) {
                    if (_binding != null && MusicPlayerRemote.currentSong.id == targetSong.id) loadLyrics()
                    Toast.makeText(requireContext(), R.string.ttml_import_success, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), R.string.ttml_import_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
        editSyncedLyricsLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                val targetUri = pendingSyncedFileUri
                val lyricsToWrite = pendingSyncedLyrics
                if (it.resultCode == Activity.RESULT_OK && targetUri != null && lyricsToWrite != null) {
                    val outputStream = requireContext().contentResolver.openOutputStream(targetUri, "wt")
                    if (outputStream != null) {
                        outputStream.use { output ->
                            output.write(lyricsToWrite.toByteArray(Charsets.UTF_8))
                            output.flush()
                        }
                        if (_binding != null) {
                            updateTitleSong()
                            loadLyrics()
                        }
                    } else {
                        Toast.makeText(requireContext(), R.string.could_not_write_tags_to_file, Toast.LENGTH_LONG).show()
                    }
                }
                pendingSyncedFileUri = null
                pendingSyncedLyrics = null
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        enterTransition = Fade()
        exitTransition = Fade()
        _binding = FragmentLyricsBinding.bind(view)
        updateHelper = MusicProgressViewUpdateHelper(this, 500, 1000)
        updateTitleSong()
        setupLyricsView()
        loadLyrics()

        setupWakelock()
        setupViews()
        setupToolbar()
    }

    private fun setupLyricsView() {
        binding.ttmlLyricsView.setKaraokeHighlightColor(accentColor())
        binding.lyricsView.apply {
            setCurrentColor(accentColor())
            setTimeTextColor(accentColor())
            setTimelineColor(accentColor())
            setTimelineTextColor(accentColor())
            setDraggable(true, LrcView.OnPlayClickListener {
                MusicPlayerRemote.seekTo(it.toInt())
                return@OnPlayClickListener true
            })
        }
    }

    override fun onUpdateProgressViews(progress: Int, total: Int) {
        binding.lyricsView.updateTime(progress.toLong())
        if (binding.ttmlLyricsView.isVisible) binding.ttmlLyricsView.updateTime(progress.toLong())
    }

    private fun setupViews() {
        binding.editButton.accentColor()
        binding.editButton.setOnClickListener {
            when (lyricsType) {
                LyricsType.SYNCED_LYRICS -> {
                    editSyncedLyrics()
                }
                LyricsType.NORMAL_LYRICS -> {
                    editNormalLyrics()
                }
            }
        }
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateTitleSong()
        loadLyrics()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateTitleSong()
        loadLyrics()
    }

    private fun updateTitleSong() {
        song = MusicPlayerRemote.currentSong
    }

    private fun setupToolbar() {
        mainActivity.setSupportActionBar(binding.toolbar)
        ToolbarContentTintHelper.colorBackButton(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupWakelock() {
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_lyrics, menu)
        ToolbarContentTintHelper.handleOnCreateOptionsMenu(
            requireContext(),
            binding.toolbar,
            menu,
            ATHToolbarActivity.getToolbarBackgroundColor(binding.toolbar)
        )
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> openUrl(googleSearchLrcUrl)
            R.id.action_fetch_lyrics -> fetchLyricsFromLrcLib()
            R.id.action_import_ttml -> {
                pendingTtmlImportSong = song
                importTtmlLauncher.launch(arrayOf("*/*"))
            }
        }
        return false
    }

    @SuppressLint("CheckResult")
    private fun editNormalLyrics(lyrics: String? = null) {
        val file = File(song.data)
        val content = lyrics ?: try {
            AudioFileIO.read(file).tagOrCreateDefault.getFirst(FieldKey.LYRICS)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }

        val song = song

        materialDialog().show {
            title(res = R.string.edit_normal_lyrics)
            input(
                hintRes = R.string.paste_lyrics_here,
                prefill = content,
                inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
            ) { _, input ->
                val fieldKeyValueMap = EnumMap<FieldKey, String>(FieldKey::class.java)
                fieldKeyValueMap[FieldKey.LYRICS] = input.toString()
                viewLifecycleOwner.lifecycleScope.launch {
                    if (VersionUtils.hasR()) {
                        pendingNormalLyricsCacheFile = TagWriter.writeTagsToFilesR(
                            requireContext(), AudioTagInfo(
                                listOf(song.data), fieldKeyValueMap, null
                            )
                        ).firstOrNull()
                        if (pendingNormalLyricsCacheFile == null) {
                            Toast.makeText(requireContext(), R.string.could_not_write_tags_to_file, Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        pendingNormalLyricsUri = song.uri
                        val pendingIntent =
                            MediaStore.createWriteRequest(
                                requireContext().contentResolver,
                                listOf(song.uri)
                            )

                        normalLyricsLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent).build()
                        )
                    } else {
                        TagWriter.writeTagsToFiles(
                            requireContext(), AudioTagInfo(
                                listOf(song.data), fieldKeyValueMap, null
                            )
                        )
                        if (_binding != null) {
                            updateTitleSong()
                            loadLyrics()
                        }
                    }
                }
            }
            positiveButton(res = R.string.save) {
                loadNormalLyrics()
            }
            negativeButton(res = android.R.string.cancel)
        }
    }


    @SuppressLint("CheckResult")
    private fun editSyncedLyrics(lyrics: String? = null) {
        val content = lyrics ?: LyricUtil.getStringFromLrc(LyricUtil.getSyncedLyricsFile(song))

        val song = song
        materialDialog().show {
            title(res = R.string.edit_synced_lyrics)
            input(
                hintRes = R.string.paste_timeframe_lyrics_here,
                prefill = content,
                inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
            ) { _, input ->
                if (VersionUtils.hasR()) {
                    val lyricsToSave = input.toString()
                    val lrcFile = LyricUtil.getSyncedLyricsFile(song)
                    if (lrcFile?.exists() == true && LyricUtil.isManagedLyricsFile(lrcFile)) {
                        // App-specific files are writable without a MediaStore consent request.
                        LyricUtil.writeLrc(song, lyricsToSave)
                        if (_binding != null) {
                            updateTitleSong()
                            loadLyrics()
                        }
                    } else if (lrcFile?.exists() == true) {
                        pendingSyncedLyrics = lyricsToSave
                        val targetUri = UriUtil.getUriFromPath(requireContext(), lrcFile.absolutePath)
                        pendingSyncedFileUri = targetUri
                        val pendingIntent =
                            MediaStore.createWriteRequest(
                                requireContext().contentResolver,
                                listOf(targetUri)
                            )
                        editSyncedLyricsLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent).build()
                        )
                    } else {
                        val fieldKeyValueMap = EnumMap<FieldKey, String>(FieldKey::class.java)
                        fieldKeyValueMap[FieldKey.LYRICS] = lyricsToSave
                        viewLifecycleOwner.lifecycleScope.launch {
                            pendingNormalLyricsCacheFile = TagWriter.writeTagsToFilesR(
                                requireContext(),
                                AudioTagInfo(listOf(song.data), fieldKeyValueMap, null)
                            ).firstOrNull()
                            if (pendingNormalLyricsCacheFile == null) {
                                Toast.makeText(requireContext(), R.string.could_not_write_tags_to_file, Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            pendingNormalLyricsUri = song.uri
                            val pendingIntent = MediaStore.createWriteRequest(
                                requireContext().contentResolver,
                                listOf(song.uri)
                            )

                            normalLyricsLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent).build()
                            )
                        }
                    }
                } else {
                    LyricUtil.writeLrc(song, input.toString())
                    if (_binding != null) {
                        updateTitleSong()
                        loadLyrics()
                    }
                }
            }
            positiveButton(res = R.string.save) {
                loadLRCLyrics()
            }
            negativeButton(res = android.R.string.cancel)
        }
    }


    private fun fetchLyricsFromLrcLib() {
        val requestedSong = song
        Toast.makeText(requireContext(), R.string.lyrics_searching, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val fetched = lyricsProvider.fetchLyrics(
                    artist = requestedSong.artistName,
                    title = requestedSong.title,
                    album = requestedSong.albumName,
                    durationMillis = requestedSong.duration
                )
                if (!isAdded) return@launch
                if (MusicPlayerRemote.currentSong.id != requestedSong.id) {
                    Toast.makeText(requireContext(), R.string.lyrics_song_changed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (fetched == null || !fetched.hasLyrics) {
                    Toast.makeText(requireContext(), R.string.lyrics_not_found, Toast.LENGTH_LONG).show()
                    return@launch
                }
                showFetchedLyricsPreview(requestedSong, fetched)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isAdded) {
                    Toast.makeText(requireContext(), R.string.lyrics_search_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showFetchedLyricsPreview(requestedSong: Song, fetched: FetchedLyrics) {
        val isSynced = code.name.monkey.retromusic.network.LyricsText.hasTimestamps(fetched.preferredLyrics)
        val details = buildString {
            append(fetched.artist)
            if (fetched.album.isNotBlank()) append(" • ").append(fetched.album)
            append("\n")
            append(if (isSynced) getString(R.string.synced_lyrics) else getString(R.string.normal_lyrics))
            append("\n\n")
            append(fetched.preferredLyrics)
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.lyrics_preview_title, fetched.title))
            .setMessage(details)
            .setPositiveButton(R.string.save) { _, _ ->
                if (isSynced) {
                    if (MusicPlayerRemote.currentSong.id != requestedSong.id) {
                        Toast.makeText(requireContext(), R.string.lyrics_song_changed, Toast.LENGTH_SHORT).show()
                    } else {
                        LyricUtil.writeLrc(requestedSong, fetched.preferredLyrics)
                        loadLyrics()
                    }
                } else {
                    if (MusicPlayerRemote.currentSong.id == requestedSong.id) {
                        editNormalLyrics(fetched.preferredLyrics)
                    } else {
                        Toast.makeText(requireContext(), R.string.lyrics_song_changed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


    private fun loadNormalLyrics() {
        val file = File(song.data)
        val lyrics = try {
            AudioFileIO.read(file).tagOrCreateDefault.getFirst(FieldKey.LYRICS)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
        binding.normalLyrics.isVisible = !lyrics.isNullOrEmpty()
        binding.noLyricsFound.isVisible = lyrics.isNullOrEmpty()
        binding.normalLyrics.text = lyrics
    }

    /**
     * @return success
     */
    private fun loadLRCLyrics(): Boolean {
        val lrcFile = LyricUtil.getSyncedLyricsFile(song)
        if (lrcFile != null) {
            binding.lyricsView.loadLrc(lrcFile)
        } else {
            val embeddedLyrics = LyricUtil.getEmbeddedSyncedLyrics(song.data)
            if (embeddedLyrics != null) {
                binding.lyricsView.loadLrc(embeddedLyrics)
            } else {
                binding.lyricsView.setLabel(getString(R.string.empty))
                return false
            }
        }
        return true
    }

    private fun loadLyrics() {
        val ttml = TtmlLyricsStore.appStore().read(song.data)
        if (ttml != null) {
            binding.ttmlLyricsView.setLyrics(ttml)
            binding.ttmlLyricsView.isVisible = true
            binding.lyricsView.isVisible = false
            binding.normalLyrics.isVisible = false
            binding.noLyricsFound.isVisible = false
            binding.editButton.isVisible = false
            lyricsType = LyricsType.SYNCED_LYRICS
            return
        }

        binding.ttmlLyricsView.setLyrics(null)
        binding.ttmlLyricsView.isVisible = false
        binding.editButton.isVisible = true
        lyricsType = if (!loadLRCLyrics()) {
            binding.lyricsView.isVisible = false
            loadNormalLyrics()
            LyricsType.NORMAL_LYRICS
        } else {
            binding.normalLyrics.isVisible = false
            binding.noLyricsFound.isVisible = false
            binding.lyricsView.isVisible = true
            LyricsType.SYNCED_LYRICS
        }
    }

    override fun onResume() {
        super.onResume()
        updateHelper.start()
    }

    override fun onPause() {
        super.onPause()
        updateHelper.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (MusicPlayerRemote.playingQueue.isNotEmpty())
            mainActivity.expandPanel()
        _binding = null
    }

    enum class LyricsType {
        NORMAL_LYRICS,
        SYNCED_LYRICS
    }
}
