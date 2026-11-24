package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.data.model.PlayerState
import com.example.data.model.RepeatMode
import com.example.data.model.SongRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

object MusicPlayerManager {
    private const val TAG = "MusicPlayerManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var loadJob: Job? = null

    fun playSong(
        context: Context,
        song: SongRecord,
        queue: List<SongRecord> = listOf(song),
        onAudioSynthesized: ((SongRecord, File) -> Unit)? = null
    ) {
        val resolvedQueue = if (queue.any { it.id == song.id }) queue else listOf(song) + queue
        val idx = resolvedQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        loadJob?.cancel()
        stopProgressTicker()
        releasePlayer()

        _playerState.update {
            it.copy(
                currentSong = song,
                queue = resolvedQueue,
                currentIndex = idx,
                playing = false,
                preparing = true,
                synthesisProgress = 0.05f,
                positionMs = 0L,
                durationMs = song.durationSec * 1000L,
                statusMessage = "Preparing studio audio..."
            )
        }

        val appCtx = context.applicationContext
        loadJob = scope.launch {
            try {
                val existingFile = song.localAudioPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 100 }
                    ?: File(appCtx.filesDir, "audio/${song.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}.wav")
                        .takeIf { it.exists() && it.length() > 100 }

                val wavFile = existingFile ?: run {
                    _playerState.update { it.copy(statusMessage = "Synthesizing ${song.genre} audio...") }
                    AudioSynthEngine.synthesizeSongToWav(
                        context = appCtx,
                        songId = song.id.ifBlank { "track_${song.title.hashCode()}" },
                        title = song.title,
                        recipientName = song.recipientName,
                        lyrics = song.lyrics,
                        genre = song.genre,
                        mood = song.mood,
                        voiceType = song.voiceType,
                        bpm = song.bpm,
                        chordProgression = song.chordProgression,
                        durationSec = song.durationSec.coerceIn(20, 90),
                        onProgress = { prog ->
                            _playerState.update { it.copy(synthesisProgress = prog) }
                        }
                    ).also { generated ->
                        onAudioSynthesized?.invoke(song, generated)
                    }
                }

                val updatedSong = song.copy(localAudioPath = wavFile.absolutePath)
                val updatedQueue = resolvedQueue.map { if (it.id == updatedSong.id) updatedSong else it }

                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(wavFile.absolutePath)
                    setVolume(_playerState.value.volume, _playerState.value.volume)
                    setOnPreparedListener { prepared ->
                        prepared.start()
                        _playerState.update {
                            it.copy(
                                currentSong = updatedSong,
                                queue = updatedQueue,
                                preparing = false,
                                playing = true,
                                synthesisProgress = 1f,
                                durationMs = prepared.duration.toLong().coerceAtLeast(1000L),
                                statusMessage = null
                            )
                        }
                        MediaPlaybackService.updateForeground(appCtx, updatedSong.title, updatedSong.genre, true)
                        startProgressTicker(appCtx)
                    }
                    setOnCompletionListener {
                        handleCompletion(appCtx, onAudioSynthesized)
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                        _playerState.update {
                            it.copy(preparing = false, playing = false, statusMessage = "Playback error ($what)")
                        }
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = mp
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare audio", e)
                _playerState.update {
                    it.copy(preparing = false, playing = false, statusMessage = e.localizedMessage ?: "Audio preparation failed")
                }
            }
        }
    }

    fun togglePlayPause(context: Context) {
        val mp = mediaPlayer ?: run {
            val current = _playerState.value.currentSong ?: return
            playSong(context, current, _playerState.value.queue)
            return
        }
        try {
            if (mp.isPlaying) {
                mp.pause()
                _playerState.update { it.copy(playing = false) }
                _playerState.value.currentSong?.let {
                    MediaPlaybackService.updateForeground(context.applicationContext, it.title, it.genre, false)
                }
            } else {
                mp.start()
                _playerState.update { it.copy(playing = true) }
                _playerState.value.currentSong?.let {
                    MediaPlaybackService.updateForeground(context.applicationContext, it.title, it.genre, true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Toggle play/pause failed", e)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt().coerceAtLeast(0))
            _playerState.update { it.copy(positionMs = positionMs) }
        } catch (_: Exception) {
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _playerState.update { it.copy(volume = clamped) }
        try {
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (_: Exception) {
        }
    }

    fun toggleShuffle() {
        _playerState.update { it.copy(shuffle = !it.shuffle) }
    }

    fun cycleRepeatMode() {
        _playerState.update {
            val next = when (it.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            it.copy(repeatMode = next)
        }
    }

    fun playNext(context: Context) {
        val state = _playerState.value
        if (state.queue.isEmpty()) return
        val nextIndex = if (state.shuffle && state.queue.size > 1) {
            (state.queue.indices - state.currentIndex).random()
        } else {
            (state.currentIndex + 1) % state.queue.size
        }
        playSong(context, state.queue[nextIndex], state.queue)
    }

    fun playPrevious(context: Context) {
        val state = _playerState.value
        if (state.queue.isEmpty()) return
        if (state.positionMs > 4000L) {
            seekTo(0L)
            return
        }
        val prevIndex = if (state.currentIndex - 1 < 0) state.queue.lastIndex else state.currentIndex - 1
        playSong(context, state.queue[prevIndex], state.queue)
    }

    fun addToQueue(song: SongRecord) {
        _playerState.update { state ->
            if (state.queue.any { it.id == song.id }) state
            else state.copy(queue = state.queue + song)
        }
    }

    fun removeFromQueue(songId: String) {
        _playerState.update { state ->
            val filtered = state.queue.filterNot { it.id == songId }
            val newIndex = filtered.indexOfFirst { it.id == state.currentSong?.id }
            state.copy(queue = filtered, currentIndex = newIndex)
        }
    }

    fun stopAndClear(context: Context) {
        loadJob?.cancel()
        stopProgressTicker()
        releasePlayer()
        _playerState.value = PlayerState()
        MediaPlaybackService.stopService(context.applicationContext)
    }

    private fun handleCompletion(context: Context, onAudioSynthesized: ((SongRecord, File) -> Unit)?) {
        val state = _playerState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
            }
            RepeatMode.ALL -> {
                if (state.queue.isNotEmpty()) {
                    val nextIdx = (state.currentIndex + 1) % state.queue.size
                    playSong(context, state.queue[nextIdx], state.queue, onAudioSynthesized)
                }
            }
            RepeatMode.OFF -> {
                if (state.currentIndex + 1 < state.queue.size) {
                    playSong(context, state.queue[state.currentIndex + 1], state.queue, onAudioSynthesized)
                } else {
                    _playerState.update { it.copy(playing = false, positionMs = 0L) }
                }
            }
        }
    }

    private fun startProgressTicker(context: Context) {
        stopProgressTicker()
        progressJob = scope.launch {
            var tick = 0
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null) {
                    try {
                        val pos = mp.currentPosition.toLong()
                        val isPlaying = mp.isPlaying
                        val bpm = _playerState.value.currentSong?.bpm ?: 120
                        val phase = (pos / 1000.0) * (bpm / 60.0) * Math.PI
                        val bars = List(24) { i ->
                            if (isPlaying) {
                                val wave = abs(sin(phase + i * 0.45)).toFloat()
                                (0.2f + wave * 0.78f).coerceIn(0.12f, 1f)
                            } else {
                                0.14f
                            }
                        }
                        _playerState.update {
                            it.copy(
                                positionMs = pos,
                                playing = isPlaying,
                                spectrumBars = bars
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
                tick++
                delay(120L)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }
    }
}
