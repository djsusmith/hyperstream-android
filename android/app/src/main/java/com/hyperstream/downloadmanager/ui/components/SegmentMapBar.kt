package com.hyperstream.downloadmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hyperstream.downloadmanager.core.SegmentUi

object SegmentMapCalculator {
    fun computeSegmentFillFractions(segments: List<SegmentUi>, maxConnections: Int): List<Float> {
        val count = maxConnections.coerceIn(1, 128)
        if (segments.isEmpty()) {
            return List(count) { 0f }
        }
        return (0 until count).map { idx ->
            val seg = segments.getOrNull(idx)
            if (seg == null) {
                0f
            } else {
                val span = (seg.end - seg.start).coerceAtLeast(1L)
                val done = (seg.pos - seg.start).coerceIn(0L, span)
                (done.toFloat() / span.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
}

@Composable
fun SegmentMapBar(
    segments: List<SegmentUi>,
    maxConnections: Int,
    activeConnections: Int,
    modifier: Modifier = Modifier
) {
    val clampedMax = maxConnections.coerceIn(1, 128)
    val fractions = SegmentMapCalculator.computeSegmentFillFractions(segments, clampedMax)
    val activeColor = MaterialTheme.colorScheme.primary
    val completedColor = Color(0xFF00E676)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Segmented Map ($activeConnections / $clampedMax threads)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Dynamic Halving + Slow-Shedding",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(trackColor)
        ) {
            val totalCells = fractions.size.coerceAtLeast(1)
            val cellWidth = size.width / totalCells.toFloat()
            fractions.forEachIndexed { idx, frac ->
                val x = idx * cellWidth
                val fillW = (cellWidth - 1f).coerceAtLeast(1f) * frac
                if (fillW > 0f) {
                    drawRect(
                        color = if (frac >= 0.99f) completedColor else activeColor,
                        topLeft = Offset(x, 0f),
                        size = Size(fillW, size.height)
                    )
                }
            }
        }
    }
}
