package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CatalogDefaults
import com.example.data.model.SongRecord
import com.example.ui.components.GlassCard
import com.example.ui.components.SongDownloadAndActionChips
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GlassElevated
import com.example.ui.theme.MusicGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.StudioUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongStudioScreen(
    presetPrompt: String,
    presetGenre: String,
    studioState: StudioUiState,
    onBack: () -> Unit,
    onGenerateLyricsOnly: (
        recipientName: String, customTitle: String, prompt: String, language: String,
        genre: String, style: String, mood: String, voiceType: String, beat: String,
        instruments: String, durationSec: Int
    ) -> Unit,
    onGenerateMusic: (
        recipientName: String, customTitle: String, customLyrics: String, prompt: String,
        language: String, genre: String, style: String, mood: String, voiceType: String,
        beat: String, instruments: String, durationSec: Int, variations: Int
    ) -> Unit,
    onEditableLyricsChanged: (String) -> Unit,
    onTranslateLyrics: (SongRecord, String) -> Unit,
    onChangeMood: (SongRecord, String) -> Unit,
    onGenerateCover: (SongRecord, String) -> Unit,
    onPlaySong: (SongRecord) -> Unit,
    onDownloadWav: (SongRecord) -> Unit,
    onDownloadVideo: (SongRecord) -> Unit,
    onDownloadCover: (SongRecord) -> Unit,
    onDownloadLyrics: (SongRecord) -> Unit,
    onShareSong: (SongRecord) -> Unit,
    onCopyLink: (SongRecord) -> Unit,
    onToggleFavorite: (SongRecord) -> Unit,
    onMakeVideo: (SongRecord) -> Unit
) {
    val active = studioState.activeSong
    var recipientName by remember(active?.id) { mutableStateOf(active?.recipientName ?: "") }
    var customTitle by remember(active?.id) { mutableStateOf(active?.title ?: "") }
    var promptText by remember(presetPrompt, active?.id) {
        mutableStateOf(presetPrompt.ifBlank { active?.prompt ?: "" })
    }
    var language by remember(active?.id) { mutableStateOf(active?.language ?: "English") }
    var genre by remember(presetGenre, active?.id) {
        mutableStateOf(presetGenre.ifBlank { active?.genre ?: "DJ Bass Boost" })
    }
    var customGenre by remember { mutableStateOf("") }
    var style by remember(active?.id) { mutableStateOf(active?.style ?: "Studio DJ") }
    var mood by remember(active?.id) { mutableStateOf(active?.mood ?: "Energetic") }
    var voiceType by remember(active?.id) { mutableStateOf(active?.voiceType ?: "Male") }
    var beat by remember(active?.id) { mutableStateOf(active?.beat ?: CatalogDefaults.beats.first()) }
    var instruments by remember(active?.id) { mutableStateOf(active?.instruments ?: CatalogDefaults.instrumentsList.first()) }
    var durationSlider by remember(active?.id) { mutableFloatStateOf((active?.durationSec ?: 36).toFloat()) }
    var variationsCount by remember { mutableIntStateOf(1) }
    var coverCustomPrompt by remember { mutableStateOf("") }
    var translateTarget by remember { mutableStateOf("Bengali") }

    LaunchedEffect(presetGenre) {
        if (presetGenre.isNotBlank()) genre = presetGenre
    }
    LaunchedEffect(presetPrompt) {
        if (presetPrompt.isNotBlank()) promptText = presetPrompt
    }

    val allGenres = remember {
        CatalogDefaults.djCategories + CatalogDefaults.normalCategories +
            CatalogDefaults.classicCategories + CatalogDefaults.otherCategories
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicGradients.darkBackdrop),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Personalized Song Studio", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text("Lyrics, Multi-Variations, Cover Art & WAV Synth", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                }
                TextButton(
                    onClick = {
                        recipientName = ""
                        customTitle = ""
                        promptText = ""
                        customGenre = ""
                        onEditableLyricsChanged("")
                    },
                    modifier = Modifier.testTag("studio_reset_button")
                ) {
                    Text("Reset", color = NeonCyan)
                }
            }
        }

        // Generation Progress Banner
        if (studioState.generating) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    borderColor = NeonCyan
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NeonCyan, strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(studioState.stageLabel.ifBlank { "Generating..." }, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        }
                        LinearProgressIndicator(
                            progress = { studioState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                            color = NeonCyan
                        )
                    }
                }
            }
        }

        if (studioState.errorMessage != null) {
            item {
                Text(
                    text = studioState.errorMessage,
                    color = CrimsonError,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
        if (studioState.statusMessage != null) {
            item {
                Text(
                    text = studioState.statusMessage,
                    color = EmeraldLive,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // Form Controls
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Personalization & Prompt", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                    val quickTemplates = listOf(
                        Triple("Bengali DJ (Aneek)", "Aneek", "DJ Bass Boost") to "Fast Beat, Bass Boost, Bengali DJ party song celebrating Aneek",
                        Triple("Romantic Hindi", "Meera", "Romantic") to "Soulful midnight romantic acoustic love song",
                        Triple("Classic Bengali", "Shilpi", "Classic Bengali") to "Nostalgic Rabindra-inspired classic Bengali melody with sitar and tabla",
                        Triple("Rap Anthem", "Champion", "Rap") to "High-energy motivational hip-hop anthem with heavy 808s"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickTemplates) { (meta, tplPrompt) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    recipientName = meta.second
                                    genre = meta.third
                                    promptText = tplPrompt
                                },
                                label = { Text(meta.first) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = recipientName,
                        onValueChange = { recipientName = it },
                        label = { Text("Your Name or Another Person's Name") },
                        placeholder = { Text("e.g. Aneek, Meera, Friend...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("studio_name_input")
                    )
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        label = { Text("Custom Song Title (Optional)") },
                        placeholder = { Text("Leave blank for AI to name it") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Song Idea / Theme") },
                        placeholder = { Text("e.g. Fast beat Bengali DJ party song celebrating Aneek") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("studio_prompt_input")
                    )

                    Text("Language", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CatalogDefaults.languages) { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { language = lang },
                                label = { Text(lang) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricViolet)
                            )
                        }
                    }

                    Text("Genre (${allGenres.size}+ Genres)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allGenres) { g ->
                            FilterChip(
                                selected = genre == g && customGenre.isBlank(),
                                onClick = { genre = g; customGenre = "" },
                                label = { Text(g) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricViolet)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customGenre,
                        onValueChange = { customGenre = it },
                        label = { Text("Or Custom Genre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = style,
                        onValueChange = { style = it },
                        label = { Text("Music Style") },
                        placeholder = { Text("Bengali DJ, Old Bollywood, Trap...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Mood", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CatalogDefaults.moods) { m ->
                            FilterChip(
                                selected = mood == m,
                                onClick = { mood = m },
                                label = { Text(m) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricViolet)
                            )
                        }
                    }

                    Text("Voice Type", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CatalogDefaults.voiceTypes) { v ->
                            FilterChip(
                                selected = voiceType == v,
                                onClick = { voiceType = v },
                                label = { Text(v) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricViolet)
                            )
                        }
                    }

                    Text("Beat Pattern", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CatalogDefaults.beats) { b ->
                            FilterChip(
                                selected = beat == b,
                                onClick = { beat = b },
                                label = { Text(b) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricViolet)
                            )
                        }
                    }

                    Text("Instruments", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CatalogDefaults.instrumentsList) { inst ->
                            FilterChip(
                                selected = instruments == inst,
                                onClick = { instruments = inst },
                                label = { Text(inst) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricViolet)
                            )
                        }
                    }

                    Text("Song Duration: ${durationSlider.toInt()}s", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    Slider(
                        value = durationSlider,
                        onValueChange = { durationSlider = it },
                        valueRange = 20f..90f
                    )

                    Text("Variations to Generate: $variationsCount", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { count ->
                            FilterChip(
                                selected = variationsCount == count,
                                onClick = { variationsCount = count },
                                label = { Text("$count Variation${if (count > 1) "s" else ""}") }
                            )
                        }
                    }

                    val resolvedGenre = customGenre.trim().ifBlank { genre }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                onGenerateLyricsOnly(
                                    recipientName, customTitle, promptText, language,
                                    resolvedGenre, style, mood, voiceType, beat, instruments, durationSlider.toInt()
                                )
                            },
                            enabled = !studioState.generating,
                            modifier = Modifier.weight(1f).height(50.dp).testTag("generate_lyrics_button")
                        ) {
                            Icon(Icons.Default.Lyrics, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Lyrics First")
                        }
                        Button(
                            onClick = {
                                onGenerateMusic(
                                    recipientName, customTitle, studioState.editableLyrics, promptText,
                                    language, resolvedGenre, style, mood, voiceType, beat, instruments,
                                    durationSlider.toInt(), variationsCount
                                )
                            },
                            enabled = !studioState.generating,
                            modifier = Modifier.weight(1f).height(50.dp).testTag("generate_music_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Generate Music")
                        }
                    }
                }
            }
        }

        // Editable Lyrics & AI Tools Section
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("2. AI Lyrics Editor & Translator", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                    OutlinedTextField(
                        value = studioState.editableLyrics,
                        onValueChange = onEditableLyricsChanged,
                        label = { Text("Editable Lyrics ([Intro], [Verse 1], [Chorus], [Bridge], [Outro])") },
                        minLines = 6,
                        maxLines = 14,
                        modifier = Modifier.fillMaxWidth().testTag("editable_lyrics_input")
                    )

                    if (active != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(listOf("Bengali", "Hindi", "Banglish", "English", "Punjabi")) { lang ->
                                    FilterChip(
                                        selected = translateTarget == lang,
                                        onClick = { translateTarget = lang },
                                        label = { Text(lang) }
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { onTranslateLyrics(active.copy(lyrics = studioState.editableLyrics), translateTarget) },
                                enabled = !studioState.generating && studioState.editableLyrics.isNotBlank()
                            ) {
                                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Translate")
                            }
                        }

                        Text("AI Song Mood Changer", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Energetic", "Sad", "Romantic", "Party", "Lo-Fi Chill").forEach { m ->
                                OutlinedButton(
                                    onClick = { onChangeMood(active, m) },
                                    enabled = !studioState.generating
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("$m Version")
                                }
                            }
                        }
                    }
                }
            }
        }

        // AI Album Cover Maker & Active Song Downloads
        if (active != null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("3. AI Album Cover & Exports", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                            Button(
                                onClick = { onPlaySong(active) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play Track")
                            }
                        }

                        OutlinedTextField(
                            value = coverCustomPrompt,
                            onValueChange = { coverCustomPrompt = it },
                            label = { Text("AI Album Cover Style Prompt") },
                            placeholder = { Text("Neon Kolkata skyline, cyberpunk vinyl...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onGenerateCover(active, coverCustomPrompt) },
                            enabled = !studioState.generatingCover,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_cover_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = GlassElevated),
                            border = BorderStroke(1.dp, NeonCyan)
                        ) {
                            if (studioState.generatingCover) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeonCyan, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Generating Cover Art (30-60s)...")
                            } else {
                                Icon(Icons.Default.Brush, contentDescription = null, tint = NeonCyan)
                                Spacer(Modifier.width(8.dp))
                                Text("Generate AI Album Cover", color = TextPrimary)
                            }
                        }

                        if (studioState.coverError != null) {
                            Text(studioState.coverError, color = CrimsonError, style = MaterialTheme.typography.bodySmall)
                        }
                        if (studioState.coverBitmap != null) {
                            Image(
                                bitmap = studioState.coverBitmap.asImageBitmap(),
                                contentDescription = "Generated Album Cover",
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .align(Alignment.CenterHorizontally),
                                contentScale = ContentScale.Crop
                            )
                        }

                        SongDownloadAndActionChips(
                            song = active,
                            onDownloadWav = { onDownloadWav(active) },
                            onDownloadVideo = { onDownloadVideo(active) },
                            onDownloadCover = { onDownloadCover(active) },
                            onDownloadLyrics = { onDownloadLyrics(active) },
                            onShare = { onShareSong(active) },
                            onCopyLink = { onCopyLink(active) },
                            onToggleFavorite = { onToggleFavorite(active) },
                            onMakeVideo = { onMakeVideo(active) }
                        )
                    }
                }
            }
        }
    }
}
