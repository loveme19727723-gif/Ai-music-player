package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.PlayerState
import com.example.data.model.RepeatMode
import com.example.data.model.SongRecord
import com.example.ui.components.CoverArtwork
import com.example.ui.components.GlassCard
import com.example.ui.components.SongDownloadAndActionChips
import com.example.ui.components.SpectrumVisualizerCanvas
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.MusicGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.DownloadExportHelper

@Composable
fun PlayerScreen(
    playerState: PlayerState,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSelectQueueSong: (SongRecord) -> Unit,
    onRemoveQueueSong: (String) -> Unit,
    onDownloadWav: (SongRecord) -> Unit,
    onDownloadVideo: (SongRecord) -> Unit,
    onDownloadCover: (SongRecord) -> Unit,
    onDownloadLyrics: (SongRecord) -> Unit,
    onToggleFavorite: (SongRecord) -> Unit,
    onMakeVideo: (SongRecord) -> Unit
) {
    val context = LocalContext.current
    val song = playerState.currentSong
    var showLyricsPanel by remember { mutableStateOf(false) }
    var showQueuePanel by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicGradients.darkBackdrop),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize player", tint = TextPrimary)
                }
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
                Row {
                    IconButton(
                        onClick = { showLyricsPanel = !showLyricsPanel; showQueuePanel = false },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Lyrics,
                            contentDescription = "Toggle lyrics",
                            tint = if (showLyricsPanel) NeonCyan else TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { showQueuePanel = !showQueuePanel; showLyricsPanel = false },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Toggle queue",
                            tint = if (showQueuePanel) NeonCyan else TextSecondary
                        )
                    }
                }
            }
        }

        if (song == null) {
            item {
                Text(
                    "No track active. Pick a song from Home, Explore, or AI Chat.",
                    color = TextSecondary,
                    modifier = Modifier.padding(24.dp)
                )
            }
            return@LazyColumn
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CoverArtwork(
                    coverType = song.coverType,
                    localCoverPath = song.localCoverPath,
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(28.dp))
                )
                Spacer(Modifier.height(18.dp))
                SpectrumVisualizerCanvas(
                    bars = playerState.spectrumBars,
                    isPlaying = playerState.playing,
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(song.title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${song.genre} • ${song.mood} • ${song.bpm} BPM • ${song.musicKey}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan,
                    textAlign = TextAlign.Center
                )
                if (song.recipientName.isNotBlank() || song.instruments.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    val subDetails = listOfNotNull(
                        song.recipientName.takeIf { it.isNotBlank() }?.let { "Made for $it" },
                        song.instruments.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    Text(
                        subDetails,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                if (playerState.statusMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(playerState.statusMessage, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }

                Spacer(Modifier.height(16.dp))
                val duration = playerState.durationMs.coerceAtLeast(1L)
                val pos = playerState.positionMs.coerceIn(0L, duration)
                Slider(
                    value = pos.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = ElectricViolet)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMillis(pos), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(formatMillis(duration), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.shuffle) NeonCyan else TextSecondary
                        )
                    }
                    IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = TextPrimary, modifier = Modifier.size(34.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MusicGradients.primaryBrand),
                        contentAlignment = Alignment.Center
                    ) {
                        if (playerState.preparing) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = TextPrimary, strokeWidth = 2.5.dp)
                        } else {
                            IconButton(
                                onClick = onTogglePlay,
                                modifier = Modifier.fillMaxSize().testTag("player_play_pause")
                            ) {
                                Icon(
                                    imageVector = if (playerState.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playerState.playing) "Pause" else "Play",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = TextPrimary, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = onCycleRepeat, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (playerState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat mode",
                            tint = if (playerState.repeatMode != RepeatMode.OFF) NeonCyan else TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume", tint = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = playerState.volume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = TextPrimary, activeTrackColor = NeonCyan)
                    )
                }
            }
        }

        if (showLyricsPanel) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Lyrics", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        Text(song.lyrics.ifBlank { "Instrumental track" }, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                }
            }
        }

        if (showQueuePanel) {
            item {
                Text(
                    "Up Next Queue (${playerState.queue.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(playerState.queue, key = { it.id.ifBlank { it.title } }) { qSong ->
                GlassCard(
                    onClick = { onSelectQueueSong(qSong) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            qSong.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (qSong.id == song.id) NeonCyan else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemoveQueueSong(qSong.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove from queue", tint = TextSecondary)
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                SongDownloadAndActionChips(
                    song = song,
                    onDownloadWav = { onDownloadWav(song) },
                    onDownloadVideo = { onDownloadVideo(song) },
                    onDownloadCover = { onDownloadCover(song) },
                    onDownloadLyrics = { onDownloadLyrics(song) },
                    onShare = { DownloadExportHelper.shareSongText(context, song) },
                    onCopyLink = { DownloadExportHelper.copySongLink(context, song) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onMakeVideo = { onMakeVideo(song) }
                )
            }
        }
    }
}

private fun formatMillis(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
