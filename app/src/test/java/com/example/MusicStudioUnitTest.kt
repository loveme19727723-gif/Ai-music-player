package com.example

import com.example.audio.AudioSynthEngine
import com.example.data.ai.AiMusicRepository
import com.example.data.model.BOOTSTRAPPED_ADMIN_EMAIL
import com.example.data.model.CatalogDefaults
import com.example.data.model.ChatMessageRecord
import com.example.data.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory

class MusicStudioUnitTest {

    @Test
    fun `catalog defaults include 35 plus music genres and showcase songs`() {
        val totalGenres = CatalogDefaults.djCategories.size +
            CatalogDefaults.normalCategories.size +
            CatalogDefaults.classicCategories.size +
            CatalogDefaults.otherCategories.size
        assertTrue("Expected at least 35 genres, got $totalGenres", totalGenres >= 35)
        assertTrue(CatalogDefaults.djCategories.contains("DJ Bass Boost"))
        assertTrue(CatalogDefaults.classicCategories.contains("Classic Bengali"))
        assertTrue(CatalogDefaults.showcaseSongs.isNotEmpty())
        assertTrue(CatalogDefaults.featuredArtists.isNotEmpty())
    }

    @Test
    fun `admin user detection matches role or bootstrapped admin email`() {
        val adminByEmail = UserProfile(userId = "u1", email = BOOTSTRAPPED_ADMIN_EMAIL, role = "user")
        val regularUser = UserProfile(userId = "u2", email = "creator@example.com", role = "user")
        assertTrue(adminByEmail.isAdminUser)
        assertFalse(regularUser.isAdminUser)
    }

    @Test
    fun `public song converts cleanly to personal song record`() {
        val showcase = CatalogDefaults.showcaseSongs.first()
        val converted = showcase.toSongRecord()
        assertEquals(showcase.id, converted.id)
        assertEquals(showcase.title, converted.title)
        assertEquals(showcase.bpm, converted.bpm)
        assertEquals(showcase.chordProgression, converted.chordProgression)
    }

    @Test
    fun `audio synth engine writes valid 44100hz stereo PCM wav file`() = runTest {
        val tempDir = createTempDirectory("wav_test").toFile()
        val outFile = File(tempDir, "aneek_dj.wav")
        try {
            AudioSynthEngine.synthesizeSongToFile(
                outFile = outFile,
                title = "Aneek Bass Drop",
                genre = "DJ Bass Boost",
                mood = "Energetic",
                bpm = 136,
                chordProgression = listOf("Am", "F", "C", "G"),
                durationSec = 18
            )

            assertTrue("WAV file should exist", outFile.exists())
            val bytes = outFile.readBytes()
            val expectedSize = 44 + (18 * 44100 * 2 * 2)
            assertEquals(expectedSize, bytes.size)
            assertEquals("RIFF", String(bytes.copyOfRange(0, 4)))
            assertEquals("WAVE", String(bytes.copyOfRange(8, 12)))
            val sampleRate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            assertEquals(44100, sampleRate)
            // Verify non-silent PCM samples exist in the middle of the track
            val midSlice = bytes.copyOfRange(bytes.size / 2, (bytes.size / 2) + 200)
            assertTrue("Synthesized audio should contain non-zero samples", midSlice.any { it.toInt() != 0 })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `chat history sanitizer drops leading model turn merges consecutive roles and drops trailing user turn`() {
        val raw = listOf(
            ChatMessageRecord(sender = "ai", text = "Welcome!"),
            ChatMessageRecord(sender = "user", text = "Amar name diye DJ gaan banao"),
            ChatMessageRecord(sender = "ai", text = "What beat style would you like?"),
            ChatMessageRecord(sender = "user", text = "Fast Beat"),
            ChatMessageRecord(sender = "user", text = "Bass Boost, Bengali DJ")
        )
        val sanitized = AiMusicRepository.sanitizeChatTurns(raw)
        assertEquals(2, sanitized.size)
        assertEquals("user" to "Amar name diye DJ gaan banao", sanitized[0])
        assertEquals("model" to "What beat style would you like?", sanitized[1])
    }

    @Test
    fun `chat intent helpers enforce multi-variation and English language when requested`() {
        assertEquals(3, AiMusicRepository.resolveVariationCount("3 tei koro and english a korbe", 1))
        assertEquals(3, AiMusicRepository.resolveVariationCount("Create all 3 variations", 1))
        assertEquals(2, AiMusicRepository.resolveVariationCount("2 toi add kore dao english", 1))
        assertEquals(2, AiMusicRepository.resolveVariationCount("Make both versions in English", 1))
        assertEquals(1, AiMusicRepository.resolveVariationCount("Make one energetic DJ song", 1))
        assertEquals("English", AiMusicRepository.resolveRequestedLanguage("3 tei koro and english a korbe", "Banglish"))
        assertEquals("English", AiMusicRepository.resolveRequestedLanguage("2 toi add kore dao english", "Banglish"))
        assertEquals("English", AiMusicRepository.resolveRequestedLanguage("Make a track for me", ""))
        assertEquals("Hindi", AiMusicRepository.resolveRequestedLanguage("Make a romantic Hindi song", "English"))
    }
}
