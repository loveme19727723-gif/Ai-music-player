package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AppSettingsRecord
import com.example.data.model.CatalogDefaults
import com.example.data.model.CategoryRecord
import com.example.data.model.PublicSongRecord
import com.example.data.model.ReportRecord
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.GlassCard
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassElevated
import com.example.ui.theme.MusicGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AdminViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

@Composable
fun ProfileAndAdminScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    isAdmin: Boolean,
    appSettings: AppSettingsRecord?,
    onUpdateProfile: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var displayName by remember(userProfile?.displayName) { mutableStateOf(userProfile?.displayName ?: "") }
    var preferredLanguage by remember(userProfile?.preferredLanguage) { mutableStateOf(userProfile?.preferredLanguage ?: "English") }
    var showAdminPanel by remember { mutableStateOf(false) }

    val storageStats = remember {
        val audioBytes = File(context.filesDir, "audio").listFiles()?.sumOf { it.length() } ?: 0L
        val videoBytes = File(context.filesDir, "videos").listFiles()?.sumOf { it.length() } ?: 0L
        val coverBytes = File(context.filesDir, "covers").listFiles()?.sumOf { it.length() } ?: 0L
        Triple(audioBytes / 1024, videoBytes / 1024, coverBytes / 1024)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicGradients.darkBackdrop),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Profile & Studio Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("Account, Cloud Sync, Storage & Admin Controls", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!userProfile?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userProfile?.photoUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.size(60.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(60.dp).clip(CircleShape).background(MusicGradients.primaryBrand),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(30.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userProfile?.displayName ?: "Music Creator", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Text(userProfile?.email ?: "", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(shape = RoundedCornerShape(50), color = GlassElevated) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Stars, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("${userProfile?.credits ?: 100} Credits", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                    }
                                }
                                if (isAdmin) {
                                    Surface(shape = RoundedCornerShape(50), color = ElectricViolet.copy(alpha = 0.25f)) {
                                        Text("ADMIN", style = MaterialTheme.typography.labelSmall, color = NeonCyan, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Username / Artist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Preferred Song Language", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CatalogDefaults.languages) { lang ->
                            FilterChip(
                                selected = preferredLanguage == lang,
                                onClick = { preferredLanguage = lang },
                                label = { Text(lang) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onUpdateProfile(displayName, preferredLanguage) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Profile")
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("sign_out_button"),
                            border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sign Out", color = CrimsonError)
                        }
                    }
                }
            }
        }

        // Backend & Storage Transparency Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Engine & Storage Status", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                    Text("• AI Brain: Firebase AI Logic (Gemini 3.8 Flash & Nano Banana 2.1)", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Text("• Auth: Google Sign-In via Credential Manager (Email/Password login requires enabling in Firebase Console)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("• Audio Synth: Studio PCM WAV Engine (${storageStats.first} KB cached)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("• Video Renderer: On-Device H.264 MP4 (${storageStats.second} KB cached)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("• Cover Art Cache: ${storageStats.third} KB | External Cloud Music API: ${if (appSettings?.externalMusicApiEnabled == true) "Enabled" else "Not configured (using built-in DSP WAV Synth)"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }

        if (isAdmin) {
            item {
                Button(
                    onClick = { showAdminPanel = !showAdminPanel },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp).testTag("toggle_admin_dashboard"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = com.example.ui.theme.ObsidianBlack)
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (showAdminPanel) "Hide Admin Dashboard" else "Open Admin Dashboard", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (showAdminPanel) {
                item {
                    AdminDashboardSection(currentUserId = currentUserId)
                }
            }
        }
    }
}

@Composable
private fun AdminDashboardSection(currentUserId: String) {
    val adminVm: AdminViewModel = viewModel(
        key = "admin_$currentUserId",
        factory = AdminViewModel.provideFactory(currentUserId)
    )
    val usersState by adminVm.allUsers.collectAsStateWithLifecycle()
    val reportsState by adminVm.allReports.collectAsStateWithLifecycle()
    val publicSongsState by adminVm.publicSongs.collectAsStateWithLifecycle()
    val categoriesState by adminVm.categories.collectAsStateWithLifecycle()
    val settingsState by adminVm.appSettings.collectAsStateWithLifecycle()

    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val reports = (reportsState as? UiState.Success)?.data.orEmpty()
    val publicSongs = (publicSongsState as? UiState.Success)?.data.orEmpty()
    val categories = (categoriesState as? UiState.Success)?.data.orEmpty()
    val currentSettings = (settingsState as? UiState.Success)?.data ?: AppSettingsRecord()

    var featTitle by remember { mutableStateOf("") }
    var featArtist by remember { mutableStateOf("") }
    var featGenre by remember { mutableStateOf("DJ Bass Boost") }
    var featSection by remember { mutableStateOf("featured") }

    var newCatName by remember { mutableStateOf("") }
    var newCatGroup by remember { mutableStateOf("DJ") }

    var providerName by remember(currentSettings.providerName) { mutableStateOf(currentSettings.providerName) }
    var externalApiUrl by remember(currentSettings.externalMusicApiUrl) { mutableStateOf(currentSettings.externalMusicApiUrl) }
    var externalMusicEnabled by remember(currentSettings.externalMusicApiEnabled) { mutableStateOf(currentSettings.externalMusicApiEnabled) }
    var maintenanceMode by remember(currentSettings.maintenanceMode) { mutableStateOf(currentSettings.maintenanceMode) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Analytics Overview
        GlassCard(borderColor = NeonCyan) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Admin Analytics", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Text("Users: ${users.size} | Public/Featured Tracks: ${publicSongs.size}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("Open Reports: ${reports.count { it.status == "open" }} | Custom Categories: ${categories.size}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }

        // 2. Manage Featured Music
        GlassCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Manage Featured Music", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                OutlinedTextField(value = featTitle, onValueChange = { featTitle = it }, label = { Text("Featured Track Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = featArtist, onValueChange = { featArtist = it }, label = { Text("Featured Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("featured", "trending", "dj", "classic", "romantic", "editors_choice")) { sec ->
                        FilterChip(selected = featSection == sec, onClick = { featSection = sec }, label = { Text(sec) })
                    }
                }
                Button(
                    onClick = {
                        if (featTitle.isNotBlank()) {
                            adminVm.publishFeaturedTrack(featTitle, featArtist, featGenre, "Energetic", "English", featSection)
                            featTitle = ""
                            featArtist = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Publish Featured Track") }

                publicSongs.take(6).forEach { song ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("Section: ${song.featuredSection}", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        }
                        OutlinedButton(onClick = {
                            val nextSec = if (song.featuredSection == "featured") "none" else "featured"
                            adminVm.updateFeaturedSection(song.id, nextSec, "public")
                        }) {
                            Text(if (song.featuredSection == "featured") "Unfeature" else "Feature")
                        }
                    }
                }
            }
        }

        // 3. Manage Users (Credits & Block)
        GlassCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Manage Users (${users.size})", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                users.take(8).forEach { u ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(u.displayName, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("${u.email} • ${u.credits} cr • ${if (u.blocked) "BLOCKED" else "ACTIVE"}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        IconButton(onClick = { adminVm.updateUser(u.userId, u.blocked, u.credits + 50, u.role) }) {
                            Icon(Icons.Default.Stars, contentDescription = "Grant +50 credits", tint = AmberGold)
                        }
                        IconButton(onClick = { adminVm.updateUser(u.userId, !u.blocked, u.credits, u.role) }) {
                            Icon(
                                if (u.blocked) Icons.Default.CheckCircle else Icons.Default.Block,
                                contentDescription = "Toggle block",
                                tint = if (u.blocked) EmeraldLive else CrimsonError
                            )
                        }
                    }
                }
            }
        }

        // 4. Manage Categories
        GlassCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Manage Music Categories", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newCatName, onValueChange = { newCatName = it }, label = { Text("Category Name") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        if (newCatName.isNotBlank()) {
                            adminVm.addCategory(newCatName, newCatGroup)
                            newCatName = ""
                        }
                    }) { Text("Add") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("DJ", "NORMAL", "CLASSIC", "OTHER", "CUSTOM")) { g ->
                        FilterChip(selected = newCatGroup == g, onClick = { newCatGroup = g }, label = { Text(g) })
                    }
                }
                categories.forEach { cat ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${cat.name} (${cat.group})", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { adminVm.deleteCategory(cat.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete category", tint = CrimsonError)
                        }
                    }
                }
            }
        }

        // 5. Manage AI APIs & App Settings
        GlassCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Manage AI APIs & App Settings", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Text("Note: Never store secret API keys in client config. Use server-side Cloud Functions / Secrets for third-party API keys.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                OutlinedTextField(value = providerName, onValueChange = { providerName = it }, label = { Text("Active AI Provider") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = externalApiUrl, onValueChange = { externalApiUrl = it }, label = { Text("External Cloud Music Webhook URL (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable External Cloud Music API", color = TextPrimary)
                    Switch(checked = externalMusicEnabled, onCheckedChange = { externalMusicEnabled = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Maintenance Mode", color = TextPrimary)
                    Switch(checked = maintenanceMode, onCheckedChange = { maintenanceMode = it })
                }
                Button(
                    onClick = {
                        adminVm.saveSettings(
                            currentSettings.copy(
                                providerName = providerName,
                                externalMusicApiUrl = externalApiUrl,
                                externalMusicApiEnabled = externalMusicEnabled,
                                maintenanceMode = maintenanceMode
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save AI & App Settings") }
            }
        }

        // 6. Manage Reports
        GlassCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Moderation Reports (${reports.size})", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                if (reports.isEmpty()) {
                    Text("No open reports.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                } else {
                    reports.forEach { rep ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${rep.songTitle} (${rep.status})", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text(rep.reason, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            if (rep.status == "open") {
                                TextButton(onClick = { adminVm.resolveReport(rep.id, "resolved") }) { Text("Resolve") }
                                TextButton(onClick = { adminVm.resolveReport(rep.id, "dismissed") }) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }
        }
    }
}
