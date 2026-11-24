package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ChatMessageRecord
import com.example.data.model.SongRecord
import com.example.data.model.UiState
import com.example.ui.components.GlassCard
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassElevated
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.MusicGradients
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ChatSessionState

@Composable
fun AiChatScreen(
    chatMessagesState: UiState<List<ChatMessageRecord>>,
    chatState: ChatSessionState,
    mySongs: List<SongRecord>,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onPlaySong: (SongRecord) -> Unit,
    onEditSongInStudio: (SongRecord) -> Unit,
    onMakeVideo: (SongRecord) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var voiceNotice by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val messages = (chatMessagesState as? UiState.Success)?.data.orEmpty()

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = spoken
            }
        }
    }

    val launchVoiceRecognizer = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your song in Bengali, Hindi, English or Banglish...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            voiceNotice = "Speech recognition service is unavailable on this device. Please type your song prompt."
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchVoiceRecognizer()
        } else {
            voiceNotice = "Microphone permission denied. You can type your song request below."
        }
    }

    LaunchedEffect(messages.size, chatState.streamingReply) {
        val total = messages.size + (if (chatState.streamingReply != null) 1 else 0)
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicGradients.darkBackdrop)
    ) {
        // Header
        Surface(
            color = GlassSurface.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MusicGradients.primaryBrand),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TextPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("AI Music Chat", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        Text("Bengali • English • Hindi • Banglish", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                    }
                }
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier.size(48.dp).testTag("clear_chat_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat history", tint = TextSecondary)
                }
            }
        }

        // Conversation List
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && chatState.streamingReply == null) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Describe any song naturally!", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "Try saying (in English, Bengali, Hindi, or Banglish):\n• \"Make an energetic DJ song with my name.\"\n• \"Create 3 DJ variations in English.\"\n• \"Make a romantic song for Meera.\"\n• \"Make this track more energetic.\"\n• \"Create a music video for this song.\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id.ifBlank { "${it.sender}_${it.text.hashCode()}" } }) { msg ->
                val isUser = msg.sender == "user"
                val linkedSong = msg.songId?.let { id -> mySongs.firstOrNull { it.id == id } }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 330.dp),
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp
                        ),
                        color = if (isUser) ElectricViolet else GlassElevated,
                        border = if (isUser) null else BorderStroke(1.dp, GlassBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            if (linkedSong != null) {
                                Spacer(Modifier.height(10.dp))
                                ChatEmbeddedSongCard(
                                    song = linkedSong,
                                    onPlay = { onPlaySong(linkedSong) },
                                    onEdit = { onEditSongInStudio(linkedSong) },
                                    onEditPrompt = {
                                        inputText = linkedSong.prompt.ifBlank {
                                            "Update '${linkedSong.title}' (${linkedSong.genre}, ${linkedSong.mood}) to "
                                        }
                                    },
                                    onRegenerate = { onSendMessage("Regenerate '${linkedSong.title}' with a fresh beat variation") },
                                    onMakeVideo = { onMakeVideo(linkedSong) }
                                )
                            } else if (!msg.songTitle.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text("Generated: ${msg.songTitle}", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
                            }
                        }
                    }
                }
            }

            if (chatState.streamingReply != null || chatState.statusStage != null) {
                item {
                    Surface(
                        modifier = Modifier.widthIn(max = 330.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = GlassElevated,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!chatState.streamingReply.isNullOrBlank()) {
                                Text(
                                    text = chatState.streamingReply,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NeonCyan)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = chatState.statusStage ?: "Thinking musically...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        if (chatState.errorMessage != null) {
            Text(
                text = chatState.errorMessage,
                color = CrimsonError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (voiceNotice != null) {
            Text(
                text = voiceNotice!!,
                color = NeonCyan,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Quick Suggestion Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatState.quickSuggestions) { suggestion ->
                AssistChip(
                    onClick = { onSendMessage(suggestion) },
                    label = { Text(suggestion, maxLines = 1) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = GlassElevated,
                        labelColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.4f))
                )
            }
        }

        // Input Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    voiceNotice = null
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) launchVoiceRecognizer()
                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GlassElevated)
                    .testTag("voice_input_button")
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = NeonCyan)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f).testTag("chat_prompt_input"),
                placeholder = { Text("Describe your song (English/বাংলা/Hindi)...") },
                maxLines = 3,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricViolet,
                    unfocusedBorderColor = GlassBorder
                )
            )

            IconButton(
                onClick = {
                    val toSend = inputText.trim()
                    if (toSend.isNotEmpty()) {
                        inputText = ""
                        onSendMessage(toSend)
                    }
                },
                enabled = !chatState.sending && inputText.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank()) ElectricViolet else GlassElevated)
                    .testTag("send_chat_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send prompt", tint = TextPrimary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatEmbeddedSongCard(
    song: SongRecord,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onEditPrompt: () -> Unit,
    onRegenerate: () -> Unit,
    onMakeVideo: () -> Unit
) {
    val lyricsSnippet = song.lyrics.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("[") }
        .take(2)
        .joinToString(" / ")

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(ElectricViolet)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play ${song.title}", tint = TextPrimary)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text("${song.genre} • ${song.mood} • ${song.bpm} BPM", style = MaterialTheme.typography.bodySmall, color = NeonCyan)
                    if (song.recipientName.isNotBlank()) {
                        Text("For: ${song.recipientName}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
            if (lyricsSnippet.isNotBlank()) {
                Text(
                    text = lyricsSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AssistChip(
                    onClick = onEdit,
                    label = { Text("Edit Lyrics") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onEditPrompt,
                    label = { Text("Edit Prompt") }
                )
                AssistChip(
                    onClick = onRegenerate,
                    label = { Text("Regenerate") },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onMakeVideo,
                    label = { Text("Video") },
                    leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}
