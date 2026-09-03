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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dentalstudio.notes.desktop.AppModel
import com.dentalstudio.notes.desktop.store.NoteRecord
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.learning.TextTokens
import com.dentalstudio.notes.util.Formatting

private enum class NoteTab { NOTE, TRANSCRIPT, INSIGHTS }

@Composable
fun NotePane(model: AppModel, note: NoteRecord) {
    var tab by remember(note.id) { mutableStateOf(NoteTab.NOTE) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val template = NoteTemplate.byId(note.templateId)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(AppBrush.hero).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(note.patientLabel, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${template.name} · ${Formatting.dayAndTime(note.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(note.displayText)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy", color = Color.White)
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete", color = Color.White)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NoteTab.entries.forEach { entry ->
                    val selected = entry == tab
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { tab = entry }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                    ) {
                        Text(
                            entry.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
                when (tab) {
                    NoteTab.NOTE -> NoteBody(model, note)
                    NoteTab.TRANSCRIPT -> AppCard {
                        Text(
                            note.transcript.ifBlank { "No dictation was recorded for this note." },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    NoteTab.INSIGHTS -> Insights(note, template)
                }
                Spacer(Modifier.height(40.dp))
            }
        }

        if (model.outcome?.learnedSomething == true || model.banner != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
                LearningBanner(model)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("The note and its transcript are removed from this computer. Preferences already learned from it are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.deleteNote(note.id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NoteBody(model: AppModel, note: NoteRecord) {
    if (model.editing) {
        SectionLabel("Editing — your changes teach the app")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = model.draftText,
            onValueChange = { model.draftText = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp),
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = model::saveEdit,
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mint40, contentColor = Color.White),
            ) { Text("Save and learn", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = model::cancelEditing,
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Cancel") }
        }
    } else {
        if (note.appliedRuleCount > 0) {
            Pill(
                "${note.appliedRuleCount} learned preference${if (note.appliedRuleCount == 1) "" else "s"} applied to this draft",
                color = Violet50,
                leadingIcon = Icons.Filled.AutoAwesome,
            )
            Spacer(Modifier.height(14.dp))
        }
        AppCard { NoteRenderer(note.displayText, AppBrush.record) }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { model.startEditing(note.displayText) },
            modifier = Modifier.height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mint40, contentColor = Color.White),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Edit note", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Insights(note: NoteRecord, template: NoteTemplate) {
    val draftWords = TextTokens.wordCount(note.draftNote)
    val finalWords = TextTokens.wordCount(note.displayText)
    AppCard {
        Text("How this note was produced", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        InsightRow("Template", template.name)
        InsightRow("Written by", note.generatorLabel.ifBlank { "On-device" })
        InsightRow("Dictation length", Formatting.duration(note.durationSec))
        InsightRow("Preferences applied", note.appliedRuleCount.toString())
        InsightRow("Draft length", "$draftWords words")
        InsightRow("Your version", "$finalWords words")
        if (note.edited && draftWords > 0) {
            val delta = draftWords - finalWords
            InsightRow("Change", if (delta > 0) "$delta words removed" else "${-delta} words added")
        }
    }
    Spacer(Modifier.height(14.dp))
    AppCard {
        Text(
            if (note.edited) "This note has taught the app how you write."
            else "Edit this note and the app will learn from the difference.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InsightRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LearningBanner(model: AppModel) {
    val outcome = model.outcome
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (outcome?.learnedSomething == true) AppBrush.learn else AppBrush.record)
            .clickable { model.clearBanner() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column {
            Text(
                if (outcome?.learnedSomething == true) "Learned from your edit" else model.banner.orEmpty(),
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
