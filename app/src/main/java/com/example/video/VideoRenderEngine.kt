package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.data.model.SongRecord
import com.example.data.model.VideoStyleOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders real H.264 MP4 Music Videos on-device via Android MediaCodec + MediaMuxer + Canvas Surface.
 */
object VideoRenderEngine {

    private const val WIDTH = 720
    private const val HEIGHT = 1280
    private const val FPS = 24
    private const val BIT_RATE = 2_400_000

    suspend fun renderMusicVideoToMp4(
        context: Context,
        song: SongRecord,
        style: VideoStyleOption,
        overlayText: String,
        showLyrics: Boolean,
        accentHex: Int,
        durationSec: Int,
        coverBitmap: Bitmap?,
        slideshowPhotos: List<Bitmap> = emptyList(),
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.Default) {
        val videoDir = File(context.filesDir, "videos").apply { mkdirs() }
        val safeId = song.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "video_${System.currentTimeMillis()}" }
        val outFile = File(videoDir, "$safeId.mp4")

        val clampedSec = durationSec.coerceIn(8, 30)
        val totalFrames = clampedSec * FPS

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        val lyricLines = song.lyrics.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[") }
            .ifEmpty { listOf(song.title, "${song.genre} • ${song.mood}") }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        try {
            for (frame in 0 until totalFrames) {
                val timeSec = frame.toFloat() / FPS.toFloat()
                val progress = frame.toFloat() / totalFrames.toFloat()

                val canvas: Canvas = inputSurface.lockCanvas(null)
                try {
                    drawVideoFrame(
                        canvas = canvas,
                        frame = frame,
                        timeSec = timeSec,
                        progress = progress,
                        song = song,
                        style = style,
                        overlayText = overlayText,
                        showLyrics = showLyrics,
                        accentHex = accentHex,
                        coverBitmap = coverBitmap,
                        slideshowPhotos = slideshowPhotos,
                        lyricLines = lyricLines,
                        bgPaint = bgPaint,
                        accentPaint = accentPaint,
                        titlePaint = titlePaint,
                        subtitlePaint = subtitlePaint,
                        lyricPaint = lyricPaint
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                drainEncoder(codec, muxer, bufferInfo, endOfStream = false, getTrackIndex = { trackIndex }, setTrackIndex = { idx ->
                    trackIndex = idx
                    muxer.start()
                    muxerStarted = true
                }, isMuxerStarted = { muxerStarted })

                if (frame % 4 == 0) {
                    onProgress((progress * 0.95f).coerceIn(0.05f, 0.95f))
                }
            }

            codec.signalEndOfInputStream()
            drainEncoder(codec, muxer, bufferInfo, endOfStream = true, getTrackIndex = { trackIndex }, setTrackIndex = { idx ->
                trackIndex = idx
                muxer.start()
                muxerStarted = true
            }, isMuxerStarted = { muxerStarted })

            onProgress(1f)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            try { inputSurface.release() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Exception) {}
        }

        outFile
    }

    private fun drawVideoFrame(
        canvas: Canvas,
        frame: Int,
        timeSec: Float,
        progress: Float,
        song: SongRecord,
        style: VideoStyleOption,
        overlayText: String,
        showLyrics: Boolean,
        accentHex: Int,
        coverBitmap: Bitmap?,
        slideshowPhotos: List<Bitmap>,
        lyricLines: List<String>,
        bgPaint: Paint,
        accentPaint: Paint,
        titlePaint: Paint,
        subtitlePaint: Paint,
        lyricPaint: Paint
    ) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val pulse = (0.5f + 0.5f * sin(timeSec * (song.bpm / 60f) * PI).toFloat())

        // Background
        if (style == VideoStyleOption.PHOTO_SLIDESHOW && slideshowPhotos.isNotEmpty()) {
            val photoIdx = ((progress * slideshowPhotos.size).toInt()).coerceIn(0, slideshowPhotos.lastIndex)
            val bmp = slideshowPhotos[photoIdx]
            canvas.drawBitmap(bmp, null, RectF(0f, 0f, w, h), null)
            bgPaint.shader = null
            bgPaint.color = Color.argb(135, 7, 7, 13)
            canvas.drawRect(0f, 0f, w, h, bgPaint)
        } else {
            val topColor = Color.rgb(10, 10, 24)
            val bottomColor = Color.rgb(
                (Color.red(accentHex) * (0.25f + 0.2f * pulse)).toInt().coerceIn(0, 255),
                (Color.green(accentHex) * (0.25f + 0.2f * pulse)).toInt().coerceIn(0, 255),
                (Color.blue(accentHex) * (0.35f + 0.25f * pulse)).toInt().coerceIn(0, 255)
            )
            bgPaint.shader = LinearGradient(0f, 0f, w, h, topColor, bottomColor, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            bgPaint.shader = null
        }

        val cx = w / 2f
        val cy = h * 0.40f

        // Visualizer / Vinyl / Album Art center
        if (style == VideoStyleOption.DJ_VISUALIZER || style == VideoStyleOption.AUDIO_SPECTRUM || style == VideoStyleOption.ANIMATED_MV) {
            for (ring in 1..3) {
                accentPaint.style = Paint.Style.STROKE
                accentPaint.strokeWidth = 6f
                accentPaint.color = Color.argb((140 - ring * 30).coerceAtLeast(30), Color.red(accentHex), Color.green(accentHex), Color.blue(accentHex))
                val radius = 155f + ring * 38f + pulse * 26f
                canvas.drawCircle(cx, cy, radius, accentPaint)
            }
        }

        // Cover art / spinning disc
        if (coverBitmap != null) {
            canvas.save()
            if (style == VideoStyleOption.ALBUM_COVER || style == VideoStyleOption.DJ_VISUALIZER) {
                canvas.rotate((frame * 1.5f) % 360f, cx, cy)
            }
            val artSize = 310f + pulse * 14f
            val dst = RectF(cx - artSize / 2, cy - artSize / 2, cx + artSize / 2, cy + artSize / 2)
            canvas.drawBitmap(coverBitmap, Rect(0, 0, coverBitmap.width, coverBitmap.height), dst, null)
            canvas.restore()
        } else {
            accentPaint.style = Paint.Style.FILL
            accentPaint.color = Color.argb(180, Color.red(accentHex), Color.green(accentHex), Color.blue(accentHex))
            canvas.drawCircle(cx, cy, 145f + pulse * 14f, accentPaint)
        }

        // Spectrum Bars
        accentPaint.style = Paint.Style.FILL
        val barCount = 28
        val barWidth = 14f
        val gap = 8f
        val totalBarWidth = barCount * (barWidth + gap)
        val startX = (w - totalBarWidth) / 2f
        val baseY = h * 0.72f
        for (b in 0 until barCount) {
            val barNorm = abs(sin(timeSec * (song.bpm / 45f) + b * 0.42f)).toFloat()
            val barH = 26f + barNorm * (if (style == VideoStyleOption.DJ_VISUALIZER) 170f else 120f)
            accentPaint.color = if (b % 2 == 0) accentHex else Color.parseColor("#06B6D4")
            val x = startX + b * (barWidth + gap)
            canvas.drawRoundRect(RectF(x, baseY - barH, x + barWidth, baseY), 8f, 8f, accentPaint)
        }

        // Title & Overlay Text
        canvas.drawText(song.title.take(28), cx, 130f, titlePaint)
        val tagText = overlayText.ifBlank { "${song.genre} • ${song.mood} • ${song.bpm} BPM" }
        canvas.drawText(tagText.take(40), cx, 178f, subtitlePaint)

        // Synced Lyrics
        if (showLyrics && lyricLines.isNotEmpty()) {
            val lineIndex = ((progress * lyricLines.size).toInt()).coerceIn(0, lyricLines.lastIndex)
            val currentLine = lyricLines[lineIndex]
            val nextLine = lyricLines.getOrNull(lineIndex + 1).orEmpty()
            lyricPaint.color = Color.WHITE
            canvas.drawText(currentLine.take(36), cx, h * 0.83f, lyricPaint)
            if (nextLine.isNotBlank()) {
                subtitlePaint.color = Color.parseColor("#94A3B8")
                canvas.drawText(nextLine.take(40), cx, h * 0.88f, subtitlePaint)
            }
        }

        // Watermark / Studio pill
        subtitlePaint.color = Color.parseColor("#8B5CF6")
        canvas.drawText("AI MUSIC WORLD • ${style.label.uppercase()}", cx, h - 68f, subtitlePaint)
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        getTrackIndex: () -> Int,
        setTrackIndex: (Int) -> Unit,
        isMuxerStarted: () -> Boolean
    ) {
        val timeoutUs = 10_000L
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!isMuxerStarted()) {
                        val newFormat = codec.outputFormat
                        setTrackIndex(muxer.addTrack(newFormat))
                    }
                }
                outputIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputIndex)
                    if (encodedData != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0 && isMuxerStarted()) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(getTrackIndex(), encodedData, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }
            }
        }
    }
}
