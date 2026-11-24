package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.audio.AudioSynthEngine
import com.example.audio.MusicPlayerManager
import com.example.data.ai.AiMusicRepository
import com.example.data.model.AppSettingsRecord
import com.example.data.model.CatalogDefaults
import com.example.data.model.CategoryRecord
import com.example.data.model.ChatMessageRecord
import com.example.data.model.PlayerState
import com.example.data.model.PlaylistRecord
import com.example.data.model.PublicSongRecord
import com.example.data.model.SongRecord
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoStyleOption
import com.example.data.repository.MusicRepository
import com.example.util.DownloadExportHelper
import com.example.video.VideoRenderEngine
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "StudioViewModel"
private const val STOP_TIMEOUT_MS = 5000L

data class StudioUiState(
    val generating: Boolean = false,
    val stageLabel: String = "",
    val progress: Float = 0f,
    val activeSong: SongRecord? = null,
    val editableLyrics: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val generatingCover: Boolean = false,
    val coverBitmap: Bitmap? = null,
    val coverError: String? = null,
    val renderingVideo: Boolean = false,
    val videoProgress: Float = 0f,
    val videoStatus: String? = null,
    val renderedVideoPath: String? = null
)

data class ChatSessionState(
    val sending: Boolean = false,
    val streamingReply: String? = null,
    val statusStage: String? = null,
    val activeSongId: String? = null,
    val errorMessage: String? = null,
    val quickSuggestions: List<String> = listOf(
        "Make an energetic DJ song with my name",
        "Create 3 DJ song variations in English",
        "Make a romantic song for Meera",
        "Create a classic melodic track"
    )
)

sealed interface StudioNavEvent {
    data class OpenVideoStudio(val songId: String) : StudioNavEvent
    data class OpenPlayer(val songId: String) : StudioNavEvent
    data class OpenSongStudio(val presetPrompt: String = "", val presetGenre: String = "DJ Bass Boost") : StudioNavEvent
}

class MusicStudioViewModel(
    private val repo: MusicRepository,
    private val aiRepo: AiMusicRepository,
    val currentUserId: String
) : ViewModel() {

    private fun <T> Flow<T>.asUiState(errorFallback: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, errorFallback, rootError)
                emit(UiState.Error(rootError.message ?: errorFallback))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val userProfile: StateFlow<UiState<UserProfile?>> =
        repo.observeUserProfile(currentUserId).asUiState("Failed to load profile")

    val mySongs: StateFlow<UiState<List<SongRecord>>> =
        repo.observeMySongs(currentUserId).asUiState("Failed to load your songs")

    val playlists: StateFlow<UiState<List<PlaylistRecord>>> =
        repo.observePlaylists(currentUserId).asUiState("Failed to load playlists")

    val chatMessages: StateFlow<UiState<List<ChatMessageRecord>>> =
        repo.observeChatMessages(currentUserId).asUiState("Failed to load chat history")

    val publicSongs: StateFlow<UiState<List<PublicSongRecord>>> =
        repo.observePublicSongs(currentUserId).asUiState("Failed to load community songs")

    val categories: StateFlow<UiState<List<CategoryRecord>>> =
        repo.observeActiveCategories(currentUserId).asUiState("Failed to load categories")

    val appSettings: StateFlow<AppSettingsRecord?> =
        repo.observeAppSettings(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val playerState: StateFlow<PlayerState> = MusicPlayerManager.playerState

    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    private val _chatState = MutableStateFlow(ChatSessionState())
    val chatState: StateFlow<ChatSessionState> = _chatState.asStateFlow()

    private val _navEvents = MutableSharedFlow<StudioNavEvent>(extraBufferCapacity = 4)
    val navEvents = _navEvents.asSharedFlow()

    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessages = _toastMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.ensureUserProfile().onFailure { e ->
                Log.w(TAG, "Profile initialization warning: ${e.message}")
            }
        }
    }

    fun isAdminUser(): Boolean {
        val profile = (userProfile.value as? UiState.Success)?.data
        return (profile?.isAdminUser == true) || repo.isCurrentUserAdmin()
    }

    fun selectActiveSong(song: SongRecord) {
        val coverBmp = song.localCoverPath?.let { path ->
            File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
        }
        _studioState.update {
            it.copy(
                activeSong = song,
                editableLyrics = song.lyrics,
                coverBitmap = coverBmp,
                renderedVideoPath = song.localVideoPath?.takeIf { p -> File(p).exists() },
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun updateEditableLyrics(newLyrics: String) {
        _studioState.update { it.copy(editableLyrics = newLyrics) }
    }

    fun clearStudioStatus() {
        _studioState.update { it.copy(statusMessage = null, errorMessage = null, coverError = null) }
    }

    // --- AI Music Chat ---

    fun sendChatMessage(context: Context, userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _chatState.value.sending) return

        viewModelScope.launch {
            _chatState.update {
                it.copy(
                    sending = true,
                    streamingReply = "",
                    statusStage = "AI Music Assistant is replying...",
                    errorMessage = null
                )
            }
            try {
                val history = (chatMessages.value as? UiState.Success)?.data.orEmpty()
                val userDisplayName = (userProfile.value as? UiState.Success)?.data?.displayName.orEmpty()
                repo.addChatMessage(sender = "user", text = trimmed)

                var fullReply = ""
                aiRepo.streamChatReply(history, trimmed, userDisplayName).collect { accumulated ->
                    fullReply = accumulated
                    _chatState.update { it.copy(streamingReply = accumulated, statusStage = null) }
                }

                val currentActiveSong = _studioState.value.activeSong
                    ?: (mySongs.value as? UiState.Success)?.data?.firstOrNull()

                _chatState.update { it.copy(statusStage = "Analyzing musical direction...") }
                val intentResult = aiRepo.analyzeChatIntent(history, trimmed, currentActiveSong, userDisplayName)
                val intent = intentResult.getOrNull()
                val suggestions = intent?.suggestions?.ifEmpty {
                    listOf("Regenerate with more bass", "Change mood to Romantic", "Create Music Video")
                } ?: listOf("Make Bengali DJ remix", "Generate Sad version", "Create Music Video")

                var generatedSongId: String? = null
                var generatedSongTitle: String? = null

                if (intent != null) {
                    when (intent.action) {
                        "generate_song", "regenerate_song" -> {
                            val totalVariations = intent.variationCount.coerceIn(1, 3)
                            val resolvedRecipient = intent.recipientName
                                .ifBlank { currentActiveSong?.recipientName.orEmpty() }
                                .ifBlank { userDisplayName }
                            val variationStyles = listOf(
                                intent.style.ifBlank { "Studio DJ" },
                                "Trap Bass Boost",
                                "Club Party Remix"
                            )
                            val savedSongs = mutableListOf<SongRecord>()
                            for (vIdx in 1..totalVariations) {
                                val styleForVariation = if (totalVariations > 1) {
                                    variationStyles[(vIdx - 1) % variationStyles.size]
                                } else {
                                    intent.style
                                }
                                val titleForVariation = if (totalVariations > 1) {
                                    "${intent.title} (Mix $vIdx)"
                                } else {
                                    intent.title
                                }
                                _chatState.update {
                                    it.copy(
                                        streamingReply = fullReply,
                                        statusStage = if (totalVariations > 1) {
                                            "Composing variation $vIdx/$totalVariations (${intent.genre})..."
                                        } else {
                                            "Composing ${intent.genre} lyrics & melody..."
                                        }
                                    )
                                }
                                val draftRes = aiRepo.generatePersonalizedSong(
                                    recipientName = resolvedRecipient,
                                    customTitle = titleForVariation,
                                    customLyrics = "",
                                    promptText = trimmed,
                                    language = intent.language,
                                    genre = intent.genre,
                                    style = styleForVariation,
                                    mood = intent.mood,
                                    voiceType = intent.voiceType,
                                    beat = intent.beat,
                                    instruments = intent.instruments,
                                    durationSec = intent.durationSec,
                                    variationIndex = vIdx
                                )
                                val draft = draftRes.getOrThrow()
                                val tempId = "song_${System.currentTimeMillis()}_$vIdx"
                                _chatState.update {
                                    it.copy(
                                        statusStage = if (totalVariations > 1) {
                                            "Synthesizing variation $vIdx/$totalVariations audio..."
                                        } else {
                                            "Synthesizing ${draft.genre} studio WAV audio..."
                                        }
                                    )
                                }
                                val wavFile = AudioSynthEngine.synthesizeSongToWav(
                                    context = context.applicationContext,
                                    songId = tempId,
                                    title = draft.title,
                                    recipientName = draft.recipientName,
                                    lyrics = draft.lyrics,
                                    genre = draft.genre,
                                    mood = draft.mood,
                                    voiceType = draft.voiceType,
                                    bpm = draft.bpm,
                                    chordProgression = draft.chordProgression,
                                    durationSec = draft.durationSec,
                                    onProgress = { prog ->
                                        _chatState.update {
                                            it.copy(
                                                statusStage = if (totalVariations > 1) {
                                                    "Synthesizing mix $vIdx/$totalVariations (${(prog * 100).toInt()}%)..."
                                                } else {
                                                    "Synthesizing ${draft.genre} audio (${(prog * 100).toInt()}%)..."
                                                }
                                            )
                                        }
                                    }
                                )
                                val newSong = SongRecord(
                                    userId = currentUserId,
                                    title = draft.title,
                                    recipientName = draft.recipientName,
                                    prompt = draft.prompt,
                                    lyrics = draft.lyrics,
                                    language = draft.language,
                                    genre = draft.genre,
                                    style = draft.style,
                                    mood = draft.mood,
                                    voiceType = draft.voiceType,
                                    beat = draft.beat,
                                    instruments = draft.instruments,
                                    durationSec = draft.durationSec,
                                    bpm = draft.bpm,
                                    musicKey = draft.musicKey,
                                    chordProgression = draft.chordProgression,
                                    coverType = draft.coverType,
                                    localAudioPath = wavFile.absolutePath,
                                    variationIndex = draft.variationIndex
                                )
                                val savedId = repo.saveSong(newSong).getOrThrow()
                                val savedSong = newSong.copy(id = savedId)
                                deductOneCredit()
                                savedSongs.add(savedSong)
                                if (vIdx > 1) {
                                    repo.addChatMessage(
                                        sender = "ai",
                                        text = "Variation #$vIdx ready: ${savedSong.title} (${savedSong.style}, ${savedSong.language})",
                                        songId = savedId,
                                        songTitle = savedSong.title,
                                        suggestions = emptyList()
                                    )
                                }
                            }
                            if (savedSongs.isNotEmpty()) {
                                val leadSong = savedSongs.first()
                                selectActiveSong(leadSong)
                                MusicPlayerManager.playSong(context, leadSong, savedSongs)
                                generatedSongId = leadSong.id
                                generatedSongTitle = leadSong.title
                            }
                        }

                        "change_mood" -> {
                            if (currentActiveSong != null) {
                                _chatState.update {
                                    it.copy(streamingReply = "$fullReply\n\nTransforming song mood to ${intent.mood}...")
                                }
                                val transformed = aiRepo.transformSongMood(currentActiveSong, intent.mood).getOrThrow()
                                val tempId = "mood_${System.currentTimeMillis()}"
                                val wavFile = AudioSynthEngine.synthesizeSongToWav(
                                    context = context.applicationContext,
                                    songId = tempId,
                                    title = transformed.title,
                                    recipientName = transformed.recipientName,
                                    lyrics = transformed.lyrics,
                                    genre = transformed.genre,
                                    mood = transformed.mood,
                                    voiceType = transformed.voiceType,
                                    bpm = transformed.bpm,
                                    chordProgression = transformed.chordProgression,
                                    durationSec = transformed.durationSec,
                                    onProgress = {}
                                )
                                val newSong = SongRecord(
                                    userId = currentUserId,
                                    title = transformed.title,
                                    recipientName = transformed.recipientName,
                                    prompt = transformed.prompt,
                                    lyrics = transformed.lyrics,
                                    language = transformed.language,
                                    genre = transformed.genre,
                                    style = transformed.style,
                                    mood = transformed.mood,
                                    voiceType = transformed.voiceType,
                                    beat = transformed.beat,
                                    instruments = transformed.instruments,
                                    durationSec = transformed.durationSec,
                                    bpm = transformed.bpm,
                                    musicKey = transformed.musicKey,
                                    chordProgression = transformed.chordProgression,
                                    coverType = transformed.coverType,
                                    localAudioPath = wavFile.absolutePath
                                )
                                val savedId = repo.saveSong(newSong).getOrThrow()
                                val savedSong = newSong.copy(id = savedId)
                                deductOneCredit()
                                selectActiveSong(savedSong)
                                MusicPlayerManager.playSong(context, savedSong, listOf(savedSong))
                                generatedSongId = savedId
                                generatedSongTitle = savedSong.title
                            }
                        }

                        "create_video" -> {
                            val targetId = currentActiveSong?.id
                            if (!targetId.isNullOrBlank()) {
                                selectActiveSong(currentActiveSong)
                                _navEvents.tryEmit(StudioNavEvent.OpenVideoStudio(targetId))
                            }
                        }
                    }
                }

                repo.addChatMessage(
                    sender = "ai",
                    text = fullReply.ifBlank { "Ready to craft your track!" },
                    songId = generatedSongId,
                    songTitle = generatedSongTitle,
                    suggestions = suggestions
                )

                _chatState.update {
                    it.copy(
                        sending = false,
                        streamingReply = null,
                        statusStage = null,
                        activeSongId = generatedSongId ?: it.activeSongId,
                        quickSuggestions = suggestions
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation error", e)
                _chatState.update {
                    it.copy(
                        sending = false,
                        streamingReply = null,
                        statusStage = null,
                        errorMessage = e.localizedMessage ?: "AI chat failed. Please check connection and App Check."
                    )
                }
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repo.clearChatHistory()
                .onSuccess { _toastMessages.tryEmit("Chat history cleared") }
                .onFailure { _toastMessages.tryEmit("Could not clear chat: ${it.localizedMessage}") }
        }
    }

    // --- Personalized Song & Lyrics Studio ---

    fun generateLyricsDraft(
        recipientName: String,
        customTitle: String,
        promptText: String,
        language: String,
        genre: String,
        style: String,
        mood: String,
        voiceType: String,
        beat: String,
        instruments: String,
        durationSec: Int
    ) {
        viewModelScope.launch {
            _studioState.update {
                it.copy(generating = true, stageLabel = "Writing personalized AI lyrics...", progress = 0.25f, errorMessage = null)
            }
            try {
                val draft = aiRepo.generatePersonalizedSong(
                    recipientName = recipientName,
                    customTitle = customTitle,
                    customLyrics = "",
                    promptText = promptText,
                    language = language,
                    genre = genre,
                    style = style,
                    mood = mood,
                    voiceType = voiceType,
                    beat = beat,
                    instruments = instruments,
                    durationSec = durationSec
                ).getOrThrow()

                val tempSong = SongRecord(
                    userId = currentUserId,
                    title = draft.title,
                    recipientName = draft.recipientName,
                    prompt = draft.prompt,
                    lyrics = draft.lyrics,
                    language = draft.language,
                    genre = draft.genre,
                    style = draft.style,
                    mood = draft.mood,
                    voiceType = draft.voiceType,
                    beat = draft.beat,
                    instruments = draft.instruments,
                    durationSec = draft.durationSec,
                    bpm = draft.bpm,
                    musicKey = draft.musicKey,
                    chordProgression = draft.chordProgression,
                    coverType = draft.coverType
                )
                _studioState.update {
                    it.copy(
                        generating = false,
                        stageLabel = "",
                        progress = 1f,
                        activeSong = tempSong,
                        editableLyrics = draft.lyrics,
                        statusMessage = "Lyrics generated! Edit them below or tap Generate Music."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _studioState.update {
                    it.copy(generating = false, stageLabel = "", errorMessage = e.localizedMessage ?: "Lyrics generation failed")
                }
            }
        }
    }

    fun generatePersonalizedMusic(
        context: Context,
        recipientName: String,
        customTitle: String,
        customLyrics: String,
        promptText: String,
        language: String,
        genre: String,
        style: String,
        mood: String,
        voiceType: String,
        beat: String,
        instruments: String,
        durationSec: Int,
        variationsCount: Int = 1
    ) {
        val count = variationsCount.coerceIn(1, 3)
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    generating = true,
                    stageLabel = "Composing AI melody & lyrics...",
                    progress = 0.1f,
                    errorMessage = null,
                    statusMessage = null
                )
            }
            try {
                var lastSaved: SongRecord? = null
                for (v in 1..count) {
                    _studioState.update {
                        it.copy(
                            stageLabel = if (count > 1) "Composing variation $v of $count..." else "Composing AI melody & lyrics...",
                            progress = ((v - 1).toFloat() / count) + 0.1f
                        )
                    }
                    val draft = aiRepo.generatePersonalizedSong(
                        recipientName = recipientName,
                        customTitle = if (count > 1 && customTitle.isNotBlank()) "$customTitle (V$v)" else customTitle,
                        customLyrics = customLyrics,
                        promptText = promptText,
                        language = language,
                        genre = genre,
                        style = style,
                        mood = mood,
                        voiceType = voiceType,
                        beat = beat,
                        instruments = instruments,
                        durationSec = durationSec,
                        variationIndex = v
                    ).getOrThrow()

                    val tempId = "song_${System.currentTimeMillis()}_$v"
                    val wavFile = AudioSynthEngine.synthesizeSongToWav(
                        context = context.applicationContext,
                        songId = tempId,
                        title = draft.title,
                        recipientName = draft.recipientName,
                        lyrics = draft.lyrics,
                        genre = draft.genre,
                        mood = draft.mood,
                        voiceType = draft.voiceType,
                        bpm = draft.bpm,
                        chordProgression = draft.chordProgression,
                        durationSec = draft.durationSec,
                        onProgress = { synthProg ->
                            _studioState.update {
                                it.copy(
                                    stageLabel = "Synthesizing ${draft.genre} audio (${(synthProg * 100).toInt()}%)...",
                                    progress = ((v - 1 + 0.3f + synthProg * 0.65f) / count).coerceIn(0.1f, 0.96f)
                                )
                            }
                        }
                    )

                    val song = SongRecord(
                        userId = currentUserId,
                        title = draft.title,
                        recipientName = draft.recipientName,
                        prompt = draft.prompt,
                        lyrics = draft.lyrics,
                        language = draft.language,
                        genre = draft.genre,
                        style = draft.style,
                        mood = draft.mood,
                        voiceType = draft.voiceType,
                        beat = draft.beat,
                        instruments = draft.instruments,
                        durationSec = draft.durationSec,
                        bpm = draft.bpm,
                        musicKey = draft.musicKey,
                        chordProgression = draft.chordProgression,
                        coverType = draft.coverType,
                        localAudioPath = wavFile.absolutePath,
                        variationIndex = v
                    )
                    val savedId = repo.saveSong(song).getOrThrow()
                    lastSaved = song.copy(id = savedId)
                    deductOneCredit()
                }

                if (lastSaved != null) {
                    selectActiveSong(lastSaved)
                    MusicPlayerManager.playSong(context, lastSaved, listOf(lastSaved))
                }
                _studioState.update {
                    it.copy(
                        generating = false,
                        stageLabel = "",
                        progress = 1f,
                        statusMessage = if (count > 1) "$count music variations created & ready!" else "Song created & playing!"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Song studio generation failed", e)
                _studioState.update {
                    it.copy(
                        generating = false,
                        stageLabel = "",
                        errorMessage = e.localizedMessage ?: "Failed to generate song."
                    )
                }
            }
        }
    }

    fun translateActiveLyrics(song: SongRecord, targetLanguage: String) {
        viewModelScope.launch {
            _studioState.update { it.copy(generating = true, stageLabel = "Translating lyrics to $targetLanguage...", errorMessage = null) }
            aiRepo.translateLyrics(song.lyrics, targetLanguage)
                .onSuccess { translated ->
                    _studioState.update {
                        it.copy(
                            generating = false,
                            editableLyrics = translated,
                            activeSong = song.copy(lyrics = translated, language = targetLanguage),
                            statusMessage = "Translated to $targetLanguage!"
                        )
                    }
                    if (song.id.isNotBlank()) {
                        repo.updateSongPartial(song.id, mapOf("lyrics" to translated))
                    }
                }
                .onFailure { err ->
                    _studioState.update {
                        it.copy(generating = false, errorMessage = err.localizedMessage ?: "Translation failed")
                    }
                }
        }
    }

    fun changeSongMood(context: Context, song: SongRecord, targetMood: String) {
        viewModelScope.launch {
            _studioState.update { it.copy(generating = true, stageLabel = "Transforming mood to $targetMood...", progress = 0.2f, errorMessage = null) }
            try {
                val draft = aiRepo.transformSongMood(song, targetMood).getOrThrow()
                val wavFile = AudioSynthEngine.synthesizeSongToWav(
                    context = context.applicationContext,
                    songId = "mood_${System.currentTimeMillis()}",
                    title = draft.title,
                    recipientName = draft.recipientName,
                    lyrics = draft.lyrics,
                    genre = draft.genre,
                    mood = draft.mood,
                    voiceType = draft.voiceType,
                    bpm = draft.bpm,
                    chordProgression = draft.chordProgression,
                    durationSec = draft.durationSec,
                    onProgress = { p -> _studioState.update { it.copy(progress = 0.3f + p * 0.65f) } }
                )
                val newSong = SongRecord(
                    userId = currentUserId,
                    title = draft.title,
                    recipientName = draft.recipientName,
                    prompt = draft.prompt,
                    lyrics = draft.lyrics,
                    language = draft.language,
                    genre = draft.genre,
                    style = draft.style,
                    mood = draft.mood,
                    voiceType = draft.voiceType,
                    beat = draft.beat,
                    instruments = draft.instruments,
                    durationSec = draft.durationSec,
                    bpm = draft.bpm,
                    musicKey = draft.musicKey,
                    chordProgression = draft.chordProgression,
                    coverType = draft.coverType,
                    localAudioPath = wavFile.absolutePath
                )
                val savedId = repo.saveSong(newSong).getOrThrow()
                val saved = newSong.copy(id = savedId)
                deductOneCredit()
                selectActiveSong(saved)
                MusicPlayerManager.playSong(context, saved, listOf(saved))
                _studioState.update {
                    it.copy(generating = false, progress = 1f, statusMessage = "Created $targetMood version!")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _studioState.update {
                    it.copy(generating = false, errorMessage = e.localizedMessage ?: "Mood change failed")
                }
            }
        }
    }

    fun generateAiAlbumCover(context: Context, song: SongRecord, customTheme: String) {
        viewModelScope.launch {
            _studioState.update { it.copy(generatingCover = true, coverError = null) }
            aiRepo.generateAlbumCover(song.title, song.genre, song.mood, customTheme)
                .onSuccess { bitmap ->
                    val file = DownloadExportHelper.saveBitmapAsCoverPng(context.applicationContext, song.id.ifBlank { song.title }, bitmap)
                    if (song.id.isNotBlank()) {
                        repo.updateSongPartial(song.id, mapOf("localCoverPath" to file.absolutePath))
                    }
                    _studioState.update {
                        it.copy(
                            generatingCover = false,
                            coverBitmap = bitmap,
                            activeSong = song.copy(localCoverPath = file.absolutePath),
                            statusMessage = "AI Album Cover ready!"
                        )
                    }
                }
                .onFailure { err ->
                    _studioState.update {
                        it.copy(
                            generatingCover = false,
                            coverError = err.localizedMessage ?: "Failed to generate album cover."
                        )
                    }
                }
        }
    }

    fun renderMusicVideo(
        context: Context,
        song: SongRecord,
        style: VideoStyleOption,
        overlayText: String,
        showLyrics: Boolean,
        accentHex: Int,
        durationSec: Int,
        slideshowPhotos: List<Bitmap>
    ) {
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    renderingVideo = true,
                    videoProgress = 0.02f,
                    videoStatus = "Initializing MP4 encoder...",
                    errorMessage = null
                )
            }
            try {
                val coverBmp = _studioState.value.coverBitmap
                    ?: song.localCoverPath?.let { File(it).takeIf { f -> f.exists() }?.let { f -> BitmapFactory.decodeFile(f.absolutePath) } }
                val mp4File = VideoRenderEngine.renderMusicVideoToMp4(
                    context = context.applicationContext,
                    song = song,
                    style = style,
                    overlayText = overlayText,
                    showLyrics = showLyrics,
                    accentHex = accentHex,
                    durationSec = durationSec,
                    coverBitmap = coverBmp,
                    slideshowPhotos = slideshowPhotos,
                    onProgress = { p ->
                        _studioState.update {
                            it.copy(
                                videoProgress = p,
                                videoStatus = "Rendering ${style.label} (${(p * 100).toInt()}%)..."
                            )
                        }
                    }
                )
                if (song.id.isNotBlank()) {
                    repo.updateSongPartial(song.id, mapOf("localVideoPath" to mp4File.absolutePath))
                }
                _studioState.update {
                    it.copy(
                        renderingVideo = false,
                        videoProgress = 1f,
                        videoStatus = "MP4 Music Video ready!",
                        renderedVideoPath = mp4File.absolutePath,
                        activeSong = song.copy(localVideoPath = mp4File.absolutePath)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "MP4 render failed", e)
                _studioState.update {
                    it.copy(
                        renderingVideo = false,
                        videoStatus = null,
                        errorMessage = e.localizedMessage ?: "MP4 rendering failed."
                    )
                }
            }
        }
    }

    // --- Playback, Library, Playlists & Downloads ---

    fun playSong(context: Context, song: SongRecord, queue: List<SongRecord> = listOf(song)) {
        MusicPlayerManager.playSong(context, song, queue) { synthesizedSong, wavFile ->
            if (synthesizedSong.userId == currentUserId && synthesizedSong.id.isNotBlank()) {
                viewModelScope.launch {
                    repo.updateSongPartial(
                        synthesizedSong.id,
                        mapOf(
                            "localAudioPath" to wavFile.absolutePath,
                            "playCount" to synthesizedSong.playCount + 1
                        )
                    )
                }
            }
        }
        if (song.userId == currentUserId && song.id.isNotBlank() && song.localAudioPath != null) {
            viewModelScope.launch {
                repo.updateSongPartial(song.id, mapOf("playCount" to song.playCount + 1))
            }
        }
    }

    fun saveShowcaseOrPublicToLibrary(song: SongRecord) {
        viewModelScope.launch {
            val copy = song.copy(id = "", userId = currentUserId, visibility = "private", favorite = false)
            repo.saveSong(copy)
                .onSuccess { _toastMessages.tryEmit("Saved '${song.title}' to My Music") }
                .onFailure { _toastMessages.tryEmit("Save failed: ${it.localizedMessage}") }
        }
    }

    fun toggleFavorite(song: SongRecord) {
        if (song.id.isBlank() || song.userId != currentUserId) {
            saveShowcaseOrPublicToLibrary(song.copy(favorite = true))
            return
        }
        viewModelScope.launch {
            repo.updateSongPartial(song.id, mapOf("favorite" to !song.favorite))
                .onFailure { _toastMessages.tryEmit("Could not update favorite") }
        }
    }

    fun renameSong(song: SongRecord, newTitle: String) {
        if (song.id.isBlank() || newTitle.isBlank()) return
        viewModelScope.launch {
            repo.updateSongPartial(song.id, mapOf("title" to newTitle.trim().take(120)))
                .onSuccess { _toastMessages.tryEmit("Renamed to $newTitle") }
                .onFailure { _toastMessages.tryEmit("Rename failed: ${it.localizedMessage}") }
        }
    }

    fun deleteSong(song: SongRecord) {
        if (song.id.isBlank()) return
        viewModelScope.launch {
            repo.deleteSong(song.id)
                .onSuccess { _toastMessages.tryEmit("Deleted '${song.title}'") }
                .onFailure { _toastMessages.tryEmit("Delete failed: ${it.localizedMessage}") }
        }
    }

    fun publishSongToFeed(song: SongRecord) {
        viewModelScope.launch {
            val creator = (userProfile.value as? UiState.Success)?.data?.displayName ?: "Creator"
            repo.publishSongToCommunity(song, creator)
                .onSuccess {
                    if (song.id.isNotBlank()) {
                        repo.updateSongPartial(song.id, mapOf("visibility" to "public"))
                    }
                    _toastMessages.tryEmit("Published '${song.title}' to Community Feed!")
                }
                .onFailure { _toastMessages.tryEmit("Publish failed: ${it.localizedMessage}") }
        }
    }

    fun makeSongPrivate(song: SongRecord) {
        if (song.id.isBlank()) return
        viewModelScope.launch {
            repo.updateSongPartial(song.id, mapOf("visibility" to "private"))
                .onSuccess { _toastMessages.tryEmit("'${song.title}' set to Private") }
        }
    }

    fun likePublicSong(song: PublicSongRecord) {
        viewModelScope.launch {
            repo.likePublicSong(song)
        }
    }

    fun reportSong(songId: String, songTitle: String, reason: String) {
        viewModelScope.launch {
            repo.submitReport(songId, songTitle, reason)
                .onSuccess { _toastMessages.tryEmit("Report submitted for admin review") }
                .onFailure { _toastMessages.tryEmit("Failed to report: ${it.localizedMessage}") }
        }
    }

    fun createAiPlaylist(moodOrTheme: String) {
        viewModelScope.launch {
            val songs = (mySongs.value as? UiState.Success)?.data.orEmpty()
            val titles = songs.map { it.title }
            val suggestion = aiRepo.generatePlaylistSuggestion(moodOrTheme, titles).getOrNull()
            val name = suggestion?.first ?: "$moodOrTheme AI Mix"
            val desc = suggestion?.second ?: "Personalized AI playlist for $moodOrTheme"
            val matchedIds = songs.filter {
                it.mood.contains(moodOrTheme, true) || it.genre.contains(moodOrTheme, true)
            }.ifEmpty { songs.take(5) }.map { it.id }

            repo.createPlaylist(name, desc, matchedIds)
                .onSuccess { _toastMessages.tryEmit("Created playlist '$name'") }
                .onFailure { _toastMessages.tryEmit("Playlist error: ${it.localizedMessage}") }
        }
    }

    fun createPlaylist(name: String, description: String, initialSongIds: List<String> = emptyList()) {
        viewModelScope.launch {
            repo.createPlaylist(name, description, initialSongIds)
                .onSuccess { _toastMessages.tryEmit("Playlist '$name' saved") }
                .onFailure { _toastMessages.tryEmit("Failed: ${it.localizedMessage}") }
        }
    }

    fun addSongToPlaylist(playlist: PlaylistRecord, songId: String) {
        viewModelScope.launch {
            repo.updatePlaylistSongs(playlist.id, playlist.songIds + songId)
                .onSuccess { _toastMessages.tryEmit("Added to '${playlist.name}'") }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repo.deletePlaylist(playlistId)
        }
    }

    fun exportSongAudioWav(context: Context, song: SongRecord) {
        val file = song.localAudioPath?.let { File(it) }?.takeIf { it.exists() }
            ?: File(context.filesDir, "audio/${song.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}.wav")
                .takeIf { it.exists() }
        if (file == null) {
            _toastMessages.tryEmit("Audio file is not ready yet. Play or generate audio first.")
            return
        }
        val cleanName = "${song.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}.wav"
        val audioUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else null
        DownloadExportHelper.exportToDeviceDownloads(context, file, cleanName, "audio/wav", audioUri)
            .onSuccess { msg ->
                if (song.id.isNotBlank() && song.userId == currentUserId) {
                    viewModelScope.launch { repo.updateSongPartial(song.id, mapOf("downloaded" to true)) }
                }
                _toastMessages.tryEmit(msg)
            }
            .onFailure { _toastMessages.tryEmit("Export failed: ${it.localizedMessage}") }
    }

    fun exportSongVideoMp4(context: Context, song: SongRecord) {
        val file = song.localVideoPath?.let { File(it) }?.takeIf { it.exists() }
        if (file == null) {
            _toastMessages.tryEmit("MP4 video has not been rendered yet.")
            return
        }
        val cleanName = "${song.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}.mp4"
        val videoUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else null
        DownloadExportHelper.exportToDeviceDownloads(context, file, cleanName, "video/mp4", videoUri)
            .onSuccess { msg -> _toastMessages.tryEmit(msg) }
            .onFailure { _toastMessages.tryEmit("Export failed: ${it.localizedMessage}") }
    }

    fun exportSongCoverImage(context: Context, song: SongRecord) {
        val file = song.localCoverPath?.let { File(it) }?.takeIf { it.exists() }
        if (file == null) {
            _toastMessages.tryEmit("Custom AI cover image is not generated yet.")
            return
        }
        val cleanName = "${song.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}_cover.png"
        val imgUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else null
        DownloadExportHelper.exportToDeviceDownloads(context, file, cleanName, "image/png", imgUri)
            .onSuccess { msg -> _toastMessages.tryEmit(msg) }
            .onFailure { _toastMessages.tryEmit("Export failed: ${it.localizedMessage}") }
    }

    fun exportSongLyrics(context: Context, song: SongRecord) {
        val lyricsFile = DownloadExportHelper.saveLyricsToFile(context, song)
        val cleanName = "${song.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}_lyrics.txt"
        DownloadExportHelper.exportToDeviceDownloads(context, lyricsFile, cleanName, "text/plain", null)
            .onSuccess { msg -> _toastMessages.tryEmit(msg) }
            .onFailure { _toastMessages.tryEmit("Export failed: ${it.localizedMessage}") }
    }

    fun updateProfile(displayName: String, preferredLanguage: String) {
        viewModelScope.launch {
            repo.updateUserProfileSettings(displayName, preferredLanguage)
                .onSuccess { _toastMessages.tryEmit("Profile updated") }
                .onFailure { _toastMessages.tryEmit("Update failed: ${it.localizedMessage}") }
        }
    }

    fun openStudioWithPreset(prompt: String, genre: String) {
        _navEvents.tryEmit(StudioNavEvent.OpenSongStudio(prompt, genre))
    }

    fun openVideoStudioFor(song: SongRecord) {
        selectActiveSong(song)
        _navEvents.tryEmit(StudioNavEvent.OpenVideoStudio(song.id))
    }

    private suspend fun deductOneCredit() {
        val currentCredits = (userProfile.value as? UiState.Success)?.data?.credits ?: return
        if (currentCredits > 0) {
            repo.spendCredits(currentCredits - 1)
        }
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MusicStudioViewModel(
                    repo = MusicRepository(db),
                    aiRepo = AiMusicRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
