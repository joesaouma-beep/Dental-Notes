package com.dentalstudio.notes.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.ui.components.AppCard
import com.dentalstudio.notes.ui.components.Pill
import com.dentalstudio.notes.ui.components.SectionLabel
import com.dentalstudio.notes.ui.components.Waveform
import com.dentalstudio.notes.ui.theme.AppBrush
import com.dentalstudio.notes.ui.theme.Mint40
import com.dentalstudio.notes.ui.theme.Violet50
import com.dentalstudio.notes.util.Formatting

@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    onClose: () -> Unit,
    onNoteReady: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.startOrPause(state.settings.preferOfflineDictation)
    }

    LaunchedEffect(state.savedNoteId) {
        state.savedNoteId?.let { id ->
            viewModel.consumeSavedNote()
            onNoteReady(id)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            TopBar(state, onClose, onToggleManual = viewModel::toggleManualEntry)

            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.patientLabel,
                    onValueChange = viewModel::setPatientLabel,
                    label = { Text("Patient reference") },
                    placeholder = { Text("Initials or chair, e.g. J.S. 10:30") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(18.dp))
                SectionLabel("Template")
                Spacer(Modifier.height(10.dp))
                TemplateRow(selected = state.template, onSelect = viewModel::setTemplate)

                Spacer(Modifier.height(20.dp))

                if (state.manualEntry) {
                    ManualEntry(state, viewModel)
                } else {
                    RecorderPanel(
                        state = state,
                        speechAvailable = viewModel.speechAvailable,
                        onToggle = {
                            if (hasMicPermission) {
                                viewModel.startOrPause(state.settings.preferOfflineDictation)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onEditTranscript = viewModel::editTranscript,
                    )
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(16.dp))
                    ErrorCard(error, viewModel::dismissError)
                }

                Spacer(Modifier.height(20.dp))

                if (state.learnedPreferences > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Pill(
                            "${state.learnedPreferences} of your preferences will shape this note",
                            color = Violet50,
                            leadingIcon = Icons.Filled.AutoAwesome,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    onClick = viewModel::generate,
                    enabled = state.canGenerate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint40, contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Write the note", fontWeight = FontWeight.SemiBold)
                }

                if (!state.settings.hasApiKey) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "No Claude API key set — the note will be structured on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        AnimatedVisibility(visible = state.isGenerating) {
            GeneratingOverlay(state)
        }
    }
}

@Composable
private fun TopBar(state: RecordUiState, onClose: () -> Unit, onToggleManual: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 44.dp, start = 4.dp, end = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Close")
        }
        Column(Modifier.weight(1f)) {
            Text("New note", style = MaterialTheme.typography.titleLarge)
            Text(
                state.template.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onToggleManual) {
            Icon(
                if (state.manualEntry) Icons.Filled.Mic else Icons.Filled.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (state.manualEntry) "Dictate" else "Type")
        }
    }
}

@Composable
private fun TemplateRow(selected: NoteTemplate, onSelect: (NoteTemplate) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(NoteTemplate.ALL, key = { it.id }) { template ->
            val isSelected = template.id == selected.id
            Column(
                Modifier
                    .width(112.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onSelect(template) }
                    .padding(12.dp),
            ) {
                Text(template.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    template.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    template.blurb,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun RecorderPanel(
    state: RecordUiState,
    speechAvailable: Boolean,
    onToggle: () -> Unit,
    onEditTranscript: (String) -> Unit,
) {
    AppCard {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Formatting.duration(state.elapsedSeconds),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    !speechAvailable -> "Speech recognition unavailable"
                    state.isRecording -> "Listening"
                    state.isPaused -> "Paused"
                    else -> "Ready when you are"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            Waveform(
                amplitude = state.dictation.amplitude,
                active = state.isRecording,
                brush = if (state.isRecording) AppBrush.recording else AppBrush.record,
            )
            Spacer(Modifier.height(14.dp))

            RecordButton(recording = state.isRecording, onClick = onToggle)

            Spacer(Modifier.height(14.dp))
            Text(
                if (state.isRecording) "Tap to pause. Keep talking through the appointment."
                else "Tap to start dictating.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (state.dictation.hasContent) {
        Spacer(Modifier.height(16.dp))
        SectionLabel("Transcript")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.dictation.finalText,
            onValueChange = onEditTranscript,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text("Your dictation appears here") },
        )
        if (state.dictation.partialText.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                state.dictation.partialText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    state.dictation.error?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Box(
        Modifier
            .size(92.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(if (recording) AppBrush.recording else AppBrush.record)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (recording) Icons.Filled.Pause else Icons.Filled.Mic,
            contentDescription = if (recording) "Pause dictation" else "Start dictation",
            tint = Color.White,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun ManualEntry(state: RecordUiState, viewModel: RecordViewModel) {
    SectionLabel("Type the visit")
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.manualText,
        onValueChange = viewModel::setManualText,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(16.dp),
        placeholder = { Text("Describe the visit the way you would say it out loud.") },
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(onClick = onDismiss)
            .padding(14.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun GeneratingOverlay(state: RecordUiState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator(color = Mint40)
            Spacer(Modifier.height(20.dp))
            Text("Writing the note", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                when (state.stage) {
                    GenerationStage.APPLYING_STYLE -> "Applying what it has learned from your edits"
                    else -> "Structuring your dictation into ${state.template.name.lowercase()}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
