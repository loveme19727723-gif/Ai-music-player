package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AppSettingsRecord
import com.example.data.model.BOOTSTRAPPED_ADMIN_EMAIL
import com.example.data.model.CategoryRecord
import com.example.data.model.ChatMessageRecord
import com.example.data.model.PlaylistRecord
import com.example.data.model.PublicSongRecord
import com.example.data.model.ReportRecord
import com.example.data.model.SongRecord
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MusicRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun handleStreamError(error: Throwable, op: OperationType, path: String, uid: String): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    suspend fun ensureUserProfile(): Result<UserProfile> {
        val uid = auth.currentUser?.uid
        val path = "users/${uid ?: "unauthenticated"}"
        return try {
            val userId = requireUserId()
            val user = auth.currentUser
            val docRef = db.collection("users").document(userId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = (user?.displayName?.takeIf { it.isNotBlank() } ?: "Music Creator").take(80)
                val safeEmail = (user?.email ?: "").take(120)
                val payload = mutableMapOf<String, Any?>(
                    "userId" to userId,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "photoUrl" to user?.photoUrl?.toString()?.take(500),
                    "role" to "user",
                    "blocked" to false,
                    "credits" to 100,
                    "preferredLanguage" to "English",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
                val created = docRef.get().await().toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(id = userId, userId = userId, displayName = safeName, email = safeEmail)
                Result.success(created)
            } else {
                val profile = existing.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(id = userId, userId = userId)
                Result.success(profile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { handleStreamError(it, OperationType.GET, path, userId) }
        )
    }

    suspend fun updateUserProfileSettings(displayName: String, preferredLanguage: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid"
        return try {
            val userId = requireUserId()
            val cleanName = displayName.trim().ifBlank { "Music Creator" }.take(80)
            val cleanLang = preferredLanguage.trim().ifBlank { "English" }.take(30)
            db.collection("users").document(userId).update(
                mapOf(
                    "displayName" to cleanName,
                    "preferredLanguage" to cleanLang,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun spendCredits(newCredits: Int): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid"
        return try {
            val userId = requireUserId()
            db.collection("users").document(userId).update(
                mapOf(
                    "credits" to newCredits.coerceAtLeast(0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Personal Songs ---

    fun observeMySongs(userId: String): Flow<List<SongRecord>> = flow {
        val path = "users/$userId/songs"
        emitAll(
            db.collection("users").document(userId).collection("songs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SongRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    suspend fun saveSong(song: SongRecord): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uid/songs"
        return try {
            val userId = requireUserId()
            val col = db.collection("users").document(userId).collection("songs")
            val docRef = if (song.id.isNotBlank()) col.document(song.id) else col.document()
            path = docRef.path
            val payload = mutableMapOf<String, Any?>(
                "userId" to userId,
                "title" to song.title.trim().ifBlank { "Untitled AI Track" }.take(120),
                "recipientName" to song.recipientName.trim().take(60),
                "prompt" to song.prompt.take(1000),
                "lyrics" to song.lyrics.take(12000),
                "language" to song.language.ifBlank { "English" }.take(40),
                "genre" to song.genre.ifBlank { "Pop" }.take(50),
                "style" to song.style.take(80),
                "mood" to song.mood.ifBlank { "Energetic" }.take(40),
                "voiceType" to song.voiceType.ifBlank { "Male" }.take(40),
                "beat" to song.beat.take(60),
                "instruments" to song.instruments.take(160),
                "durationSec" to song.durationSec.coerceIn(10, 600),
                "bpm" to song.bpm.coerceIn(40, 220),
                "musicKey" to song.musicKey.ifBlank { "Am" }.take(30),
                "chordProgression" to song.chordProgression.take(16),
                "coverType" to (song.coverType.takeIf { it in listOf("dj", "classic", "romantic", "custom") } ?: "custom"),
                "localAudioPath" to song.localAudioPath?.take(300),
                "localVideoPath" to song.localVideoPath?.take(300),
                "localCoverPath" to song.localCoverPath?.take(300),
                "favorite" to song.favorite,
                "downloaded" to song.downloaded,
                "playCount" to song.playCount.coerceAtLeast(0),
                "visibility" to (song.visibility.takeIf { it in listOf("private", "public") } ?: "private"),
                "variationIndex" to song.variationIndex.coerceIn(1, 10),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSongPartial(songId: String, updates: Map<String, Any>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/songs/$songId"
        return try {
            val userId = requireUserId()
            require(songId.isNotBlank()) { "Song ID must not be blank" }
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(userId).collection("songs").document(songId)
                .update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSong(songId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/songs/$songId"
        return try {
            val userId = requireUserId()
            db.collection("users").document(userId).collection("songs").document(songId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Playlists ---

    fun observePlaylists(userId: String): Flow<List<PlaylistRecord>> = flow {
        val path = "users/$userId/playlists"
        emitAll(
            db.collection("users").document(userId).collection("playlists")
                .snapshots()
                .map { snap ->
                    snap.toObjects(PlaylistRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    suspend fun createPlaylist(name: String, description: String, songIds: List<String> = emptyList()): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uid/playlists"
        return try {
            val userId = requireUserId()
            val docRef = db.collection("users").document(userId).collection("playlists").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to userId,
                    "name" to name.trim().ifBlank { "My Playlist" }.take(80),
                    "description" to description.trim().take(250),
                    "songIds" to songIds.take(100),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePlaylistSongs(playlistId: String, songIds: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/playlists/$playlistId"
        return try {
            val userId = requireUserId()
            db.collection("users").document(userId).collection("playlists").document(playlistId)
                .update(
                    mapOf(
                        "songIds" to songIds.distinct().take(100),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/playlists/$playlistId"
        return try {
            val userId = requireUserId()
            db.collection("users").document(userId).collection("playlists").document(playlistId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Chat History ---

    fun observeChatMessages(userId: String): Flow<List<ChatMessageRecord>> = flow {
        val path = "users/$userId/chatMessages"
        emitAll(
            db.collection("users").document(userId).collection("chatMessages")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessageRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: 0L }
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    suspend fun addChatMessage(
        sender: String,
        text: String,
        songId: String? = null,
        songTitle: String? = null,
        suggestions: List<String> = emptyList()
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uid/chatMessages"
        return try {
            val userId = requireUserId()
            val docRef = db.collection("users").document(userId).collection("chatMessages").document()
            path = docRef.path
            val payload = mutableMapOf<String, Any?>(
                "userId" to userId,
                "sender" to (sender.takeIf { it in listOf("user", "ai") } ?: "user"),
                "text" to text.trim().ifBlank { "..." }.take(8000),
                "songId" to songId?.take(128),
                "songTitle" to songTitle?.take(120),
                "suggestions" to suggestions.take(8),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val path = "users/$uid/chatMessages"
        return try {
            val userId = requireUserId()
            val docs = db.collection("users").document(userId).collection("chatMessages").get().await()
            val batch = db.batch()
            docs.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Public & Featured Songs ---

    fun observePublicSongs(userId: String): Flow<List<PublicSongRecord>> = flow {
        val path = "songs_public"
        emitAll(
            db.collection("songs_public")
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snap ->
                    snap.toObjects(PublicSongRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    suspend fun publishSongToCommunity(song: SongRecord, creatorName: String): Result<String> {
        val path = "songs_public"
        return try {
            val userId = requireUserId()
            val docId = if (song.id.isNotBlank()) "pub_${song.id}" else db.collection("songs_public").document().id
            val docRef = db.collection("songs_public").document(docId)
            val payload = mapOf(
                "userId" to userId,
                "creatorName" to creatorName.ifBlank { "Creator" }.take(80),
                "title" to song.title.ifBlank { "AI Creation" }.take(120),
                "recipientName" to song.recipientName.take(60),
                "lyrics" to song.lyrics.take(12000),
                "language" to song.language.ifBlank { "English" }.take(40),
                "genre" to song.genre.ifBlank { "Pop" }.take(50),
                "style" to song.style.take(80),
                "mood" to song.mood.ifBlank { "Energetic" }.take(40),
                "voiceType" to song.voiceType.ifBlank { "Male" }.take(40),
                "instruments" to song.instruments.take(160),
                "durationSec" to song.durationSec.coerceIn(10, 600),
                "bpm" to song.bpm.coerceIn(40, 220),
                "musicKey" to song.musicKey.ifBlank { "Am" }.take(30),
                "chordProgression" to song.chordProgression.take(16),
                "coverType" to (song.coverType.takeIf { it in listOf("dj", "classic", "romantic", "custom") } ?: "custom"),
                "visibility" to "public",
                "featuredSection" to "none",
                "playCount" to 0,
                "likeCount" to 0,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun likePublicSong(song: PublicSongRecord): Result<Unit> {
        val path = "songs_public/${song.id}"
        return try {
            requireUserId()
            db.collection("songs_public").document(song.id).update(
                mapOf(
                    "likeCount" to song.likeCount + 1,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Categories ---

    fun observeActiveCategories(userId: String): Flow<List<CategoryRecord>> = flow {
        val path = "categories"
        emitAll(
            db.collection("categories")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CategoryRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.sortOrder }
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    // --- Reports ---

    suspend fun submitReport(songId: String, songTitle: String, reason: String): Result<Unit> {
        val path = "reports"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("reports").document()
            docRef.set(
                mapOf(
                    "reporterId" to uid,
                    "songId" to songId.take(128),
                    "songTitle" to songTitle.ifBlank { "Unknown Song" }.take(120),
                    "reason" to reason.trim().ifBlank { "Inappropriate content" }.take(500),
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- App Settings ---

    fun observeAppSettings(userId: String): Flow<AppSettingsRecord?> = flow {
        val path = "app_settings/config"
        emitAll(
            db.collection("app_settings").document("config")
                .snapshots()
                .map { snap ->
                    snap.toObject(AppSettingsRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { handleStreamError(it, OperationType.GET, path, userId) }
        )
    }

    // --- Admin Operations ---

    fun isCurrentUserAdmin(): Boolean {
        val email = auth.currentUser?.email ?: return false
        return email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
    }

    fun observeAllUsersForAdmin(userId: String): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    fun observeAllReportsForAdmin(userId: String): Flow<List<ReportRecord>> = flow {
        val path = "reports"
        emitAll(
            db.collection("reports")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ReportRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { handleStreamError(it, OperationType.LIST, path, userId) }
        )
    }

    suspend fun adminUpdateUser(targetUid: String, blocked: Boolean, credits: Int, role: String): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            requireUserId()
            db.collection("users").document(targetUid).update(
                mapOf(
                    "blocked" to blocked,
                    "credits" to credits.coerceIn(0, 1_000_000),
                    "role" to (role.takeIf { it in listOf("user", "admin") } ?: "user"),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateFeaturedSong(songId: String, featuredSection: String, visibility: String): Result<Unit> {
        val path = "songs_public/$songId"
        return try {
            requireUserId()
            db.collection("songs_public").document(songId).update(
                mapOf(
                    "featuredSection" to featuredSection,
                    "visibility" to visibility,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminPublishFeaturedSong(song: PublicSongRecord): Result<String> {
        val path = "songs_public"
        return try {
            val uid = requireUserId()
            val docRef = if (song.id.isNotBlank()) db.collection("songs_public").document(song.id)
            else db.collection("songs_public").document()
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "creatorName" to song.creatorName.ifBlank { "AI Studio Admin" }.take(80),
                    "title" to song.title.ifBlank { "Featured Track" }.take(120),
                    "recipientName" to song.recipientName.take(60),
                    "lyrics" to song.lyrics.take(12000),
                    "language" to song.language.ifBlank { "English" }.take(40),
                    "genre" to song.genre.ifBlank { "DJ Remix" }.take(50),
                    "style" to song.style.take(80),
                    "mood" to song.mood.ifBlank { "Energetic" }.take(40),
                    "voiceType" to song.voiceType.ifBlank { "Male" }.take(40),
                    "instruments" to song.instruments.take(160),
                    "durationSec" to song.durationSec.coerceIn(10, 600),
                    "bpm" to song.bpm.coerceIn(40, 220),
                    "musicKey" to song.musicKey.ifBlank { "Am" }.take(30),
                    "chordProgression" to song.chordProgression.take(16),
                    "coverType" to (song.coverType.takeIf { it in listOf("dj", "classic", "romantic", "custom") } ?: "dj"),
                    "visibility" to "public",
                    "featuredSection" to song.featuredSection.ifBlank { "featured" },
                    "playCount" to song.playCount,
                    "likeCount" to song.likeCount,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminAddCategory(name: String, group: String, sortOrder: Int = 10): Result<Unit> {
        val path = "categories"
        return try {
            requireUserId()
            val docRef = db.collection("categories").document()
            docRef.set(
                mapOf(
                    "name" to name.trim().ifBlank { "Custom Genre" }.take(60),
                    "group" to (group.takeIf { it in listOf("DJ", "NORMAL", "CLASSIC", "OTHER", "CUSTOM") } ?: "CUSTOM"),
                    "active" to true,
                    "sortOrder" to sortOrder.coerceIn(0, 1000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteCategory(categoryId: String): Result<Unit> {
        val path = "categories/$categoryId"
        return try {
            requireUserId()
            db.collection("categories").document(categoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun adminResolveReport(reportId: String, status: String): Result<Unit> {
        val path = "reports/$reportId"
        return try {
            requireUserId()
            db.collection("reports").document(reportId).update(
                mapOf(
                    "status" to (status.takeIf { it in listOf("open", "resolved", "dismissed") } ?: "resolved"),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminSaveAppSettings(settings: AppSettingsRecord): Result<Unit> {
        val path = "app_settings/config"
        return try {
            requireUserId()
            db.collection("app_settings").document("config").set(
                mapOf(
                    "providerName" to settings.providerName.ifBlank { "Firebase AI Logic" }.take(80),
                    "externalMusicApiUrl" to settings.externalMusicApiUrl.trim().take(300),
                    "externalMusicApiEnabled" to settings.externalMusicApiEnabled,
                    "externalVideoApiEnabled" to settings.externalVideoApiEnabled,
                    "defaultCredits" to settings.defaultCredits.coerceIn(0, 10000),
                    "maintenanceMode" to settings.maintenanceMode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
