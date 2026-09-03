package com.dentalstudio.notes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.dentalstudio.notes.learning.NoteStructure

/**
 * Renders a generated note as headed blocks.
 *
 * The stored note is plain text so it can be copied straight into practice
 * management software; this only changes how it looks on screen.
 */
@Composable
fun NoteRenderer(
    note: String,
    modifier: Modifier = Modifier,
    accent: Brush,
) {
    val sections = NoteStructure.parse(note)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (sections.isEmpty()) {
            Text(
                note.ifBlank { "This note is empty." },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        sections.forEach { section ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        Modifier
                            .size(width = 3.dp, height = 14.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        section.heading,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                section.body.lines().filter { it.isNotBlank() }.forEach { line ->
                    val bullet = line.trimStart().startsWith("- ") ||
                        line.trimStart().startsWith("• ") ||
                        line.trimStart().startsWith("* ")
                    if (bullet) {
                        Row(modifier = Modifier.padding(bottom = 5.dp)) {
                            Text(
                                "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                line.trimStart().removeRange(0, 2).trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        Text(
                            line.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
            }
        }
    }
}
