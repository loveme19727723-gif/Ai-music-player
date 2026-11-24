package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CatalogDefaults
import com.example.data.model.CategoryRecord
import com.example.data.model.PublicSongRecord
import com.example.data.model.SongRecord
import com.example.data.model.UiState
import com.example.ui.components.CoverArtwork
import com.example.ui.components.GlassCard
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassElevated
import com.example.ui.theme.MagentaPulse
import com.example.ui.theme.MusicGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
    publicSongsState: UiState<List<PublicSongRecord>>,
    categoriesState: UiState<List<CategoryRecord>>,
    onPlaySong: (SongRecord, List<SongRecord>) -> Unit,
    onSelectCategoryForCreation: (String) -> Unit,
    onLikePublicSong: (PublicSongRecord) -> Unit,
    onSaveToLibrary: (SongRecord) -> Unit,
    onReportSong: (songId: String, songTitle: String, reason: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFeaturedFilter by remember { mutableStateOf("All") }
    var customGenreInput by remember { mutableStateOf("") }
    var reportTarget by remember { mutableStateOf<PublicSongRecord?>(null) }
    var reportReason by remember { mutableStateOf("") }

    val cloudSongs = (publicSongsState as? UiState.Success)?.data.orEmpty()
    val adminCategories = (categoriesState as? UiState.Success)?.data.orEmpty()
    val allSongs = (cloudSongs + CatalogDefaults.showcaseSongs).distinctBy { it.id }

    val featuredTabs = listOf("All", "Featured DJ", "Featured Classic", "AI Creations", "Editor's Choice", "Trending")
    val filteredSongs = remember(allSongs, searchQuery, selectedFeaturedFilter) {
        allSongs.filter { song ->
            val matchesQuery = searchQuery.isBlank() ||
                song.title.contains(searchQuery, true) ||
                song.genre.contains(searchQuery, true) ||
                song.language.contains(searchQuery, true) ||
                song.creatorName.contains(searchQuery, true)
            val matchesTab = when (selectedFeaturedFilter) {
                "Featured DJ" -> song.featuredSection == "dj" || song.genre.contains("DJ", true)
                "Featured Classic" -> song.featuredSection == "classic" || song.genre.contains("Classic", true)
                "AI Creations" -> song.userId != "studio_official"
                "Editor's Choice" -> song.featuredSection == "editors_choice" || song.featuredSection == "featured"
                "Trending" -> song.featuredSection == "trending" || song.playCount > 1000
                else -> true
            }
            matchesQuery && matchesTab
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
                Text("Explore & Featured", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("35+ Music Categories, Featured Hits & Community Feed", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().testTag("explore_search_input"),
                    placeholder = { Text("Search songs, genres, Bengali/Hindi/DJ...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // Featured Filter Chips
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(featuredTabs) { tab ->
                    FilterChip(
                        selected = selectedFeaturedFilter == tab,
                        onClick = { selectedFeaturedFilter = tab },
                        label = { Text(tab) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricViolet,
                            selectedLabelColor = TextPrimary
                        )
                    )
                }
            }
        }

        // Community & Featured Feed
        item {
            Text(
                text = "Community & Featured Feed (${filteredSongs.size})",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }

        items(filteredSongs, key = { it.id }) { pubSong ->
            val asSong = pubSong.toSongRecord()
            val queue = filteredSongs.map { it.toSongRecord() }
            GlassCard(
                onClick = { onPlaySong(asSong, queue) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverArtwork(
                        coverType = pubSong.coverType,
                        contentDescription = pubSong.title,
                        modifier = Modifier.size(62.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pubSong.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${pubSong.creatorName} • ${pubSong.genre} • ${pubSong.language}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pubSong.featuredSection != "none") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ElectricViolet.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        pubSong.featuredSection.replace("_", " ").uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("${pubSong.playCount} plays • ${pubSong.likeCount} likes", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    IconButton(
                        onClick = { onLikePublicSong(pubSong) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = "Like song", tint = MagentaPulse, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { onSaveToLibrary(asSong) },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "Save to library", tint = NeonCyan, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { reportTarget = pubSong },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = "Report song", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ElectricViolet)
                            .clickable { onPlaySong(asSong, queue) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = TextPrimary)
                    }
                }
            }
        }

        // All Music Categories Browser
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                Text("All Music Categories", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text("Tap any genre to open Personalized Song Studio", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(12.dp))

                // Custom genre input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customGenreInput,
                        onValueChange = { customGenreInput = it },
                        placeholder = { Text("Enter custom genre (e.g. Baul EDM)...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = {
                            if (customGenreInput.isNotBlank()) {
                                onSelectCategoryForCreation(customGenreInput.trim())
                            }
                        },
                        modifier = Modifier.height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Use custom genre")
                        Spacer(Modifier.width(4.dp))
                        Text("Create")
                    }
                }
            }
        }

        // Standard Category Groups + Admin Custom Categories
        val groupedCategories = buildMap {
            putAll(CatalogDefaults.allCategoryGroups)
            if (adminCategories.isNotEmpty()) {
                put("Admin Curated", adminCategories.map { it.name })
            }
        }

        groupedCategories.forEach { (groupTitle, itemsList) ->
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(groupTitle, style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsList.forEach { cat ->
                            AssistChip(
                                onClick = { onSelectCategoryForCreation(cat) },
                                label = { Text(cat, style = MaterialTheme.typography.labelLarge) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = GlassElevated,
                                    labelColor = TextPrimary
                                ),
                                border = BorderStroke(1.dp, GlassBorder),
                                modifier = Modifier.height(40.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    reportTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("Report '${target.title}'") },
            text = {
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it },
                    label = { Text("Reason for report") },
                    placeholder = { Text("Spam, copyright, inappropriate lyrics...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onReportSong(target.id, target.title, reportReason)
                    reportReason = ""
                    reportTarget = null
                }) { Text("Submit Report") }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) { Text("Cancel") }
            }
        )
    }
}
