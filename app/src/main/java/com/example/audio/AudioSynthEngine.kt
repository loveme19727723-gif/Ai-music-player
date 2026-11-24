package com.example.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Real 16-bit 44.1kHz Stereo PCM WAV Audio Synthesizer.
 * Converts AI-composed BPM, musical key, chord progression, genre, instruments, and mood
 * into genuine multi-track playable & downloadable WAV audio files.
 */
object AudioSynthEngine {

    private const val SAMPLE_RATE = 44100
    private const val CHANNELS = 2

    private val noteFreqMap = mapOf(
        "C" to 261.63, "C#" to 277.18, "Db" to 277.18,
        "D" to 293.66, "D#" to 311.13, "Eb" to 311.13,
        "E" to 329.63, "F" to 349.23, "F#" to 369.99, "Gb" to 369.99,
        "G" to 392.00, "G#" to 415.30, "Ab" to 415.30,
        "A" to 440.00, "A#" to 466.16, "Bb" to 466.16,
        "B" to 493.88
    )

    suspend fun synthesizeSongToWav(
        context: Context,
        songId: String,
        title: String,
        recipientName: String,
        lyrics: String,
        genre: String,
        mood: String,
        voiceType: String,
        bpm: Int,
        chordProgression: List<String>,
        durationSec: Int,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.Default) {
        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val safeId = songId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "track_${System.currentTimeMillis()}" }
        val outFile = File(audioDir, "$safeId.wav")

        // Optional brief vocal tag/intro via Android TTS if voiceType != Instrumental
        val vocalSamples: ShortArray? = if (!voiceType.equals("Instrumental", ignoreCase = true)) {
            val tagLine = buildString {
                if (recipientName.isNotBlank()) append("$recipientName! ")
                val firstLine = lyrics.lines()
                    .firstOrNull { it.isNotBlank() && !it.trim().startsWith("[") }
                    ?.take(60)
                if (!firstLine.isNullOrBlank()) append(firstLine)
            }.trim()
            if (tagLine.isNotBlank()) {
                synthesizeTtsPcm(context, tagLine)
            } else null
        } else null

        return@withContext synthesizeSongToFile(
            outFile = outFile,
            title = title,
            genre = genre,
            mood = mood,
            bpm = bpm,
            chordProgression = chordProgression,
            durationSec = durationSec,
            vocalSamples = vocalSamples,
            onProgress = onProgress
        )
    }

    suspend fun synthesizeSongToFile(
        outFile: File,
        title: String,
        genre: String,
        mood: String,
        bpm: Int,
        chordProgression: List<String>,
        durationSec: Int,
        vocalSamples: ShortArray? = null,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.Default) {

        val clampedDuration = durationSec.coerceIn(18, 90)
        val totalSamples = SAMPLE_RATE * clampedDuration
        val effectiveBpm = bpm.coerceIn(60, 175)
        val beatDurationSec = 60.0 / effectiveBpm
        val samplesPerBeat = (SAMPLE_RATE * beatDurationSec).toInt().coerceAtLeast(1)
        val samplesPerStep = (samplesPerBeat / 4).coerceAtLeast(1) // 16th note grid

        val isDj = genre.contains("DJ", ignoreCase = true) ||
            genre.contains("EDM", ignoreCase = true) ||
            genre.contains("Trap", ignoreCase = true) ||
            genre.contains("Rap", ignoreCase = true) ||
            genre.contains("Hip-Hop", ignoreCase = true)
        val isClassic = genre.contains("Classic", ignoreCase = true) ||
            genre.contains("Acoustic", ignoreCase = true) ||
            genre.contains("Devotional", ignoreCase = true) ||
            genre.contains("Folk", ignoreCase = true)
        val isSadOrLofi = mood.contains("Sad", ignoreCase = true) ||
            mood.contains("Chill", ignoreCase = true) ||
            genre.contains("Lo-Fi", ignoreCase = true) ||
            mood.contains("Soft", ignoreCase = true)

        val chords = chordProgression.ifEmpty { listOf("Am", "F", "C", "G") }.map { parseChord(it) }
        val rng = Random(title.hashCode() xor effectiveBpm)

        val leftBuffer = DoubleArray(totalSamples)
        val rightBuffer = DoubleArray(totalSamples)

        // Chunked synthesis for clean progress reporting
        val chunkSize = SAMPLE_RATE // 1 second per progress tick
        var chordIndex = 0
        var prevStep = -1

        for (start in 0 until totalSamples step chunkSize) {
            val end = minOf(start + chunkSize, totalSamples)
            for (i in start until end) {
                val t = i.toDouble() / SAMPLE_RATE
                val step = i / samplesPerStep
                val stepPosSec = (i % samplesPerStep).toDouble() / SAMPLE_RATE
                val beat = i / samplesPerBeat
                val beatPosSec = (i % samplesPerBeat).toDouble() / SAMPLE_RATE
                val bar = beat / 4
                chordIndex = bar % chords.size
                val chord = chords[chordIndex]
                val rootBassFreq = chord.first() / 4.0 // Deep bass octave

                if (step != prevStep) {
                    prevStep = step
                }

                // 1. Drums (Kick, Snare/Clap, Hi-Hat)
                var drumSample = 0.0
                val stepInBar = step % 16

                // Kick
                val hasKick = when {
                    isDj -> stepInBar % 4 == 0 || (genre.contains("Trap", true) && (stepInBar == 6 || stepInBar == 10))
                    isClassic -> stepInBar == 0 || stepInBar == 6 || stepInBar == 10
                    else -> stepInBar == 0 || stepInBar == 8 || stepInBar == 10
                }
                if (hasKick) {
                    val kickEnv = exp(-stepPosSec * if (isDj) 14.0 else 18.0)
                    val kickFreq = (if (isDj) 135.0 else 105.0) * exp(-stepPosSec * 24.0) + 42.0
                    drumSample += sin(2.0 * PI * kickFreq * stepPosSec) * kickEnv * (if (isDj) 0.72 else 0.45)
                }

                // Snare / Clap / Tabla Tap
                val hasSnare = stepInBar == 4 || stepInBar == 12
                if (hasSnare) {
                    val snEnv = exp(-stepPosSec * 22.0)
                    val noise = (rng.nextDouble() * 2.0 - 1.0)
                    val body = sin(2.0 * PI * (if (isClassic) 240.0 else 185.0) * stepPosSec)
                    drumSample += (noise * 0.45 + body * 0.55) * snEnv * (if (isSadOrLofi) 0.25 else 0.42)
                }

                // Hi-Hat / Shaker
                val hasHat = if (isDj) stepInBar % 2 == 1 || stepInBar % 4 == 2 else stepInBar % 2 == 0
                if (hasHat) {
                    val hatEnv = exp(-stepPosSec * 55.0)
                    val noise = (rng.nextDouble() * 2.0 - 1.0)
                    drumSample += noise * hatEnv * (if (isDj) 0.16 else 0.09)
                }

                // 2. Sub Bass / 808 Bassline
                val bassPatternActive = if (isDj) stepInBar % 2 == 0 else stepInBar in listOf(0, 4, 8, 12)
                val bassEnv = if (bassPatternActive) exp(-stepPosSec * (if (isDj) 4.2 else 3.0)) else 0.2
                val subWave = sin(2.0 * PI * rootBassFreq * t) + 0.3 * sin(2.0 * PI * rootBassFreq * 2.0 * t)
                val bassGain = if (isDj) 0.45 else 0.28
                val bassSample = subWave * bassEnv * bassGain

                // 3. Chord Pad / Keys / Plucked Strings
                var chordL = 0.0
                var chordR = 0.0
                val barPosSec = (i % (samplesPerBeat * 4)).toDouble() / SAMPLE_RATE
                val padEnv = (1.0 - exp(-barPosSec * 6.0)) * exp(-barPosSec * if (isClassic) 0.8 else 0.45)
                chord.forEachIndexed { idx, freq ->
                    val phase = 2.0 * PI * freq * t
                    val tone = when {
                        isDj -> (sin(phase) + 0.45 * sin(phase * 2.0) + 0.2 * sin(phase * 3.0)) * 0.35
                        isClassic -> sin(phase) + 0.35 * sin(phase * 2.0) * exp(-barPosSec * 2.5)
                        else -> sin(phase) + 0.2 * sin(phase * 2.0)
                    }
                    val pan = if (idx % 2 == 0) 0.65 else 0.35
                    chordL += tone * padEnv * pan
                    chordR += tone * padEnv * (1.0 - pan)
                }

                // 4. Arpeggio / Lead Melody
                val arpIndex = stepInBar % chord.size
                val leadFreq = chord[arpIndex] * (if (isClassic) 1.5 else 2.0)
                val arpEnv = exp(-stepPosSec * (if (isClassic) 9.0 else 12.0))
                val leadTone = if (isClassic) {
                    // Sitar/plucked harmonic shimmer
                    (sin(2.0 * PI * leadFreq * t) + 0.45 * sin(4.0 * PI * leadFreq * t) + 0.25 * sin(6.0 * PI * leadFreq * t)) * 0.22
                } else {
                    (sin(2.0 * PI * leadFreq * t) + 0.3 * sin(4.0 * PI * leadFreq * t)) * 0.20
                }
                val leadSample = leadTone * arpEnv

                // 5. Optional Vocal Name Shoutout / Intro Overlay (subtle ducking)
                var vocalSample = 0.0
                if (vocalSamples != null) {
                    val introStart = SAMPLE_RATE * 2
                    val chorusStart = SAMPLE_RATE * (clampedDuration / 2)
                    val vIdx1 = i - introStart
                    val vIdx2 = i - chorusStart
                    if (vIdx1 in vocalSamples.indices) {
                        vocalSample += (vocalSamples[vIdx1] / 32768.0) * 0.72
                    } else if (vIdx2 in vocalSamples.indices) {
                        vocalSample += (vocalSamples[vIdx2] / 32768.0) * 0.72
                    }
                }

                // Master fade in/out + warm saturation limiter
                val fadeIn = (t / 0.8).coerceIn(0.0, 1.0)
                val fadeOut = ((clampedDuration - t) / 1.8).coerceIn(0.0, 1.0)
                val masterEnv = fadeIn * fadeOut

                val mixL = tanh((drumSample + bassSample + chordL * 0.28 + leadSample * 0.9 + vocalSample) * masterEnv * 0.85)
                val mixR = tanh((drumSample + bassSample + chordR * 0.28 + leadSample * 1.1 + vocalSample) * masterEnv * 0.85)

                leftBuffer[i] = mixL.coerceIn(-0.98, 0.98)
                rightBuffer[i] = mixR.coerceIn(-0.98, 0.98)
            }
            onProgress(((end.toFloat() / totalSamples.toFloat()) * 0.9f).coerceIn(0.05f, 0.9f))
        }

        // Write 16-bit PCM WAV file
        writeStereoWav(outFile, leftBuffer, rightBuffer)
        onProgress(1f)
        outFile
    }

    private fun writeStereoWav(outFile: File, left: DoubleArray, right: DoubleArray) {
        val numSamples = left.size
        val dataSize = numSamples * CHANNELS * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // PCM chunk size
            putShort(1) // PCM format
            putShort(CHANNELS.toShort())
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * CHANNELS * 2)
            putShort((CHANNELS * 2).toShort())
            putShort(16) // Bits per sample
            put("data".toByteArray())
            putInt(dataSize)
        }.array()

        FileOutputStream(outFile).use { fos ->
            fos.write(header)
            val pcmBytes = ByteArray(4096 * 4)
            var byteIdx = 0
            for (i in 0 until numSamples) {
                val l = (left[i] * 32767.0).toInt().coerceIn(-32768, 32767)
                val r = (right[i] * 32767.0).toInt().coerceIn(-32768, 32767)
                pcmBytes[byteIdx++] = (l and 0xFF).toByte()
                pcmBytes[byteIdx++] = ((l shr 8) and 0xFF).toByte()
                pcmBytes[byteIdx++] = (r and 0xFF).toByte()
                pcmBytes[byteIdx++] = ((r shr 8) and 0xFF).toByte()
                if (byteIdx == pcmBytes.size) {
                    fos.write(pcmBytes)
                    byteIdx = 0
                }
            }
            if (byteIdx > 0) {
                fos.write(pcmBytes, 0, byteIdx)
            }
        }
    }

    private fun parseChord(symbol: String): List<Double> {
        val clean = symbol.trim()
        if (clean.isEmpty()) return listOf(220.0, 261.63, 329.63)
        val root = if (clean.length >= 2 && (clean[1] == '#' || clean[1] == 'b')) {
            clean.substring(0, 2)
        } else {
            clean.substring(0, 1)
        }
        val base = noteFreqMap[root] ?: 220.0
        val isMinor = clean.contains("m", ignoreCase = false) && !clean.contains("maj", ignoreCase = true)
        val thirdRatio = if (isMinor) 1.1892 else 1.2599
        val fifthRatio = 1.4983
        return listOf(base, base * thirdRatio, base * fifthRatio)
    }

    private suspend fun synthesizeTtsPcm(context: Context, text: String): ShortArray? {
        return try {
            val tempWav = File(context.cacheDir, "tts_tag_${System.nanoTime()}.wav")
            val success = withTimeoutOrNull(3_500L) {
                suspendCancellableCoroutine { cont ->
                    var tts: TextToSpeech? = null
                    tts = TextToSpeech(context.applicationContext) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            tts?.language = Locale.US
                            tts?.setSpeechRate(1.05f)
                            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}
                                override fun onDone(utteranceId: String?) {
                                    tts?.shutdown()
                                    if (cont.isActive) cont.resume(true)
                                }
                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) {
                                    tts?.shutdown()
                                    if (cont.isActive) cont.resume(false)
                                }
                            })
                            val res = tts?.synthesizeToFile(text, null, tempWav, "tag_utt")
                            if (res != TextToSpeech.SUCCESS) {
                                tts?.shutdown()
                                if (cont.isActive) cont.resume(false)
                            }
                        } else {
                            tts?.shutdown()
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                    cont.invokeOnCancellation { tts.shutdown() }
                }
            } ?: false
            if (!success || !tempWav.exists() || tempWav.length() <= 44) {
                tempWav.delete()
                return null
            }
            val raw = tempWav.readBytes()
            tempWav.delete()
            // Parse WAV header sample rate and resample simply to 44.1kHz mono ShortArray
            val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            val srcRate = if (raw.size >= 28) buf.getInt(24).coerceIn(8000, 48000) else 22050
            val srcChannels = if (raw.size >= 24) buf.getShort(22).toInt().coerceIn(1, 2) else 1
            val dataOffset = 44
            val frameCount = (raw.size - dataOffset) / (2 * srcChannels)
            if (frameCount <= 0) return null
            val ratio = SAMPLE_RATE.toDouble() / srcRate.toDouble()
            val outLen = (frameCount * ratio).toInt().coerceAtMost(SAMPLE_RATE * 8)
            val out = ShortArray(outLen)
            for (i in 0 until outLen) {
                val srcIdx = (i / ratio).toInt().coerceIn(0, frameCount - 1)
                val bytePos = dataOffset + srcIdx * 2 * srcChannels
                if (bytePos + 1 < raw.size) {
                    out[i] = ByteBuffer.wrap(raw, bytePos, 2).order(ByteOrder.LITTLE_ENDIAN).short
                }
            }
            out
        } catch (_: Exception) {
            null
        }
    }
}
