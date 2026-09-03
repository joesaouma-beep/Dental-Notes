package com.dentalstudio.notes.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/** Live microphone level, drawn as a symmetrical bar meter. */
@Composable
fun Waveform(
    amplitude: Float,
    active: Boolean,
    brush: Brush,
    modifier: Modifier = Modifier,
    bars: Int = 27,
) {
    val level by animateFloatAsState(
        targetValue = if (active) amplitude.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(120),
        label = "level",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bars) { i ->
            // A fixed envelope keeps the meter centred and readable rather than
            // jittering uniformly across the row.
            val envelope = abs(sin(i * 3.14159f / (bars - 1)))
            val height = (6f + level * 44f * (0.35f + 0.65f * envelope)).dp
            Box(
                Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(CircleShape)
                    .background(brush),
            )
        }
    }
}
