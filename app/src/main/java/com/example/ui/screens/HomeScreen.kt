package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CatalogDefaults
import com.example.data.model.FeaturedArtist
import com.example.data.model.PublicSongRecord
import com.example.data.model.SongRecord
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.CoverArtwork
import com.example.ui.components.GlassCard
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassElevated
import com.example.ui.theme.MagentaPulse
import com.example.ui.theme.MusicGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    userProfile: UserProfile?,
    mySongsState: UiState<List<SongRecord>>,
    publicSongsState: UiState<List<PublicSongRecord>>,
    onOpenAiChat: () -> Unit,
    onOpenSongStudio: (presetPrompt: String, presetGenre: String) -> Unit,
    onPlaySong: (SongRecord, List<SongRecord>) -> Unit,
    onOpenExplore: () -> Unit
) {
    val mySongs = (mySongsState as? UiState.Success)?.data.orEmpty()
    val cloudPublic = (publicSongsState as? UiState.Success)?.data.orEmpty()
    val allShowcaseAndPublic = (cloudPublic + CatalogDefaults.showcaseSongs)
        .distinctBy { it.id }

    val featuredMusic = allShowcaseAndPublic.filter {
        it.featuredSection in listOf("featured", "editors_choice")
    }.ifEmpty { allShowcaseAndPublic.take(4) }.map { it.toSongRecord() }

    val trendingSongs = allShowcaseAndPublic.sortedByDescending { it.playCount }.map { it.toSongRecord() }
    val popularDjSongs = allShowcaseAndPublic.filter {
        it.genre.contains("DJ", true) || it.featuredSection == "dj"
    }.map { it.toSongRecord() }
    val classicSongs = allShowcaseAndPublic.filter {
        it.genre.contains("Classic", true) || it.genre.contains("Bollywood", true) || it.featuredSection == "classic"
    }.map { it.toSongRecord() }
    val romanticSongs = allShowcaseAndPublic.filter {
        it.genre.contains("Romantic", true) || it.mood.contains("Romantic", true) || it.featuredSection == "romantic"
    }.map { it.toSongRecord() }
    val latestReleases = allShowcaseAndPublic.take(6).map { it.toSongRecord() }
    val mostPlayed = (mySongs + allShowcaseAndPublic.map { it.toSongRecord() }).sortedByDescending { it.playCount }.take(6)
    val recommended = allShowcaseAndPublic.reversed().take(5).map { it.toSongRecord() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicGradients.darkBackdrop),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Header Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Music World",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Welcome back, ${userProfile?.displayName ?: "Creator"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = GlassElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Stars, contentDescription = "Credits", tint = AmberGold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${userProfile?.credits ?: 100} Credits",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_studio),
                    contentDescription = "AI Music Studio Hero",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ObsidianBlack.copy(alpha = 0.92f), ObsidianBlack.copy(alpha = 0.45f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ElectricViolet.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "GEMINI AI MUSIC STUDIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Create Songs With Your Name",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Chat in Bengali, Hindi, English or Banglish",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onOpenAiChat,
                            modifier = Modifier.height(48.dp).testTag("home_ai_chat_cta"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI Music Chat")
                        }
                        OutlinedButton(
                            onClick = { onOpenSongStudio("", "DJ Bass Boost") },
                            modifier = Modifier.height(48.dp).testTag("home_studio_cta")
                        ) {
                            Text("Song Studio", color = TextPrimary)
                        }
                    }
                }
            }
        }

        // Personalized Song Makers (Extra Premium Features)
        item {
            SectionHeader("Personalized Makers", "One-tap special occasion tracks", onSeeAll = { onOpenSongStudio("", "Pop") })
            val makers = listOf(
                Triple("Birthday Song", "Birthday", Icons.Default.Cake),
                Triple("Love Song", "Love", Icons.Default.Favorite),
                Triple("Friendship", "Friendship", Icons.Default.Group),
                Triple("Rap Battle", "Rap", Icons.Default.Mic),
                Triple("My Anthem", "Motivational", Icons.Default.GraphicEq)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(makers) { (label, genre, icon) ->
                    GlassCard(
                        onClick = { onOpenSongStudio("Create an original $label with my name", genre) },
                        modifier = Modifier.width(134.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MusicGradients.primaryBrand),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1)
                            Text(genre, style = MaterialTheme.typography.bodySmall, color = NeonCyan, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Recently Generated Songs (User's own)
        if (mySongs.isNotEmpty()) {
            item {
                SectionHeader("Recently Generated Songs", "Your latest AI creations", onSeeAll = null)
                SongCarousel(mySongs.take(8), onPlaySong)
            }
        }

        // Featured Music
        item {
            SectionHeader("Featured Music", "Curated by AI Music World Admin", onSeeAll = onOpenExplore)
            SongCarousel(featuredMusic, onPlaySong)
        }

        // Featured Artists
        item {
            SectionHeader("Featured Artists", "Tap an AI producer style to remix", onSeeAll = onOpenExplore)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(CatalogDefaults.featuredArtists) { artist ->
                    ArtistCard(artist = artist) {
                        onOpenSongStudio(artist.samplePrompt, artist.specialty.split(" ").first())
                    }
                }
            }
        }

        // Trending Songs
        item {
            SectionHeader("Trending Songs", "Top plays across the studio", onSeeAll = onOpenExplore)
            SongCarousel(trendingSongs, onPlaySong)
        }

        // Popular DJ Songs
        if (popularDjSongs.isNotEmpty()) {
            item {
                SectionHeader("Popular DJ Songs", "Bass boost, club & party mixes", onSeeAll = onOpenExplore)
                SongCarousel(popularDjSongs, onPlaySong)
            }
        }

        // Classic Music
        if (classicSongs.isNotEmpty()) {
            item {
                SectionHeader("Classic Music", "Bengali, Hindi & Classical Indian", onSeeAll = onOpenExplore)
                SongCarousel(classicSongs, onPlaySong)
            }
        }

        // Romantic Songs
        if (romanticSongs.isNotEmpty()) {
            item {
                SectionHeader("Romantic Songs", "Midnight acoustic & love melodies", onSeeAll = onOpenExplore)
                SongCarousel(romanticSongs, onPlaySong)
            }
        }

        // Latest Releases & Most Played & Recommended
        item {
            SectionHeader("Latest Releases", "Freshly dropped tracks", onSeeAll = onOpenExplore)
            SongCarousel(latestReleases, onPlaySong)
        }
        item {
            SectionHeader("Most Played", "Listeners' all-time favorites", onSeeAll = onOpenExplore)
            SongCarousel(mostPlayed, onPlaySong)
        }
        item {
            SectionHeader("Recommended For You", "Tailored to your listening vibe", onSeeAll = onOpenExplore)
            SongCarousel(recommended, onPlaySong)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    onSeeAll: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        if (onSeeAll != null) {
            Text(
                text = "Explore",
                style = MaterialTheme.typography.labelLarge,
                color = NeonCyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SongCarousel(
    songs: List<SongRecord>,
    onPlaySong: (SongRecord, List<SongRecord>) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(songs, key = { it.id.ifBlank { it.title } }) { song ->
            GlassCard(
                onClick = { onPlaySong(song, songs) },
                modifier = Modifier.width(156.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(142.dp)) {
                    CoverArtwork(
                        coverType = song.coverType,
                        localCoverPath = song.localCoverPath,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ElectricViolet),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play ${song.title}",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${song.genre} • ${song.language}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistCard(
    artist: FeaturedArtist,
    onClick: () -> Unit
) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier.width(138.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CoverArtwork(
                coverType = artist.coverType,
                contentDescription = artist.name,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(10.dp))
            Text(artist.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1)
            Text(
                artist.specialty,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
