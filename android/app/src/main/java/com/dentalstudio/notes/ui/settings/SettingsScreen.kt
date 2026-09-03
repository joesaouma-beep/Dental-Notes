package com.dentalstudio.notes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dentalstudio.notes.data.prefs.AppSettings
import com.dentalstudio.notes.domain.ToothNotation
import com.dentalstudio.notes.ui.components.AppCard
import com.dentalstudio.notes.ui.components.SectionLabel
import com.dentalstudio.notes.ui.theme.AppBrush

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    padding: PaddingValues,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showKey by remember { mutableStateOf(false) }
    var keyDraft by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var confirmWipe by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = padding.calculateBottomPadding() + 32.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(AppBrush.hero)
                .padding(horizontal = 20.dp)
                .padding(top = 56.dp, bottom = 24.dp),
        ) {
            Column {
                Text("SETTINGS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.height(6.dp))
                Text("Set up once", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
        }

        Column(Modifier.padding(16.dp)) {
            SectionLabel("Clinician")
            Spacer(Modifier.height(10.dp))
            AppCard {
                OutlinedTextField(
                    value = settings.clinicianName,
                    onValueChange = viewModel::setClinicianName,
                    label = { Text("Your name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = settings.practiceName,
                    onValueChange = viewModel::setPracticeName,
                    label = { Text("Practice name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Note generation")
            Spacer(Modifier.height(10.dp))
            AppCard {
                Text("Claude API key", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Stored only on this device. Without a key the app structures notes on-device instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    label = { Text("sk-ant-…") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "Hide key" else "Show key",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.setApiKey(keyDraft) }) { Text("Save key") }
                    if (settings.hasApiKey) {
                        TextButton(onClick = {
                            keyDraft = ""
                            viewModel.setApiKey("")
                        }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Model", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                AppSettings.MODELS.forEach { (id, label) ->
                    ChoiceRow(
                        label = label,
                        selected = settings.model == id,
                        onClick = { viewModel.setModel(id) },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Charting")
            Spacer(Modifier.height(10.dp))
            AppCard {
                Text("Tooth numbering", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ToothNotation.entries.forEach { notation ->
                    ChoiceRow(
                        label = "${notation.label} — ${notation.example}",
                        selected = settings.toothNotation == notation,
                        onClick = { viewModel.setToothNotation(notation) },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Dictation and learning")
            Spacer(Modifier.height(10.dp))
            AppCard {
                ToggleRow(
                    title = "Offline dictation",
                    subtitle = "Use on-device speech recognition. More private, sometimes less accurate.",
                    checked = settings.preferOfflineDictation,
                    onCheckedChange = viewModel::setOfflineDictation,
                )
                Spacer(Modifier.height(14.dp))
                ToggleRow(
                    title = "Apply learned preferences",
                    subtitle = "Rewrite each draft with the preferences the app is most confident about.",
                    checked = settings.autoApplyLearning,
                    onCheckedChange = viewModel::setAutoApply,
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Data")
            Spacer(Modifier.height(10.dp))
            AppCard {
                Text(
                    "Notes, transcripts and learned preferences stay in this app's private storage. " +
                        "Dictation text is sent to Claude only when an API key is set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = viewModel::resetLearning) { Text("Reset learned preferences") }
                TextButton(onClick = { confirmWipe = true }) {
                    Text("Delete all notes and learning", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?") },
            text = { Text("Every note, transcript and learned preference on this device is removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    viewModel.wipeEverything()
                }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
