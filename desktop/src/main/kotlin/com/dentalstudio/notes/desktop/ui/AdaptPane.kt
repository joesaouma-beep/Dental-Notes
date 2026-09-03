package com.dentalstudio.notes.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.dentalstudio.notes.desktop.AppModel
import com.dentalstudio.notes.desktop.store.Workspace
import com.dentalstudio.notes.desktop.store.toDomain
import com.dentalstudio.notes.domain.NoteTemplate
import com.dentalstudio.notes.learning.LearnedRule
import com.dentalstudio.notes.learning.MinedRule
import com.dentalstudio.notes.learning.RuleType
import com.dentalstudio.notes.learning.StyleProfile

@Composable
fun AdaptPane(model: AppModel, workspace: Workspace) {
    var confirmReset by remember { mutableStateOf(false) }
    val rules = workspace.rules.map { it.toDomain() }
    val active = rules.filter { it.isActive }
    val emerging = rules.filter { !it.isActive && it.enabled }
    val profile = workspace.profiles.firstOrNull { it.scope == "*" }?.toDomain() ?: StyleProfile()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().background(AppBrush.learn).padding(24.dp)) {
                Text("ADAPTIVE STYLE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.height(6.dp))
                Text("What the app has learned from you", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(
                    "${active.size} active preference${if (active.size == 1) "" else "s"} · " +
                        "${rules.count { it.isAutoApplied }} applied automatically · " +
                        "${workspace.editCount} edit${if (workspace.editCount == 1) "" else "s"} studied",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }

        item {
            Column(Modifier.padding(24.dp)) {
                AppCard {
                    Text("Your writing profile", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    if (profile.samples == 0) {
                        Text(
                            "Measured from the notes you edit. Nothing measured yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SummaryRow("Notes studied", profile.samples.toString())
                        SummaryRow("Preferred note length", "${profile.targetWords} words")
                        SummaryRow("You trim drafts by", "${(profile.trimRatio * 100).toInt()}%")
                        SummaryRow(
                            "Layout",
                            when {
                                profile.prefersBullets -> "Bullet points"
                                profile.prefersProse -> "Prose"
                                else -> "Mixed"
                            },
                        )
                        SummaryRow("Average sentence", "${profile.avgSentenceLength.toInt()} words")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Drafts now start at this length and in this layout, so there is less to cut.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (rules.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🧠",
                    title = "Nothing learned yet",
                    message = "Edit a generated note and the app compares it with the draft. Whatever you consistently change becomes a preference here.",
                )
            }
        }

        RuleType.entries.forEach { type ->
            val group = active.filter { it.type == type }
            if (group.isNotEmpty()) {
                item {
                    SectionLabel(sectionTitle(type), Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                items(group, key = { it.id }) { rule ->
                    Box(Modifier.padding(horizontal = 24.dp, vertical = 5.dp)) { RuleCard(model, rule) }
                }
            }
        }

        if (emerging.isNotEmpty()) {
            item { SectionLabel("Watching — seen once so far", Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
            items(emerging, key = { "emerging-${it.id}" }) { rule ->
                Box(Modifier.padding(horizontal = 24.dp, vertical = 5.dp)) { RuleCard(model, rule, emerging = true) }
            }
        }

        if (rules.isNotEmpty()) {
            item {
                Box(Modifier.padding(24.dp)) {
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
                    model.forgetEverything()
                }) { Text("Forget", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
private fun RuleCard(model: AppModel, rule: LearnedRule, emerging: Boolean = false) {
    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(describe(rule), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("seen ${rule.occurrences}×", color = if (rule.isAutoApplied) Mint40 else Amber50)
                    if (rule.isAutoApplied) Pill("applied automatically", color = Violet50)
                    if (rule.contradictions > 0) Pill("${rule.contradictions} kept as-is", color = MaterialTheme.colorScheme.error)
                    if (rule.scope != MinedRule.SCOPE_ALL) {
                        Pill(NoteTemplate.byId(rule.scope).name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            if (!emerging) {
                Switch(checked = rule.enabled, onCheckedChange = { model.setRuleEnabled(rule.id, it) })
            }
            IconButton(onClick = { model.forgetRule(rule.id) }) {
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
