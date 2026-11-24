package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.SongRecord
import com.example.data.model.VideoStyleOption
import com.example.ui.components.GlassCard
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MagentaPulse
import com.example.ui.theme.MusicGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.StudioUiState
import com.example.util.DownloadExportHelper
import java.io.File

@Composable
fun VideoStudioScreen(
    song: SongRecord?,
    studioState: StudioUiState,
    onBack: () -> Unit,
    onRenderVideo: (
        song: SongRecord,
        style: VideoStyleOption,
        overlayText: String,
        showLyrics: Boolean,
        accentHex: Int,
        durationSec: Int,
        photos: List<Bitmap>
    ) -> Unit,
    onDownloadMp4: (SongRecord) -> Unit
) {
    val context = LocalContext.current
    var selectedStyle by remember { mutableStateOf(VideoStyleOption.DJ_VISUALIZER) }
    var overlayText by remember(song?.id) { mutableStateOf(song?.let { "${it.genre} • ${it.mood}" } ?: "") }
    var showLyrics by remember { mutableStateOf(true) }
    var durationSec by remember { mutableFloatStateOf(12f) }
    var selectedAccent by remember { mutableIntStateOf(android.graphics.Color.parseColor("#8B5CF6")) }
    var slideshowBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris: List<Uri> ->
        val loaded = uris.mapNotNull { uri ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = false
                    }.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (_: Exception) {
                null
            }
        }
        slideshowBitmaps = loaded
    }

    val accentOptions = listOf(
        "Electric Violet" to android.graphics.Color.parseColor("#8B5CF6"),
        "Neon Cyan" to android.graphics.Color.parseColor("#06B6D4"),
        "Magenta Pulse" to android.graphics.Color.parseColor("#EC4899"),
        "Classic Gold" to android.graphics.Color.parseColor("#F59E0B")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicGradients.darkBackdrop),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Music Video Generator", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(song?.title ?: "Select a song first", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                }
            }
        }

        if (song == null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("No Song Selected", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text("Generate or select a song from My Music or AI Chat to render its MP4 music video.", color = TextSecondary)
                    }
                }
            }
            return@LazyColumn
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Video Format & Style", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                    VideoStyleOption.entries.forEach { option ->
                        FilterChip(
                            selected = selectedStyle == option,
                            onClick = { selectedStyle = option },
                            label = {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(option.label, style = MaterialTheme.typography.titleSmall)
                                    Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricViolet.copy(alpha = 0.35f))
                        )
                    }

                    if (selectedStyle == VideoStyleOption.PHOTO_SLIDESHOW) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pick Slideshow Photos (${slideshowBitmaps.size} selected)")
                        }
                    }

                    Text("2. Background Accent & Effects", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(accentOptions) { (label, hex) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedAccent = hex }
                                    .padding(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    label,
                                    color = if (selectedAccent == hex) NeonCyan else TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = overlayText,
                        onValueChange = { overlayText = it },
                        label = { Text("Custom Video Overlay Text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show Synced Lyrics Overlay", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Switch(checked = showLyrics, onCheckedChange = { showLyrics = it })
                    }

                    Text("Video Duration: ${durationSec.toInt()} seconds", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    Slider(
                        value = durationSec,
                        onValueChange = { durationSec = it },
                        valueRange = 8f..25f
                    )

                    Button(
                        onClick = {
                            onRenderVideo(
                                song,
                                selectedStyle,
                                overlayText,
                                showLyrics,
                                selectedAccent,
                                durationSec.toInt(),
                                slideshowBitmaps
                            )
                        },
                        enabled = !studioState.renderingVideo,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("render_mp4_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (studioState.renderingVideo) "Rendering MP4..." else "Render MP4 Music Video")
                    }

                    if (studioState.renderingVideo) {
                        LinearProgressIndicator(
                            progress = { studioState.videoProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                            color = NeonCyan
                        )
                        Text(studioState.videoStatus.orEmpty(), style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                    }
                }
            }
        }

        val videoFile = studioState.renderedVideoPath?.let { File(it) }?.takeIf { it.exists() }
        if (videoFile != null) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    borderColor = EmeraldLive
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Video Ready (${videoFile.length() / 1024} KB)", style = MaterialTheme.typography.titleMedium, color = EmeraldLive)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val mc = MediaController(ctx)
                                        mc.setAnchorView(this)
                                        setMediaController(mc)
                                        setVideoPath(videoFile.absolutePath)
                                        start()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onDownloadMp4(song.copy(localVideoPath = videoFile.absolutePath)) },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("download_mp4_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MagentaPulse)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Download MP4")
                            }
                            OutlinedButton(
                                onClick = { DownloadExportHelper.shareFile(context, videoFile, "video/mp4", song.title) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                border = BorderStroke(1.dp, GlassBorder)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Share MP4")
                            }
                        }
                    }
                }
            }
        }
    }
}
