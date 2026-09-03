package com.dentalstudio.notes.desktop.ui

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dentalstudio.notes.desktop.AppModel
import com.dentalstudio.notes.desktop.Screen
import com.dentalstudio.notes.desktop.store.DesktopSettings
import com.dentalstudio.notes.desktop.store.toDomain
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.speech.DictationStatus
import com.dentalstudio.notes.util.Formatting

@Composable
fun RecordPane(model: AppModel, settings: DesktopSettings) {
    val dictation by model.dictation.state.collectAsState()
    val workspace by model.repository.workspace.collectAsState()
    val activeRules = workspace.rules.count { it.toDomain().isActive }
    val listening = dictation.status == DictationStatus.LISTENING

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("New note", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        model.template.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { model.go(Screen.NOTES) }) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Discard")
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = model.patientLabel,
                onValueChange = { model.patientLabel = it },
                label = { Text("Patient reference") },
                placeholder = { Text("Initials or chair, e.g. J.S. 10:30") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Template")
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(NoteTemplate.ALL, key = { it.id }) { template ->
                    val selected = template.id == model.template.id
                    Column(
                        Modifier
                            .width(126.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { model.template = template }
                            .padding(12.dp),
                    ) {
                        Text(template.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(template.name, style = MaterialTheme.typography.labelLarge)
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

            Spacer(Modifier.height(22.dp))
            AppCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Formatting.duration(model.elapsedSeconds),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            listening -> "Listening"
                            dictation.status == DictationStatus.PAUSED -> "Paused"
                            !model.dictation.isAvailable -> "Dictation unavailable"
                            else -> "Ready when you are"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Waveform(dictation.amplitude, listening, if (listening) AppBrush.recording else AppBrush.record)
                    Spacer(Modifier.height(14.dp))
                    RecordButton(listening, model::toggleDictation)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        model.dictation.unavailableReason
                            ?: if (listening) "Click to pause. Keep talking through the appointment."
                            else "Click to start dictating.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Transcript")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = dictation.finalText,
                onValueChange = model::setTranscript,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text("Dictate, or type the visit here. Windows dictation (Win+H) types straight into this box.")
                },
            )
            if (dictation.partialText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(dictation.partialText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            dictation.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            model.recordError?.let { error ->
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable { model.recordError = null }
                        .padding(14.dp),
                ) {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Spacer(Modifier.height(20.dp))
            if (activeRules > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Pill(
                        "$activeRules of your preferences will shape this note",
                        color = Violet50,
                        leadingIcon = Icons.Filled.AutoAwesome,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Button(
                onClick = model::generate,
                enabled = dictation.fullText.trim().length >= 12 && !model.generating,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mint40, contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Write the note", fontWeight = FontWeight.SemiBold)
            }

            if (!settings.hasApiKey) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "No Claude API key set — the note will be structured on this computer only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (model.generating) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Mint40)
                    Spacer(Modifier.height(18.dp))
                    Text("Writing the note", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Applying what it has learned from your edits",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (recording) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    Box(
        Modifier
            .size(88.dp)
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
            modifier = Modifier.size(32.dp),
        )
    }
}
