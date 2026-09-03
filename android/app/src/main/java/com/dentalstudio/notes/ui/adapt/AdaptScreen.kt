package com.dentalstudio.notes.ui.adapt

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.learning.LearnedRule
import com.dentalstudio.notes.learning.MinedRule
import com.dentalstudio.notes.learning.RuleType
import com.dentalstudio.notes.ui.components.AppCard
import com.dentalstudio.notes.ui.components.ConfidenceBar
import com.dentalstudio.notes.ui.components.EmptyState
import com.dentalstudio.notes.ui.components.Pill
import com.dentalstudio.notes.ui.components.SectionLabel
import com.dentalstudio.notes.ui.theme.AppBrush
import com.dentalstudio.notes.ui.theme.Amber50
import com.dentalstudio.notes.ui.theme.Mint40
import com.dentalstudio.notes.ui.theme.Violet50

@Composable
fun AdaptScreen(
    viewModel: AdaptViewModel,
    padding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp)) {
        item { Header(state) }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(18.dp))
                StyleSummary(state)
                Spacer(Modifier.height(22.dp))
            }
        }

        if (state.rules.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🧠",
                    title = "Nothing learned yet",
                    message = "Edit a generated note and the app compares it with the draft. Whatever you consistently change becomes a preference here.",
                )
            }
        }

        RuleType.entries.forEach { type ->
            val rules = state.byType[type].orEmpty()
            if (rules.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionLabel(sectionTitle(type))
                        Spacer(Modifier.height(10.dp))
                    }
                }
                items(rules, key = { it.id }) { rule ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        RuleCard(rule, viewModel)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }

        if (state.emerging.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionLabel("Watching — seen once so far")
                    Spacer(Modifier.height(10.dp))
                }
            }
            items(state.emerging, key = { "emerging-${it.id}" }) { rule ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    RuleCard(rule, viewModel, emerging = true)
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        if (state.rules.isNotEmpty()) {
            item {
                Box(Modifier.padding(16.dp)) {
                    TextButton(onClick = { confirmReset = true }) {
                        Text("Forget everything learned", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Forget everything learned?") },
            text = { Text("Every preference and style measurement is deleted. Your notes are kept. The app starts learning again from your next edit.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.forgetEverything()
                }) { Text("Forget", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(state: AdaptUiState) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(AppBrush.learn)
            .padding(horizontal = 20.dp)
            .padding(top = 56.dp, bottom = 24.dp),
    ) {
        Column {
            Text(
                "ADAPTIVE STYLE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(6.dp))
            Text("What the app has\nlearned from you", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(14.dp))
            Text(
                "${state.active.size} active preference${if (state.active.size == 1) "" else "s"} · " +
                    "${state.autoApplied} applied automatically · ${state.edits} edit${if (state.edits == 1) "" else "s"} studied",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun StyleSummary(state: AdaptUiState) {
    AppCard {
        Text("Your writing profile", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (state.profile.samples == 0) {
            Text(
                "Measured from the notes you edit. Nothing measured yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SummaryRow("Notes studied", state.profile.samples.toString())
            SummaryRow("Preferred note length", "${state.profile.targetWords} words")
            SummaryRow("You trim drafts by", "${state.trimPercent}%")
            SummaryRow(
                "Layout",
                when {
                    state.profile.prefersBullets -> "Bullet points"
                    state.profile.prefersProse -> "Prose"
                    else -> "Mixed"
                },
            )
            SummaryRow("Average sentence", "${state.profile.avgSentenceLength.toInt()} words")
            Spacer(Modifier.height(12.dp))
            Text(
                "Drafts now start at this length and in this layout, so there is less to cut.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
private fun RuleCard(rule: LearnedRule, viewModel: AdaptViewModel, emerging: Boolean = false) {
    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(describe(rule), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(
                        "seen ${rule.occurrences}×",
                        color = if (rule.isAutoApplied) Mint40 else Amber50,
                    )
                    if (rule.isAutoApplied) Pill("applied automatically", color = Violet50)
                    if (rule.contradictions > 0) Pill("${rule.contradictions} kept as-is", color = MaterialTheme.colorScheme.error)
                    if (rule.scope != MinedRule.SCOPE_ALL) {
                        Pill(NoteTemplate.byId(rule.scope).name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (!emerging) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { viewModel.setEnabled(rule.id, it) },
                )
            }
            IconButton(onClick = { viewModel.forget(rule.id) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Forget this preference",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ConfidenceBar(
            fraction = rule.confidence.toFloat() * (rule.occurrences.coerceAtMost(5) / 5f),
            brush = if (rule.isAutoApplied) AppBrush.learn else AppBrush.record,
        )
    }
}

private fun describe(rule: LearnedRule): String = when (rule.type) {
    RuleType.TERM -> "Write “${rule.replacement}” instead of “${rule.pattern}”"
    RuleType.REMOVE -> "Never include “${rule.pattern}”"
    RuleType.ADD -> "Always include “${rule.exampleAfter.ifBlank { rule.pattern }}”"
    RuleType.SECTION_DROP -> "Leave out the ${rule.pattern} heading"
}

private fun sectionTitle(type: RuleType): String = when (type) {
    RuleType.TERM -> "Your wording"
    RuleType.REMOVE -> "Phrases you cut"
    RuleType.ADD -> "Wording you add"
    RuleType.SECTION_DROP -> "Headings you drop"
}
