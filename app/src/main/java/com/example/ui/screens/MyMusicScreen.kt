package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PlaylistRecord
import com.example.data.model.SongRecord
import com.example.data.model.UiState
import com.example.ui.components.CoverArtwork
import com.example.ui.components.GlassCard
import com.example.ui.components.SongDownloadAndActionChips
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassElevated
import com.example.ui.theme.MagentaPulse
import com.example.ui.theme.MusicGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.DownloadExportHelper
import java.io.File

@Composable
fun MyMusicScreen(
    mySongsState: UiState<List<SongRecord>>,
    playlistsState: UiState<List<PlaylistRecord>>,
    onPlaySong: (SongRecord, List<SongRecord>) -> Unit,
    onEditSong: (SongRecord) -> Unit,
    onRenameSong: (SongRecord, String) -> Unit,
    onDeleteSong: (SongRecord) -> Unit,
    onToggleFavorite: (SongRecord) -> Unit,
    onPublishPublic: (SongRecord) -> Unit,
    onMakePrivate: (SongRecord) -> Unit,
    onMakeVideo: (SongRecord) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onGenerateAiPlaylist: (String) -> Unit,
    onAddSongToPlaylist: (PlaylistRecord, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onDownloadWav: (SongRecord) -> Unit,
    onDownloadVideo: (SongRecord) -> Unit,
    onDownloadCover: (SongRecord) -> Unit,
    onDownloadLyrics: (SongRecord) -> Unit
) {
    val context = LocalContext.current
    val songs = (mySongsState as? UiState.Success)?.data.orEmpty()
    val playlists = (playlistsState as? UiState.Success)?.data.orEmpty()

    val tabs = listOf(
        "My Songs", "My Lyrics", "My Videos", "My Playlists",
        "My Favorites", "My Downloads", "Recently Played", "History"
    )
    var selectedTab by remember { mutableStateOf("My Songs") }
    var renameTarget by remember { mutableStateOf<SongRecord?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var playlistDialogTarget by remember { mutableStateOf<SongRecord?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    var aiPlaylistMood by remember { mutableStateOf("") }
    var expandedSongId by remember { mutableStateOf<String?>(null) }

    val displayedSongs = remember(songs, selectedTab) {
        when (selectedTab) {
            "My Lyrics" -> songs.filter { it.lyrics.isNotBlank() }
            "My Videos" -> songs.filter { it.localVideoPath?.let { p -> File(p).exists() } == true }
            "My Favorites" -> songs.filter { it.favorite }
            "My Downloads" -> songs.filter { it.downloaded || it.localAudioPath?.let { p -> File(p).exists() } == true }
            "Recently Played" -> songs.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
            else -> songs
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicGradients.darkBackdrop),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("My Music Library", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("${songs.size} creations • ${playlists.size} playlists", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricViolet)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (selectedTab == "My Playlists") {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("AI Playlist Generator", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = aiPlaylistMood,
                                onValueChange = { aiPlaylistMood = it },
                                placeholder = { Text("Mood/Theme e.g. Late Night Bengali DJ") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (aiPlaylistMood.isNotBlank()) {
                                        onGenerateAiPlaylist(aiPlaylistMood.trim())
                                        aiPlaylistMood = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("AI Mix")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newPlaylistName,
                                onValueChange = { newPlaylistName = it },
                                placeholder = { Text("Or create custom playlist...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    onCreatePlaylist(newPlaylistName.trim(), "Personal playlist")
                                    newPlaylistName = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("Add")
                            }
                        }
                    }
                }
            }

            if (playlists.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "No playlists yet. Generate an AI playlist above!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(playlists, key = { it.id }) { pl ->
                    val plSongs = songs.filter { it.id in pl.songIds }
                    GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pl.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("${pl.songIds.size} songs • ${pl.description}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            if (plSongs.isNotEmpty()) {
                                IconButton(onClick = { onPlaySong(plSongs.first(), plSongs) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play playlist", tint = NeonCyan)
                                }
                            }
                            IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete playlist", tint = CrimsonError)
                            }
                        }
                    }
                }
            }
        } else {
            if (displayedSongs.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Nothing in $selectedTab yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text("Use AI Chat or Personalized Song Studio to generate your first track.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            } else {
                items(displayedSongs, key = { it.id.ifBlank { it.title } }) { song ->
                    val expanded = expandedSongId == song.id
                    GlassCard(
                        onClick = { expandedSongId = if (expanded) null else song.id },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CoverArtwork(
                                    coverType = song.coverType,
                                    localCoverPath = song.localCoverPath,
                                    contentDescription = song.title,
                                    modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${song.genre} • ${song.mood} • ${song.language}", style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (song.visibility == "public") EmeraldLive.copy(alpha = 0.2f) else GlassElevated
                                        ) {
                                            Text(
                                                if (song.visibility == "public") "PUBLIC" else "PRIVATE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (song.visibility == "public") EmeraldLive else TextSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (song.playCount > 0) {
                                            Text("${song.playCount} plays", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                        }
                                    }
                                }
                                IconButton(onClick = { onToggleFavorite(song) }) {
                                    Icon(
                                        imageVector = if (song.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = MagentaPulse
                                    )
                                }
                                IconButton(onClick = { onPlaySong(song, displayedSongs) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = NeonCyan)
                                }
                            }

                            if (expanded) {
                                Spacer(Modifier.height(10.dp))
                                if (song.lyrics.isNotBlank()) {
                                    Text(
                                        text = song.lyrics.take(280) + if (song.lyrics.length > 280) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(onClick = { onEditSong(song) }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Studio")
                                    }
                                    OutlinedButton(
                                        onClick = { renameTarget = song; renameValue = song.title },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Rename")
                                    }
                                    OutlinedButton(
                                        onClick = { playlistDialogTarget = song },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("List")
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { if (song.visibility == "public") onMakePrivate(song) else onPublishPublic(song) },
                                        modifier = Modifier.weight(1f),
                                        border = BorderStroke(1.dp, GlassBorder)
                                    ) {
                                        Icon(
                                            if (song.visibility == "public") Icons.Default.Lock else Icons.Default.Public,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (song.visibility == "public") "Make Private" else "Share Public")
                                    }
                                    OutlinedButton(
                                        onClick = { onDeleteSong(song) },
                                        modifier = Modifier.weight(1f),
                                        border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Delete", color = CrimsonError)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
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
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Song") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("New Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameSong(target, renameValue)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    playlistDialogTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { playlistDialogTarget = null },
            title = { Text("Add '${target.title}' to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("Create a playlist in the 'My Playlists' tab first.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        playlists.forEach { pl ->
                            OutlinedButton(
                                onClick = {
                                    onAddSongToPlaylist(pl, target.id)
                                    playlistDialogTarget = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(pl.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { playlistDialogTarget = null }) { Text("Close") }
            }
        )
    }
}
