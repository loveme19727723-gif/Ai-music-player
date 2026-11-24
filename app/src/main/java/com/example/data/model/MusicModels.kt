package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val BOOTSTRAPPED_ADMIN_EMAIL = "chapchup481@gmail.com"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val role: String = "user",
    val blocked: Boolean = false,
    val credits: Int = 100,
    val preferredLanguage: String = "English",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isAdminUser: Boolean
        get() = role == "admin" || email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
}

@Keep
@IgnoreExtraProperties
data class SongRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val recipientName: String = "",
    val prompt: String = "",
    val lyrics: String = "",
    val language: String = "English",
    val genre: String = "Pop",
    val style: String = "",
    val mood: String = "Energetic",
    val voiceType: String = "Male",
    val beat: String = "Modern Groove",
    val instruments: String = "Synth, Bass, Drums",
    val durationSec: Int = 45,
    val bpm: Int = 120,
    val musicKey: String = "Am",
    val chordProgression: List<String> = listOf("Am", "F", "C", "G"),
    val coverType: String = "custom",
    val localAudioPath: String? = null,
    val localVideoPath: String? = null,
    val localCoverPath: String? = null,
    val favorite: Boolean = false,
    val downloaded: Boolean = false,
    val playCount: Int = 0,
    val visibility: String = "private",
    val variationIndex: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PlaylistRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val songIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = "user",
    val text: String = "",
    val songId: String? = null,
    val songTitle: String? = null,
    val suggestions: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicSongRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val creatorName: String = "",
    val title: String = "",
    val recipientName: String = "",
    val lyrics: String = "",
    val language: String = "English",
    val genre: String = "DJ Remix",
    val style: String = "",
    val mood: String = "Energetic",
    val voiceType: String = "Male",
    val instruments: String = "Synth, 808 Bass, Percussion",
    val durationSec: Int = 45,
    val bpm: Int = 128,
    val musicKey: String = "Am",
    val chordProgression: List<String> = listOf("Am", "F", "C", "G"),
    val coverType: String = "dj",
    val visibility: String = "public",
    val featuredSection: String = "none",
    val playCount: Int = 0,
    val likeCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toSongRecord(): SongRecord = SongRecord(
        id = id,
        userId = userId,
        title = title,
        recipientName = recipientName,
        prompt = "$genre • $style • $mood",
        lyrics = lyrics,
        language = language,
        genre = genre,
        style = style,
        mood = mood,
        voiceType = voiceType,
        beat = "$bpm BPM Studio Groove",
        instruments = instruments,
        durationSec = durationSec,
        bpm = bpm,
        musicKey = musicKey,
        chordProgression = chordProgression,
        coverType = coverType,
        playCount = playCount,
        visibility = visibility,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@Keep
@IgnoreExtraProperties
data class CategoryRecord(
    @DocumentId val id: String = "",
    val name: String = "",
    val group: String = "OTHER",
    val active: Boolean = true,
    val sortOrder: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReportRecord(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val songId: String = "",
    val songTitle: String = "",
    val reason: String = "",
    val status: String = "open",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppSettingsRecord(
    @DocumentId val id: String = "",
    val providerName: String = "Firebase AI Logic + Studio DSP Synth",
    val externalMusicApiUrl: String = "",
    val externalMusicApiEnabled: Boolean = false,
    val externalVideoApiEnabled: Boolean = false,
    val defaultCredits: Int = 100,
    val maintenanceMode: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class GeneratedSongDraft(
    val title: String,
    val recipientName: String,
    val prompt: String,
    val lyrics: String,
    val language: String,
    val genre: String,
    val style: String,
    val mood: String,
    val voiceType: String,
    val beat: String,
    val instruments: String,
    val durationSec: Int,
    val bpm: Int,
    val musicKey: String,
    val chordProgression: List<String>,
    val coverType: String,
    val variationIndex: Int = 1
)

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerState(
    val currentSong: SongRecord? = null,
    val queue: List<SongRecord> = emptyList(),
    val currentIndex: Int = -1,
    val playing: Boolean = false,
    val preparing: Boolean = false,
    val synthesisProgress: Float = 0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.9f,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val spectrumBars: List<Float> = List(24) { 0.15f },
    val statusMessage: String? = null
)

data class FeaturedArtist(
    val name: String,
    val specialty: String,
    val language: String,
    val coverType: String,
    val samplePrompt: String
)

enum class VideoStyleOption(val label: String, val subtitle: String) {
    ANIMATED_MV("Animated Music Video", "Dynamic 3D waveform & neon particle pulses"),
    LYRICS_VIDEO("Lyrics Video", "Synced karaoke verse & chorus typography"),
    DJ_VISUALIZER("DJ Visualizer", "Club bass rings & high-energy strobe equalizer"),
    AUDIO_SPECTRUM("Audio Spectrum", "Precision studio spectrum bars & BPM clock"),
    CINEMATIC_VIDEO("Cinematic Video", "Atmospheric widescreen lens flares & glow"),
    PHOTO_SLIDESHOW("Photo Slideshow", "Your selected gallery photos with smooth fades"),
    ALBUM_COVER("Album Cover Video", "Spinning vinyl cover art & radial aura")
}

object CatalogDefaults {
    val djCategories = listOf(
        "DJ Remix", "DJ Dance", "DJ Bass Boost", "DJ Party", "DJ Trap", "DJ EDM", "DJ Mashup"
    )
    val normalCategories = listOf(
        "Normal Songs", "Pop", "Acoustic", "Soft Music", "Folk", "Modern Songs"
    )
    val classicCategories = listOf(
        "Classic Bengali", "Classic Hindi", "Old Bollywood Style", "Classical Indian", "Instrumental Classical"
    )
    val otherCategories = listOf(
        "Romantic", "Sad", "Love", "Breakup", "Hip-Hop", "Rap", "Trap", "Lo-Fi",
        "Rock", "Punjabi", "Bollywood", "Devotional", "Birthday", "Friendship",
        "Motivational", "Cinematic", "Instrumental"
    )

    val allCategoryGroups: Map<String, List<String>> = linkedMapOf(
        "DJ Music" to djCategories,
        "Normal Music" to normalCategories,
        "Classic Music" to classicCategories,
        "Other Music" to otherCategories
    )

    val languages = listOf("English", "Bengali", "Hindi", "Banglish", "Punjabi", "Instrumental")
    val moods = listOf("Energetic", "Romantic", "Happy", "Sad", "Chill", "Epic", "Devotional", "Party", "Nostalgic")
    val voiceTypes = listOf("Male", "Female", "Duet", "DJ MC", "Rapper", "Instrumental")
    val beats = listOf("Fast 140 BPM", "Club 128 BPM", "Trap 808", "Lo-Fi 78 BPM", "Classic Tabla", "Acoustic Strum", "Cinematic Pulse")
    val instrumentsList = listOf(
        "808 Sub Bass, Synth Lead, Electronic Drums",
        "Grand Piano, Violin, Soft Pad",
        "Sitar, Tabla, Bansuri Flute",
        "Acoustic Guitar, Warm Bass, Cajon",
        "Electric Guitar, Live Drums, Synth",
        "Dhol, Punjabi Tumbi, Trap Beat"
    )

    val featuredArtists = listOf(
        FeaturedArtist("DJ NovaX", "Bengali & EDM Bass Boost", "Banglish", "dj", "Fast Beat, Bass Boost, Bengali DJ party track"),
        FeaturedArtist("RagaVibe AI", "Classic Bengali & Sitar Fusion", "Bengali", "classic", "Soulful classic Bengali song with sitar and piano"),
        FeaturedArtist("Aria Echo", "Midnight Romantic & Lo-Fi", "Hindi", "romantic", "Soft romantic Hindi acoustic love song"),
        FeaturedArtist("MC Volt", "Desi Rap & Trap Battles", "English", "dj", "Hard-hitting hip-hop rap anthem with 808 bass")
    )

    val showcaseSongs: List<PublicSongRecord> = listOf(
        PublicSongRecord(
            id = "showcase_dj_1",
            userId = "studio_official",
            creatorName = "DJ NovaX",
            title = "Neon Kolkata Bass Drop",
            recipientName = "Party Crew",
            lyrics = "[Intro]\nTurn the bass up high tonight\nKolkata lights are shining bright\n\n[Verse 1]\nFeel the rhythm in the air\nHands up everywhere\nSubwoofer shaking up the floor\nEverybody wants some more\n\n[Chorus]\nDrop the beat, let the laser glow\nFast tempo, steal the show\nBass boost pumping in our veins\nDancing through the midnight rains\n\n[Outro]\nAI Music World in the mix!",
            language = "Banglish",
            genre = "DJ Bass Boost",
            style = "Bengali DJ",
            mood = "Energetic",
            voiceType = "DJ MC",
            instruments = "808 Sub Bass, Supersaw Synth, Club Kick",
            durationSec = 36,
            bpm = 138,
            musicKey = "Am",
            chordProgression = listOf("Am", "F", "C", "G"),
            coverType = "dj",
            visibility = "public",
            featuredSection = "featured",
            playCount = 1420,
            likeCount = 380
        ),
        PublicSongRecord(
            id = "showcase_classic_1",
            userId = "studio_official",
            creatorName = "RagaVibe AI",
            title = "Moner Manush Raag",
            recipientName = "Shilpi",
            lyrics = "[Intro]\nSitar strings whisper in twilight gold\n\n[Verse 1]\nSondhya belay nodir tire\nHawa bole jay moner kotha dhire\nPurono surer mayay bhora gaan\nChuye jay ajo amader pran\n\n[Chorus]\nTumi amar chirochena sur\nBaje hridoye bohudur\nClassic banglar chowa niye\nGaan geye jai tomay niye\n\n[Outro]\nSoft tabla fade into moonlight.",
            language = "Bengali",
            genre = "Classic Bengali",
            style = "Classical Fusion",
            mood = "Nostalgic",
            voiceType = "Female",
            instruments = "Sitar, Tabla, Grand Piano",
            durationSec = 36,
            bpm = 86,
            musicKey = "Dm",
            chordProgression = listOf("Dm", "Bb", "C", "Am"),
            coverType = "classic",
            visibility = "public",
            featuredSection = "classic",
            playCount = 980,
            likeCount = 290
        ),
        PublicSongRecord(
            id = "showcase_romantic_1",
            userId = "studio_official",
            creatorName = "Aria Echo",
            title = "Midnight Aurora Love",
            recipientName = "Aarav",
            lyrics = "[Intro]\nWarm Rhodes chords under velvet stars\n\n[Verse 1]\nTeri baaton mein ek sukoon sa hai\nHar lamha ab toh junoon sa hai\nDheere dheere chalti hai yeh raat\nHaathon mein jab ho tera haath\n\n[Chorus]\nTumse hi roshan hai yeh jahaan\nDil ki dhadkan mein tera nishaan\nRomantic sitaron ki chhaon mein\nKho jaayein hum pyar ke gaon mein\n\n[Outro]\nSoft cosmic hum...",
            language = "Hindi",
            genre = "Romantic",
            style = "Midnight Acoustic Lo-Fi",
            mood = "Romantic",
            voiceType = "Duet",
            instruments = "Electric Piano, Acoustic Guitar, Soft Pad",
            durationSec = 36,
            bpm = 82,
            musicKey = "F",
            chordProgression = listOf("F", "Dm", "Bb", "C"),
            coverType = "romantic",
            visibility = "public",
            featuredSection = "romantic",
            playCount = 1210,
            likeCount = 415
        ),
        PublicSongRecord(
            id = "showcase_trending_1",
            userId = "studio_official",
            creatorName = "MC Volt",
            title = "Cyberpunk Trap Anthem",
            recipientName = "Champion",
            lyrics = "[Intro]\n808s locked and loaded\n\n[Verse 1]\nStepping in the booth with the neon crown\nEvery single beat gonna shake the town\nSynthwave echoes from the skyline view\nEverything we build is original and new\n\n[Chorus]\nRise up, turn the volume to the sky\nWatch the spectrum glow as the bass flies by\nNo limits when the sound takes flight\nWe own the studio tonight\n\n[Outro]\nEcho out on the sub drop.",
            language = "English",
            genre = "DJ Trap",
            style = "Cyberpunk Trap",
            mood = "Epic",
            voiceType = "Rapper",
            instruments = "808 Bass, Trap Hi-Hats, Brass Synth",
            durationSec = 36,
            bpm = 132,
            musicKey = "Em",
            chordProgression = listOf("Em", "C", "G", "D"),
            coverType = "dj",
            visibility = "public",
            featuredSection = "trending",
            playCount = 1650,
            likeCount = 510
        ),
        PublicSongRecord(
            id = "showcase_editors_1",
            userId = "studio_official",
            creatorName = "Studio Symphony",
            title = "Golden Horizon Bollywood",
            recipientName = "Meera",
            lyrics = "[Intro]\nOrchestral strings rise with dawn\n\n[Verse 1]\nNaya savera lekar aaya geet\nHar dil mein goonje pyari si preet\nFlute ki dhun par jhoome hawa\nHar pal lage ab bilkul naya\n\n[Chorus]\nGaate chalo yeh zindagi ka raag\nDil mein jala ke khushiyon ka chiraag\n\n[Outro]\nWarm strings resolve.",
            language = "Hindi",
            genre = "Old Bollywood Style",
            style = "Golden Era Classic",
            mood = "Happy",
            voiceType = "Male",
            instruments = "Bansuri Flute, Strings, Acoustic Tabla",
            durationSec = 36,
            bpm = 96,
            musicKey = "C",
            chordProgression = listOf("C", "Am", "F", "G"),
            coverType = "classic",
            visibility = "public",
            featuredSection = "editors_choice",
            playCount = 870,
            likeCount = 260
        )
    )
}
