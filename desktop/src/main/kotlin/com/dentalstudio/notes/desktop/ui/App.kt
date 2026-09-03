package com.dentalstudio.notes.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dentalstudio.notes.desktop.AppModel
import com.dentalstudio.notes.desktop.Screen
import com.dentalstudio.notes.desktop.store.NoteRecord
import com.dentalstudio.notes.desktop.store.toDomain
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.util.Formatting
import androidx.compose.runtime.collectAsState

@Composable
fun App(model: AppModel) {
    val workspace by model.repository.workspace.collectAsState()
    val settings by model.repository.settings.collectAsState()

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Sidebar(
            model = model,
            notes = workspace.notes.sortedByDescending { it.updatedAt },
            practiceName = settings.practiceName,
            activeRules = workspace.rules.count { it.toDomain().isActive },
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (model.screen) {
                Screen.NOTES -> {
                    val selected = model.selectedNoteId?.let { id -> workspace.notes.firstOrNull { it.id == id } }
                    if (selected == null) {
                        NotesWelcome(model, workspace.notes.size)
                    } else {
                        NotePane(model, selected)
                    }
                }
                Screen.RECORD -> RecordPane(model, settings)
                Screen.ADAPT -> AdaptPane(model, workspace)
                Screen.SETTINGS -> SettingsPane(model, settings)
            }
        }
    }
}

@Composable
private fun Sidebar(
    model: AppModel,
    notes: List<NoteRecord>,
    practiceName: String,
    activeRules: Int,
) {
    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().background(AppBrush.hero).padding(20.dp),
        ) {
            Text(
                practiceName.ifBlank { "Dental Notes" },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Dictate it once. It writes it your way.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        Column(Modifier.padding(12.dp)) {
            PrimaryAction(onClick = model::newNote)
            Spacer(Modifier.height(12.dp))
            NavItem("Notes", Icons.Filled.Description, model.screen == Screen.NOTES) { model.go(Screen.NOTES) }
            NavItem("Adapt", Icons.Filled.AutoAwesome, model.screen == Screen.ADAPT, badge = activeRules) { model.go(Screen.ADAPT) }
            NavItem("Settings", Icons.Filled.Settings, model.screen == Screen.SETTINGS) { model.go(Screen.SETTINGS) }
        }

        if (model.screen == Screen.NOTES) {
            Spacer(Modifier.height(4.dp))
            SectionLabel("Recent notes", Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(notes, key = { it.id }) { note ->
                    NoteRow(note, note.id == model.selectedNoteId) { model.openNote(note.id) }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PrimaryAction(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppBrush.record)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("New note", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        if (badge > 0) {
            Spacer(Modifier.weight(1f))
            Pill(badge.toString(), color = Violet50)
        }
    }
}

@Composable
private fun NoteRow(note: NoteRecord, selected: Boolean, onClick: () -> Unit) {
    val template = NoteTemplate.byId(note.templateId)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text(template.emoji, style = MaterialTheme.typography.bodyLarge) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                note.patientLabel,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${template.name} · ${Formatting.relativeTime(note.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (note.edited) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Mint40))
        }
    }
}

@Composable
private fun NotesWelcome(model: AppModel, noteCount: Int) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (noteCount == 0) {
            EmptyState(
                emoji = "🦷",
                title = "No notes yet",
                message = "Dictate your first treatment note. Every edit you make afterwards teaches the app how you write.",
            )
        } else {
            EmptyState(
                emoji = "📋",
                title = "Pick a note",
                message = "Choose a note on the left, or start a new one.",
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(200.dp)) { PrimaryAction(onClick = model::newNote) }
    }
}
