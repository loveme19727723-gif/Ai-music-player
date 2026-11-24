package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.AppSettingsRecord
import com.example.data.model.CategoryRecord
import com.example.data.model.PublicSongRecord
import com.example.data.model.ReportRecord
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.MusicRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "AdminViewModel"

class AdminViewModel(
    private val repo: MusicRepository,
    private val currentUserId: String
) : ViewModel() {

    private fun <T> Flow<T>.asUiState(err: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, err, root)
                emit(UiState.Error(root.message ?: err))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val allUsers: StateFlow<UiState<List<UserProfile>>> =
        repo.observeAllUsersForAdmin(currentUserId).asUiState("Failed to load users")

    val allReports: StateFlow<UiState<List<ReportRecord>>> =
        repo.observeAllReportsForAdmin(currentUserId).asUiState("Failed to load reports")

    val publicSongs: StateFlow<UiState<List<PublicSongRecord>>> =
        repo.observePublicSongs(currentUserId).asUiState("Failed to load public songs")

    val categories: StateFlow<UiState<List<CategoryRecord>>> =
        repo.observeActiveCategories(currentUserId).asUiState("Failed to load categories")

    val appSettings: StateFlow<UiState<AppSettingsRecord?>> =
        repo.observeAppSettings(currentUserId).asUiState("Failed to load settings")

    private val _adminToast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val adminToast = _adminToast.asSharedFlow()

    fun updateUser(targetUid: String, blocked: Boolean, credits: Int, role: String) {
        viewModelScope.launch {
            repo.adminUpdateUser(targetUid, blocked, credits, role)
                .onSuccess { _adminToast.tryEmit("Updated user $targetUid") }
                .onFailure { _adminToast.tryEmit("Error: ${it.localizedMessage}") }
        }
    }

    fun updateFeaturedSection(songId: String, section: String, visibility: String) {
        viewModelScope.launch {
            repo.adminUpdateFeaturedSong(songId, section, visibility)
                .onSuccess { _adminToast.tryEmit("Updated featured status") }
                .onFailure { _adminToast.tryEmit("Error: ${it.localizedMessage}") }
        }
    }

    fun publishFeaturedTrack(title: String, artist: String, genre: String, mood: String, language: String, section: String) {
        viewModelScope.launch {
            val song = PublicSongRecord(
                userId = currentUserId,
                creatorName = artist.ifBlank { "AI Studio Featured" },
                title = title.ifBlank { "Featured Creation" },
                lyrics = "[Intro]\nFeatured studio track: $title\n\n[Chorus]\n$genre • $mood vibes in $language",
                language = language,
                genre = genre,
                style = genre,
                mood = mood,
                voiceType = "Male",
                instruments = "Synth, Bass, Drums",
                durationSec = 36,
                bpm = if (genre.contains("DJ", true)) 134 else 96,
                musicKey = "Am",
                chordProgression = listOf("Am", "F", "C", "G"),
                coverType = if (genre.contains("DJ", true)) "dj" else if (genre.contains("Classic", true)) "classic" else "romantic",
                visibility = "public",
                featuredSection = section
            )
            repo.adminPublishFeaturedSong(song)
                .onSuccess { _adminToast.tryEmit("Published featured track '$title'") }
                .onFailure { _adminToast.tryEmit("Error: ${it.localizedMessage}") }
        }
    }

    fun addCategory(name: String, group: String) {
        viewModelScope.launch {
            repo.adminAddCategory(name, group)
                .onSuccess { _adminToast.tryEmit("Added category '$name'") }
                .onFailure { _adminToast.tryEmit("Error: ${it.localizedMessage}") }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            repo.adminDeleteCategory(categoryId)
                .onSuccess { _adminToast.tryEmit("Removed category") }
                .onFailure { _adminToast.tryEmit("Error: ${it.localizedMessage}") }
        }
    }

    fun resolveReport(reportId: String, status: String) {
        viewModelScope.launch {
            repo.adminResolveReport(reportId, status)
                .onSuccess { _adminToast.tryEmit("Report marked $status") }
                .onFailure { _adminToast.tryEmit("Error: ${it.localizedMessage}") }
        }
    }

    fun saveSettings(settings: AppSettingsRecord) {
        viewModelScope.launch {
            repo.adminSaveAppSettings(settings)
                .onSuccess { _adminToast.tryEmit("Saved app & AI API settings") }
                .onFailure { _adminToast.tryEmit("Error: ${it.localizedMessage}") }
        }
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AdminViewModel(MusicRepository(db), currentUserId)
            }
        }
    }
}
