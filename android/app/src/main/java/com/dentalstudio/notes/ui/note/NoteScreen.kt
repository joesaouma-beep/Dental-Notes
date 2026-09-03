package com.dentalstudio.notes.ui.note

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dentalstudio.notes.data.db.NoteEntity
import com.dentalstudio.notes.learning.TextTokens
import com.dentalstudio.notes.ui.components.AppCard
import com.dentalstudio.notes.ui.components.NoteRenderer
import com.dentalstudio.notes.ui.components.Pill
import com.dentalstudio.notes.ui.components.SectionLabel
import com.dentalstudio.notes.ui.theme.AppBrush
import com.dentalstudio.notes.ui.theme.Mint40
import com.dentalstudio.notes.ui.theme.Violet50
import com.dentalstudio.notes.util.Formatting

@Composable
fun NoteScreen(
    viewModel: NoteViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    val note = state.note
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            NoteTopBar(
                note = note,
                onBack = onBack,
                onCopy = { copyToClipboard(context, state.displayText) },
                onShare = { share(context, state.displayText) },
                onDelete = { confirmDelete = true },
            )

            TabRow(state.tab, viewModel::selectTab)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                when (state.tab) {
                    NoteTab.NOTE -> NoteTabContent(state, viewModel)
                    NoteTab.TRANSCRIPT -> TranscriptTab(note)
                    NoteTab.INSIGHTS -> InsightsTab(state)
                }
                Spacer(Modifier.height(40.dp))
            }
        }

        AnimatedVisibility(
            visible = state.outcome?.learnedSomething == true || state.message != null,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LearningBanner(state, viewModel::dismissMessage)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("The note and its transcript are removed from this device. Preferences already learned from it are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NoteTopBar(
    note: NoteEntity?,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppBrush.hero)
            .padding(top = 44.dp, bottom = 18.dp)
            .padding(horizontal = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy note", tint = Color.White)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share note", tint = Color.White)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete note", tint = Color.White)
            }
        }
        Column(Modifier.padding(horizontal = 12.dp)) {
            Text(
                note?.patientLabel ?: "Note",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                note?.let {
                    "${com.dentalstudio.notes.domain.NoteTemplate.byId(it.templateId).name} · ${Formatting.dayAndTime(it.createdAt)}"
                } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun TabRow(selected: NoteTab, onSelect: (NoteTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NoteTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoteTabContent(state: NoteUiState, viewModel: NoteViewModel) {
    if (state.editing) {
        SectionLabel("Editing — your changes teach the app")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.draftText,
            onValueChange = viewModel::updateDraft,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp),
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = viewModel::saveEdit,
                enabled = !state.saving,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mint40, contentColor = Color.White),
            ) { Text("Save and learn", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = viewModel::cancelEditing,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Cancel") }
        }
    } else {
        val note = state.note
        if (note != null && note.appliedRuleCount > 0) {
            Pill(
                "${note.appliedRuleCount} learned preference${if (note.appliedRuleCount == 1) "" else "s"} applied to this draft",
                color = Violet50,
                leadingIcon = Icons.Filled.AutoAwesome,
            )
            Spacer(Modifier.height(14.dp))
        }
        AppCard {
            NoteRenderer(note = state.displayText, accent = AppBrush.record)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { viewModel.startEditing(state.displayText) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mint40, contentColor = Color.White),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Edit note", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TranscriptTab(note: NoteEntity?) {
    AppCard {
        Text(
            note?.transcript?.ifBlank { "No dictation was recorded for this note." }
                ?: "No transcript.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InsightsTab(state: NoteUiState) {
    val note = state.note ?: return
    val draftWords = TextTokens.wordCount(note.draftNote)
    val finalWords = TextTokens.wordCount(note.finalNote.ifBlank { note.draftNote })

    AppCard {
        Text("How this note was produced", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        InsightRow("Template", state.template.name)
        InsightRow("Written by", note.generatorLabel.ifBlank { "On-device" })
        InsightRow("Dictation length", Formatting.duration(note.durationSec))
        InsightRow("Preferences applied", note.appliedRuleCount.toString())
        InsightRow("Draft length", "$draftWords words")
        InsightRow("Your version", "$finalWords words")
        if (note.edited && draftWords > 0) {
            val delta = draftWords - finalWords
            InsightRow(
                "Change",
                if (delta > 0) "$delta words removed" else "${-delta} words added",
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AppBrush.learn),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (note.edited) "This note has taught the app how you write."
                else "Edit this note and the app will learn from the difference.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun InsightRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LearningBanner(state: NoteUiState, onDismiss: () -> Unit) {
    val outcome = state.outcome
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (outcome?.learnedSomething == true) AppBrush.learn else AppBrush.record)
            .clickable(onClick = onDismiss)
            .padding(16.dp),
    ) {
        Column {
            Text(
                if (outcome?.learnedSomething == true) "Learned from your edit" else state.message.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            if (outcome != null && outcome.learnedSomething) {
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (outcome.newRules > 0) append("${outcome.newRules} new preference${if (outcome.newRules == 1) "" else "s"}")
                        if (outcome.newRules > 0 && outcome.reinforcedRules > 0) append(", ")
                        if (outcome.reinforcedRules > 0) append("${outcome.reinforcedRules} reinforced")
                        if (outcome.wordsTrimmed > 0) append(" · ${outcome.wordsTrimmed} words shorter than the draft")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Treatment note", text))
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share note"))
}
