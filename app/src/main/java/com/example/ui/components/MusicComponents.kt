package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PlayerState
import com.example.data.model.SongRecord
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassElevated
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.MagentaPulse
import com.example.ui.theme.MusicGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = GlassBorder,
    backgroundColor: Color = GlassSurface.copy(alpha = 0.85f),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier
        ),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 4.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun CoverArtwork(
    coverType: String,
    localCoverPath: String? = null,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val localBitmap = remember(localCoverPath) {
        localCoverPath?.let { path ->
            val f = File(path)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }
    }
    if (localBitmap != null) {
        Image(
            bitmap = localBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        val resId = when (coverType.lowercase()) {
            "dj" -> R.drawable.img_cover_dj
            "classic" -> R.drawable.img_cover_classic
            "romantic" -> R.drawable.img_cover_romantic
            else -> R.drawable.img_cover_dj
        }
        Image(
            painter = painterResource(id = resId),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun SpectrumVisualizerCanvas(
    bars: List<Float>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    accentBrush: Brush = MusicGradients.primaryBrand
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spectrum")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(modifier = modifier) {
        val count = bars.size.coerceAtLeast(1)
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)
        bars.forEachIndexed { idx, value ->
            val factor = if (isPlaying) (value * shimmer).coerceIn(0.12f, 1f) else 0.14f
            val h = size.height * factor
            val x = idx * (barWidth + gap)
            val y = (size.height - h) / 2f
            drawRoundRect(
                brush = accentBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun MiniPlayerBar(
    playerState: PlayerState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playerState.currentSong ?: return
    val progress = if (playerState.durationMs > 0) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenPlayer)
            .testTag("mini_player_bar"),
        shape = RoundedCornerShape(18.dp),
        color = GlassElevated.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.45f)),
        shadowElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArtwork(
                    coverType = song.coverType,
                    localCoverPath = song.localCoverPath,
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playerState.statusMessage ?: "${song.genre} • ${song.mood}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (playerState.preparing) NeonCyan else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (playerState.preparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp).padding(4.dp),
                        color = NeonCyan,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier.size(48.dp).testTag("mini_player_toggle")
                    ) {
                        Icon(
                            imageVector = if (playerState.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.playing) "Pause" else "Play",
                            tint = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(48.dp).testTag("mini_player_next")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next track",
                            tint = NeonCyan
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { if (playerState.preparing) playerState.synthesisProgress else progress },
                modifier = Modifier.fillMaxWidth().height(2.5.dp),
                color = NeonCyan,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
fun SongDownloadAndActionChips(
    song: SongRecord,
    onDownloadWav: () -> Unit,
    onDownloadVideo: () -> Unit,
    onDownloadCover: () -> Unit,
    onDownloadLyrics: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMakeVideo: () -> Unit
) {
    val audioReady = song.localAudioPath?.let { File(it).exists() } == true
    val videoReady = song.localVideoPath?.let { File(it).exists() } == true
    val coverReady = song.localCoverPath?.let { File(it).exists() } == true
    val lyricsReady = song.lyrics.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (audioReady) {
                ActionChipButton(
                    icon = Icons.Default.AudioFile,
                    label = "Save WAV",
                    tint = NeonCyan,
                    onClick = onDownloadWav,
                    modifier = Modifier.weight(1f)
                )
            }
            if (videoReady) {
                ActionChipButton(
                    icon = Icons.Default.VideoFile,
                    label = "Save MP4",
                    tint = MagentaPulse,
                    onClick = onDownloadVideo,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (lyricsReady) {
                ActionChipButton(
                    icon = Icons.Default.Lyrics,
                    label = "Lyrics .txt",
                    tint = ElectricViolet,
                    onClick = onDownloadLyrics,
                    modifier = Modifier.weight(1f)
                )
            }
            if (coverReady) {
                ActionChipButton(
                    icon = Icons.Default.Image,
                    label = "Cover PNG",
                    tint = NeonCyan,
                    onClick = onDownloadCover,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChipButton(
                icon = if (song.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (song.favorite) "Favorited" else "Favorite",
                tint = MagentaPulse,
                onClick = onToggleFavorite,
                modifier = Modifier.weight(1f)
            )
            ActionChipButton(
                icon = Icons.Default.Videocam,
                label = "Music Video",
                tint = ElectricViolet,
                onClick = onMakeVideo,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChipButton(
                icon = Icons.Default.Share,
                label = "Share",
                tint = TextPrimary,
                onClick = onShare,
                modifier = Modifier.weight(1f)
            )
            ActionChipButton(
                icon = Icons.Default.ContentCopy,
                label = "Copy Link",
                tint = TextSecondary,
                onClick = onCopyLink,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActionChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        },
        label = {
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = GlassElevated,
            labelColor = TextPrimary
        ),
        border = BorderStroke(1.dp, GlassBorder)
    )
}
