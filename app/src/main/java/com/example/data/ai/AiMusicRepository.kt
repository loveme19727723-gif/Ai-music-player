package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ChatMessageRecord
import com.example.data.model.GeneratedSongDraft
import com.example.data.model.SongRecord
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiMusicRepository"
private const val TEXT_MODEL = "gemini-3.8-flash"
private const val IMAGE_MODEL = "gemini-nano-banana-2.1"
private const val AI_TIMEOUT_MS = 60_000L

data class ChatSongIntent(
    val action: String, // "chat_only", "generate_song", "regenerate_song", "change_mood", "create_video"
    val recipientName: String,
    val title: String,
    val language: String,
    val genre: String,
    val style: String,
    val mood: String,
    val voiceType: String,
    val beat: String,
    val instruments: String,
    val durationSec: Int,
    val suggestions: List<String>,
    val variationCount: Int = 1
)

class AiMusicRepository {

    private val chatSystemInstruction = content {
        text(
            """
            You are AI Music World Assistant, a creative AI music producer and songwriter.
            Always respond in clear, concise English (unless the user explicitly asks for lyrics or replies in Bengali/Hindi script).
            You naturally understand requests written in English, Bengali (বাংলা), Hindi (हिन्दी), and Banglish (e.g. "Amar name diye DJ gaan banao", "2 toi add kore dao english", "3 tei koro and english a korbe").
            Do NOT keep asking repetitive follow-up questions. Whenever a user asks for a song—even briefly—confirm the musical direction enthusiastically in English right away and proceed with creating the song(s).
            If the user asks for "both", "2 toi", "2 variations", "all 3", "3 tei", "3 variations", or "everything", confirm you are generating all requested variations in English now.
            """.trimIndent()
        )
    }

    private val chatModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = TEXT_MODEL,
            systemInstruction = chatSystemInstruction
        )
    }

    private val songGenerationSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Catchy original song title"),
            "lyrics" to Schema.string(description = "Full original lyrics formatted with [Intro], [Verse 1], [Chorus], [Verse 2], [Bridge], [Outro] sections and featuring the recipient name naturally"),
            "language" to Schema.string(description = "Language of the lyrics"),
            "genre" to Schema.string(description = "Primary genre"),
            "style" to Schema.string(description = "Specific musical style"),
            "mood" to Schema.string(description = "Emotional mood"),
            "voiceType" to Schema.string(description = "Vocal style or Instrumental"),
            "beat" to Schema.string(description = "Beat pattern description"),
            "instruments" to Schema.string(description = "Comma-separated instruments"),
            "bpm" to Schema.integer(description = "Tempo in BPM between 60 and 180"),
            "musicKey" to Schema.string(description = "Musical key e.g. Am, C, Dm, F, Em, G"),
            "chordProgression" to Schema.array(
                items = Schema.string(),
                description = "4 to 8 chord symbols matching the key, e.g. [Am, F, C, G]"
            )
        )
    )

    private val songGeneratorModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = TEXT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = songGenerationSchema
            }
        )
    }

    private val intentSchema = Schema.obj(
        properties = mapOf(
            "action" to Schema.enumeration(
                values = listOf("chat_only", "generate_song", "regenerate_song", "change_mood", "create_video"),
                description = "Whether user wants just conversation, to generate a song now, regenerate/modify an existing song, change mood, or open video generator"
            ),
            "recipientName" to Schema.string(description = "Person name mentioned in conversation or empty string"),
            "title" to Schema.string(description = "Suggested song title"),
            "language" to Schema.string(description = "Detected language: Bengali, Hindi, Banglish, Punjabi, English, or Instrumental"),
            "genre" to Schema.string(description = "Detected music genre e.g. DJ Bass Boost, DJ Remix, Classic Bengali, Romantic, Hip-Hop, Pop"),
            "style" to Schema.string(description = "Detected style e.g. Bengali DJ, Old Bollywood Style, Lo-Fi"),
            "mood" to Schema.string(description = "Detected mood e.g. Energetic, Romantic, Sad, Happy, Party"),
            "voiceType" to Schema.string(description = "Male, Female, Duet, DJ MC, Rapper, or Instrumental"),
            "beat" to Schema.string(description = "Beat style e.g. Fast 140 BPM, Lo-Fi 80 BPM"),
            "instruments" to Schema.string(description = "Key instruments"),
            "durationSec" to Schema.integer(description = "Duration in seconds between 25 and 120"),
            "variationCount" to Schema.integer(description = "Number of variations to generate: 1 by default, 2 if user asks for both / 2 variations / '2 toi', or 3 if user asks for all 3 / 3 variations / '3 tei' / 'sob'"),
            "suggestions" to Schema.array(
                items = Schema.string(),
                description = "3 short clickable follow-up prompts in English"
            )
        )
    )

    private val intentModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = TEXT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = intentSchema
            }
        )
    }

    private val imageModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = IMAGE_MODEL,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    fun streamChatReply(
        history: List<ChatMessageRecord>,
        userMessage: String,
        userDisplayName: String = ""
    ): Flow<String> = flow {
        val sanitizedTurns = sanitizeChatTurns(history.takeLast(14))
        val sdkHistory = sanitizedTurns.map { (role, text) ->
            content(role) { text(text) }
        }
        val chat = chatModel.startChat(history = sdkHistory)
        val contextualPrompt = if (userDisplayName.isNotBlank() && sdkHistory.isEmpty()) {
            "[Signed-in user's name: $userDisplayName]\n$userMessage"
        } else {
            userMessage
        }
        val buffer = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            chat.sendMessageStream(contextualPrompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    buffer.append(part)
                    emit(buffer.toString())
                }
            }
        }
    }

    suspend fun analyzeChatIntent(
        recentMessages: List<ChatMessageRecord>,
        latestUserMessage: String,
        activeSong: SongRecord?,
        userDisplayName: String = ""
    ): Result<ChatSongIntent> {
        return try {
            val conversation = recentMessages.takeLast(8).joinToString("\n") {
                "${if (it.sender == "user") "User" else "AI"}: ${it.text}"
            }
            val songContext = activeSong?.let {
                "Currently active song: title='${it.title}', recipient='${it.recipientName}', genre='${it.genre}', mood='${it.mood}', language='${it.language}'."
            } ?: "No active song selected yet."
            val userContext = if (userDisplayName.isNotBlank()) {
                "Signed-in user's name: '$userDisplayName' (use this if they say 'amar name' / 'my name' without specifying another name)."
            } else ""

            val prompt = """
                Analyze this music assistant conversation and the latest user message.
                $userContext
                $songContext
                Conversation:
                $conversation
                Latest User Message: "$latestUserMessage"
                
                Rules:
                - Do NOT stall with "chat_only" when the user wants a song. For ANY song creation request (even brief requests like "Amar name diye DJ gaan banao" or "Make a DJ song with my name"), set action="generate_song" using sensible studio defaults!
                - Only set action="chat_only" for pure greetings or general questions with no song creation request.
                - If the user asks for "both", "2 toi", or "2 variations", set action="generate_song" and variationCount=2.
                - If the user asks for "all 3", "3 tei", "3 variations", "all styles", or "sob", set action="generate_song" and variationCount=3. Otherwise set variationCount=1.
                - If the user mentions "English", "in English", or "english a korbe", ALWAYS set language="English". Otherwise default to "English" unless Bengali or Hindi lyrics are explicitly requested.
                - If the user asks to make a song more energetic, remix it, regenerate, or try another version, set action="regenerate_song".
                - If the user asks for a sad/happy/romantic version of the same lyrics, set action="change_mood".
                - If the user asks to make a music video ("music video banao", "create video", "visualizer"), set action="create_video".
                - Always provide 3 helpful short follow-up suggestions in English.
            """.trimIndent()

            val response = withTimeout(AI_TIMEOUT_MS) {
                intentModel.generateContent(prompt)
            }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty intent response"))
            val suggestionsArray = json.optJSONArray("suggestions")
            val suggestions = buildList {
                if (suggestionsArray != null) {
                    for (i in 0 until suggestionsArray.length()) {
                        add(suggestionsArray.optString(i))
                    }
                }
            }.filter { it.isNotBlank() }.take(4)

            Result.success(
                ChatSongIntent(
                    action = json.optString("action", "chat_only"),
                    recipientName = json.optString("recipientName", ""),
                    title = json.optString("title", "AI Custom Track"),
                    language = resolveRequestedLanguage(latestUserMessage, json.optString("language", "English")),
                    genre = json.optString("genre", "DJ Bass Boost").ifBlank { "DJ Bass Boost" },
                    style = json.optString("style", "Studio DJ").ifBlank { "Studio DJ" },
                    mood = json.optString("mood", "Energetic").ifBlank { "Energetic" },
                    voiceType = json.optString("voiceType", "Male").ifBlank { "Male" },
                    beat = json.optString("beat", "128 BPM Pulse").ifBlank { "128 BPM Pulse" },
                    instruments = json.optString("instruments", "Synth, 808 Bass, Drums").ifBlank { "Synth, 808 Bass, Drums" },
                    durationSec = json.optInt("durationSec", 36).coerceIn(25, 120),
                    suggestions = suggestions,
                    variationCount = resolveVariationCount(latestUserMessage, json.optInt("variationCount", 1))
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Intent analysis timed out", e)
            Result.failure(Exception("AI analysis timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Intent analysis failed", e)
            Result.failure(e)
        }
    }

    suspend fun generatePersonalizedSong(
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
        variationIndex: Int = 1
    ): Result<GeneratedSongDraft> {
        return try {
            val lyricsInstruction = if (customLyrics.isNotBlank()) {
                "Use and refine these user-provided lyrics without losing their core message (keep [Intro], [Verse 1], [Chorus], [Verse 2], [Bridge], [Outro] tags):\n$customLyrics"
            } else {
                "Write 100% original, catchy song lyrics structured clearly with [Intro], [Verse 1], [Chorus], [Verse 2], [Bridge], [Outro]. Naturally weave the name '${recipientName.ifBlank { "Friend" }}' into the chorus and verses."
            }

            val fullPrompt = """
                Compose a complete personalized AI song blueprint (Variation #$variationIndex).
                User Prompt: $promptText
                Recipient / Featured Name: ${recipientName.ifBlank { "None specified" }}
                Requested Title: ${customTitle.ifBlank { "Create a memorable title" }}
                Language: $language (If Banglish, write Bengali words in natural English/Latin script mixed with modern music hooks; if Bengali, write in Bengali script or requested style; if Hindi, natural Hindi/Hinglish as fitting).
                Genre: $genre
                Style: $style
                Mood: $mood
                Voice Type: $voiceType
                Beat / Groove: $beat
                Instruments: $instruments
                Target Duration: $durationSec seconds
                
                $lyricsInstruction
                
                Also output musical synthesis parameters:
                - bpm: integer matching the genre & mood (e.g., DJ/EDM 126-148, Lo-Fi 72-88, Romantic 78-96, Trap 130-145, Classic 80-108).
                - musicKey: key signature (Am, Dm, Em, C, F, G).
                - chordProgression: list of 4 to 8 chord symbols (e.g. ["Am", "F", "C", "G"]).
            """.trimIndent()

            val response = withTimeout(AI_TIMEOUT_MS) {
                songGeneratorModel.generateContent(fullPrompt)
            }
            val jsonString = response.text ?: throw IllegalStateException("AI model returned empty response.")
            val json = JSONObject(jsonString)

            val chordsArray = json.optJSONArray("chordProgression")
            val chords = buildList {
                if (chordsArray != null) {
                    for (i in 0 until chordsArray.length()) {
                        val c = chordsArray.optString(i)
                        if (c.isNotBlank()) add(c)
                    }
                }
            }.ifEmpty { listOf("Am", "F", "C", "G") }

            val resolvedGenre = json.optString("genre", genre).ifBlank { genre }
            val resolvedMood = json.optString("mood", mood).ifBlank { mood }
            val coverType = when {
                resolvedGenre.contains("DJ", ignoreCase = true) ||
                    resolvedGenre.contains("EDM", ignoreCase = true) ||
                    resolvedGenre.contains("Trap", ignoreCase = true) ||
                    resolvedGenre.contains("Rap", ignoreCase = true) -> "dj"
                resolvedGenre.contains("Classic", ignoreCase = true) ||
                    resolvedGenre.contains("Bollywood", ignoreCase = true) ||
                    resolvedGenre.contains("Devotional", ignoreCase = true) -> "classic"
                resolvedMood.contains("Romantic", ignoreCase = true) ||
                    resolvedGenre.contains("Romantic", ignoreCase = true) ||
                    resolvedGenre.contains("Love", ignoreCase = true) ||
                    resolvedGenre.contains("Lo-Fi", ignoreCase = true) -> "romantic"
                else -> "dj"
            }

            Result.success(
                GeneratedSongDraft(
                    title = customTitle.ifBlank { json.optString("title", "AI Personalized Song") }.take(120),
                    recipientName = recipientName.take(60),
                    prompt = promptText.take(1000),
                    lyrics = json.optString("lyrics", customLyrics).ifBlank { "[Verse 1]\nMusic flows tonight..." },
                    language = json.optString("language", language).ifBlank { language },
                    genre = resolvedGenre,
                    style = json.optString("style", style).ifBlank { style },
                    mood = resolvedMood,
                    voiceType = json.optString("voiceType", voiceType).ifBlank { voiceType },
                    beat = json.optString("beat", beat).ifBlank { beat },
                    instruments = json.optString("instruments", instruments).ifBlank { instruments },
                    durationSec = durationSec.coerceIn(20, 180),
                    bpm = json.optInt("bpm", 124).coerceIn(55, 190),
                    musicKey = json.optString("musicKey", "Am").ifBlank { "Am" },
                    chordProgression = chords,
                    coverType = coverType,
                    variationIndex = variationIndex
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Song generation timed out", e)
            Result.failure(Exception("Song generation timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Song generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun translateLyrics(lyrics: String, targetLanguage: String): Result<String> {
        return try {
            val prompt = """
                Translate and rhythmically adapt these song lyrics into $targetLanguage while preserving the [Intro], [Verse 1], [Chorus], [Verse 2], [Bridge], [Outro] section markers and emotional flow. Output only the translated lyrics:
                
                $lyrics
            """.trimIndent()
            val response = withTimeout(AI_TIMEOUT_MS) {
                chatModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (text.isNullOrBlank()) Result.failure(Exception("Empty translation response."))
            else Result.success(text)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Translation timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            Result.failure(e)
        }
    }

    suspend fun transformSongMood(song: SongRecord, targetMood: String): Result<GeneratedSongDraft> {
        return generatePersonalizedSong(
            recipientName = song.recipientName,
            customTitle = "${song.title} ($targetMood Mix)",
            customLyrics = "",
            promptText = "Keep the core story and person (${song.recipientName}) of these lyrics, but rewrite and re-compose the entire song into a $targetMood mood:\n${song.lyrics}",
            language = song.language,
            genre = song.genre,
            style = "$targetMood ${song.style}".trim(),
            mood = targetMood,
            voiceType = song.voiceType,
            beat = song.beat,
            instruments = song.instruments,
            durationSec = song.durationSec
        )
    }

    suspend fun generatePlaylistSuggestion(moodOrTheme: String, availableTitles: List<String>): Result<Pair<String, String>> {
        return try {
            val prompt = """
                Create a creative playlist name and short 1-sentence description for the theme: "$moodOrTheme".
                Available tracks: ${availableTitles.joinToString(", ")}.
                Format strictly as:
                Title: <playlist name>
                Description: <1 sentence description>
            """.trimIndent()
            val response = withTimeout(AI_TIMEOUT_MS) {
                chatModel.generateContent(prompt)
            }
            val raw = response.text.orEmpty()
            val title = raw.lines().firstOrNull { it.startsWith("Title:", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.ifBlank { "$moodOrTheme Mix" } ?: "$moodOrTheme Mix"
            val desc = raw.lines().firstOrNull { it.startsWith("Description:", ignoreCase = true) }
                ?.substringAfter(":")?.trim()?.ifBlank { "Curated AI playlist for $moodOrTheme vibes." }
                ?: "Curated AI playlist for $moodOrTheme vibes."
            Result.success(title to desc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Playlist suggestion failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateAlbumCover(
        songTitle: String,
        genre: String,
        mood: String,
        customPrompt: String = ""
    ): Result<Bitmap> {
        return try {
            val prompt = buildString {
                append("Create a striking square music album cover artwork for a song titled '$songTitle'. ")
                append("Genre: $genre. Mood: $mood. ")
                if (customPrompt.isNotBlank()) append("Visual theme: $customPrompt. ")
                append("Modern Spotify luxury aesthetic, atmospheric dark background, vibrant lighting, high artistic detail, no text or watermarks.")
            }
            val bitmap = withTimeout(AI_TIMEOUT_MS) {
                val response = imageModel.generateContent(prompt)
                response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()?.image
            }
            if (bitmap != null) Result.success(bitmap)
            else Result.failure(Exception("No image was returned by the model. Ensure Firebase Blaze billing is active for Imagen/Nano Banana."))
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Album cover generation timed out after 60 seconds."))
        } catch (e: QuotaExceededException) {
            Result.failure(Exception("Image generation quota exceeded. Please try again later."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Album cover generation failed", e)
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Ensures Gemini multi-turn chat history alternates cleanly (starts with "user", ends with "model")
         * and merges consecutive turns of the same role to prevent HTTP 400 / SDK ordering failures.
         */
        internal fun sanitizeChatTurns(messages: List<ChatMessageRecord>): List<Pair<String, String>> {
            val merged = mutableListOf<Pair<String, String>>()
            for (msg in messages) {
                val text = msg.text.trim()
                if (text.isEmpty()) continue
                val role = if (msg.sender == "user") "user" else "model"
                val prev = merged.lastOrNull()
                if (prev != null && prev.first == role) {
                    merged[merged.lastIndex] = role to "${prev.second}\n$text"
                } else {
                    merged.add(role to text)
                }
            }
            while (merged.isNotEmpty() && merged.first().first != "user") {
                merged.removeAt(0)
            }
            if (merged.isNotEmpty() && merged.last().first == "user") {
                merged.removeAt(merged.lastIndex)
            }
            return merged
        }

        internal fun resolveVariationCount(userMessage: String, aiCount: Int): Int {
            val lower = userMessage.lowercase()
            val requestsThree = lower.contains("3 tei") ||
                lower.contains("3tei") ||
                lower.contains("all 3") ||
                lower.contains("all three") ||
                lower.contains("3 variations") ||
                lower.contains("3 songs") ||
                lower.contains("sob")
            val requestsTwo = lower.contains("2 toi") ||
                lower.contains("2toi") ||
                lower.contains("both") ||
                lower.contains("2 variations") ||
                lower.contains("2 songs")
            return when {
                requestsThree -> 3
                requestsTwo -> 2
                else -> aiCount.coerceIn(1, 3)
            }
        }

        internal fun resolveRequestedLanguage(userMessage: String, aiLanguage: String): String {
            val lower = userMessage.lowercase()
            return when {
                lower.contains("english") -> "English"
                lower.contains("hindi") -> "Hindi"
                lower.contains("punjabi") -> "Punjabi"
                lower.contains("instrumental") -> "Instrumental"
                lower.contains("bengali") || lower.contains("bangla") -> "Bengali"
                aiLanguage.isBlank() -> "English"
                else -> aiLanguage
            }
        }
    }
}
