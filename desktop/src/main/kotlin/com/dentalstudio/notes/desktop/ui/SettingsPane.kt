package com.dentalstudio.notes.desktop.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dentalstudio.notes.desktop.AppModel
import com.dentalstudio.notes.desktop.store.AppPaths
import com.dentalstudio.notes.desktop.store.DesktopSettings
import com.dentalstudio.notes.domain.ToothNotation
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Locale
import javax.swing.JFileChooser

@Composable
fun SettingsPane(model: AppModel, settings: DesktopSettings) {
    var showKey by remember { mutableStateOf(false) }
    var keyDraft by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var confirmWipe by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(AppBrush.hero).padding(24.dp)) {
            Text("SETTINGS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(6.dp))
            Text("Set up once", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }

        Column(Modifier.padding(24.dp).width(720.dp)) {
            SectionLabel("Clinician")
            Spacer(Modifier.height(10.dp))
            AppCard {
                OutlinedTextField(
                    value = settings.clinicianName,
                    onValueChange = { value -> model.updateSettings { it.copy(clinicianName = value) } },
                    label = { Text("Your name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = settings.practiceName,
                    onValueChange = { value -> model.updateSettings { it.copy(practiceName = value) } },
                    label = { Text("Practice name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
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
                    "Stored only on this computer, in ${AppPaths.settingsFile}. Without a key the app structures notes locally instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    label = { Text("sk-ant-…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { model.updateSettings { it.copy(apiKey = keyDraft.trim()) } }) { Text("Save key") }
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                    if (settings.hasApiKey) {
                        TextButton(onClick = {
                            keyDraft = ""
                            model.updateSettings { it.copy(apiKey = "") }
                        }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Model", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                DesktopSettings.MODELS.forEach { (id, label) ->
                    ChoiceRow(label, settings.model == id) { model.updateSettings { it.copy(model = id) } }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Dictation")
            Spacer(Modifier.height(10.dp))
            AppCard {
                Text("Speech model", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Recognition runs on this computer. Download a Vosk model, unpack it, and point the app at the folder. " +
                        "Without one you can still use Windows dictation (Win+H) directly in the transcript box.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        settings.voskModelPath.ifBlank { "No model folder set" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        pickDirectory()?.let { path -> model.updateSettings { it.copy(voskModelPath = path) } }
                    }) { Text("Choose folder") }
                    if (settings.voskModelPath.isNotBlank()) {
                        TextButton(onClick = { model.updateSettings { it.copy(voskModelPath = "") } }) { Text("Clear") }
                    }
                }
                model.dictation.unavailableReason?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Charting and learning")
            Spacer(Modifier.height(10.dp))
            AppCard {
                Text("Tooth numbering", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ToothNotation.entries.forEach { notation ->
                    ChoiceRow("${notation.label} — ${notation.example}", settings.notation == notation) {
                        model.updateSettings { it.copy(toothNotation = notation.name) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Apply learned preferences", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Rewrite each draft with the preferences the app is most confident about.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.autoApplyLearning,
                        onCheckedChange = { value -> model.updateSettings { it.copy(autoApplyLearning = value) } },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Data")
            Spacer(Modifier.height(10.dp))
            AppCard {
                Text(
                    "Notes, transcripts and learned preferences are stored in ${AppPaths.dataDir}. " +
                        "Dictation text is sent to Anthropic only when an API key is set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { model.forgetEverything() }) { Text("Reset learned preferences") }
                TextButton(onClick = { confirmWipe = true }) {
                    Text("Delete all notes and learning", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?") },
            text = { Text("Every note, transcript and learned preference on this computer is removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    model.wipeEverything()
                }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Folder picker.
 *
 * Compose Desktop has no folder chooser of its own. AWT's FileDialog can only
 * be coaxed into choosing a directory on macOS; on Windows and Linux it always
 * returns a file, which is useless for pointing at a model folder. Swing's
 * JFileChooser is the one that can select a directory there.
 */
private fun pickDirectory(): String? {
    val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    return if (os.contains("mac")) pickDirectoryNative() else pickDirectorySwing()
}

private fun pickDirectorySwing(): String? = try {
    val chooser = JFileChooser().apply {
        dialogTitle = "Choose the speech model folder"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
} catch (e: Exception) {
    null
}

private fun pickDirectoryNative(): String? {
    val previous = System.getProperty("apple.awt.fileDialogForDirectories")
    return try {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val dialog = FileDialog(null as Frame?, "Choose the speech model folder", FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return dir
        File(dir, name).absolutePath
    } catch (e: Exception) {
        null
    } finally {
        if (previous == null) System.clearProperty("apple.awt.fileDialogForDirectories")
        else System.setProperty("apple.awt.fileDialogForDirectories", previous)
    }
}
