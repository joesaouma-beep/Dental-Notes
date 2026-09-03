package com.dentalstudio.notes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dentalstudio.notes.data.db.NoteEntity
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.ui.components.AppCard
import com.dentalstudio.notes.ui.components.ConfidenceBar
import com.dentalstudio.notes.ui.components.EmptyState
import com.dentalstudio.notes.ui.components.Pill
import com.dentalstudio.notes.ui.components.SectionLabel
import com.dentalstudio.notes.ui.theme.AppBrush
import com.dentalstudio.notes.ui.theme.Mint40
import com.dentalstudio.notes.ui.theme.Violet50
import com.dentalstudio.notes.util.Formatting

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    padding: PaddingValues,
    practiceName: String,
    onOpenNote: (Long) -> Unit,
    onOpenAdapt: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
    ) {
        item { Hero(state, practiceName) }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(18.dp))
                LearningCard(state, onOpenAdapt)
                Spacer(Modifier.height(22.dp))
                SectionLabel("Recent notes")
                Spacer(Modifier.height(10.dp))
            }
        }

        if (state.notes.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🦷",
                    title = "No notes yet",
                    message = "Dictate your first treatment note. Every edit you make afterwards teaches the app how you write.",
                )
            }
        }

        items(state.notes, key = { it.id }) { note ->
            NoteRow(note = note, onClick = { onOpenNote(note.id) })
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Hero(state: HomeUiState, practiceName: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(AppBrush.hero)
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 26.dp),
    ) {
        Column {
            Text(
                practiceName.ifBlank { "Treatment notes" }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Dictate it once.\nIt writes it your way.",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat(state.notesThisWeek.toString(), "notes this week", Modifier.weight(1f))
                HeroStat(state.activeRules.toString(), "preferences learned", Modifier.weight(1f))
                HeroStat(
                    if (state.profile.samples >= 2) "${state.trimPercent}%" else "—",
                    "shorter drafts",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun LearningCard(state: HomeUiState, onOpenAdapt: () -> Unit) {
    AppCard(onClick = onOpenAdapt) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppBrush.learn),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.isLearning) "Adapting to your style" else "Learning starts with your first edit",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (state.isLearning) {
                        "${state.activeRules} preference${if (state.activeRules == 1) "" else "s"} from ${state.edits} edit${if (state.edits == 1) "" else "s"}"
                    } else {
                        "Correct a note and the next one arrives closer to finished"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.isLearning) {
            Spacer(Modifier.height(14.dp))
            ConfidenceBar(
                fraction = (state.edits.toFloat() / 12f).coerceIn(0.08f, 1f),
                brush = AppBrush.learn,
            )
        }
    }
}

@Composable
private fun NoteRow(note: NoteEntity, onClick: () -> Unit) {
    val template = NoteTemplate.byId(note.templateId)
    Box(Modifier.padding(horizontal = 16.dp)) {
        AppCard(onClick = onClick) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(template.emoji, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        note.patientLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${template.name} · ${Formatting.relativeTime(note.updatedAt)}" +
                            if (note.durationSec > 0) " · ${Formatting.duration(note.durationSec)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        preview(note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    if (note.edited || note.appliedRuleCount > 0) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (note.appliedRuleCount > 0) {
                                Pill(
                                    "${note.appliedRuleCount} learned edit${if (note.appliedRuleCount == 1) "" else "s"} applied",
                                    color = Violet50,
                                    leadingIcon = Icons.Filled.AutoAwesome,
                                )
                            }
                            if (note.edited) Pill("Edited", color = Mint40)
                        }
                    }
                }
            }
        }
    }
}

private fun preview(note: NoteEntity): String {
    val body = note.finalNote.ifBlank { note.draftNote }
    return body.lines()
        .firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("#") }
        ?.trim()
        ?.removePrefix("- ")
        ?: "No content yet."
}

/** The primary action, present on the notes list. */
@Composable
fun DictateFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = Mint40,
        contentColor = Color.White,
        icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
        text = { Text("Dictate", fontWeight = FontWeight.SemiBold) },
    )
}
