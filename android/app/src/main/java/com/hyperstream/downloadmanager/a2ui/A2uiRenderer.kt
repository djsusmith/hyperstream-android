package com.hyperstream.downloadmanager.a2ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders an [A2uiSurface] natively in Jetpack Compose using HyperStream's PDM-inspired catalog.
 */
@Composable
fun A2uiSurfaceRenderer(
    surface: A2uiSurface,
    onAction: (A2uiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        surface.components.forEach { component ->
            when (component) {
                is A2uiComponent.SpeedometerHero -> {
                    val dm = surface.dataModel
                    val speedBps = (dm[component.speedBpsPath] as? Number)?.toLong() ?: 0L
                    val peakBps = (dm[component.peakBpsPath] as? Number)?.toLong() ?: 0L
                    val activeCount = (dm[component.activeCountPath] as? Number)?.toInt() ?: 0
                    val queuedCount = (dm[component.queuedCountPath] as? Number)?.toInt() ?: 0
                    val doneCount = (dm[component.doneCountPath] as? Number)?.toInt() ?: 0
                    val activeThreads = (dm["activeThreads"] as? Number)?.toInt() ?: 0
                    val collapsed = dm[component.collapsedPath] as? Boolean ?: false
                    val showMap = dm[component.showMapPath] as? Boolean ?: false
                    val hasActive = dm[component.hasActivePath] as? Boolean ?: false
                    @Suppress("UNCHECKED_CAST")
                    val history = (dm[component.speedHistoryPath] as? List<Long>) ?: emptyList()

                    PdmSpeedometerHeroCard(
                        title = component.title,
                        speedBps = speedBps,
                        peakBps = peakBps,
                        activeCount = activeCount,
                        queuedCount = queuedCount,
                        doneCount = doneCount,
                        activeThreads = activeThreads,
                        collapsed = collapsed,
                        showConnectionMap = showMap,
                        hasActive = hasActive,
                        speedHistory = history,
                        onToggleCollapse = {
                            onAction(A2uiAction("toggle_collapse", surface.surfaceId))
                        },
                        onTogglePauseAll = {
                            onAction(A2uiAction(if (hasActive) "pause_all" else "resume_all", surface.surfaceId))
                        },
                        onToggleDialMode = {
                            onAction(A2uiAction("toggle_dial_mode", surface.surfaceId))
                        }
                    )
                }
                is A2uiComponent.StatusBanner -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(component.title, fontWeight = FontWeight.Bold)
                            Text(
                                component.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is A2uiComponent.MetricGrid -> Unit
            }
        }
    }
}

@Composable
private fun PdmSpeedometerHeroCard(
    title: String,
    speedBps: Long,
    peakBps: Long,
    activeCount: Int,
    queuedCount: Int,
    doneCount: Int,
    activeThreads: Int,
    collapsed: Boolean,
    showConnectionMap: Boolean,
    hasActive: Boolean,
    speedHistory: List<Long>,
    onToggleCollapse: () -> Unit,
    onTogglePauseAll: () -> Unit,
    onToggleDialMode: () -> Unit
) {
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF3F5EFB), Color(0xFF2B44E7), Color(0xFF2237C9)),
        start = Offset(0f, 0f),
        end = Offset(900f, 900f)
    )

    val speedMbps = speedBps / (1024f * 1024f)
    // Dynamic gauge max: scales cleanly (16 MB/s default like PDM, or up to 128 MB/s on fast Wi-Fi/5G)
    val gaugeMaxMbps = when {
        speedMbps > 64f || (peakBps / (1024f * 1024f)) > 64f -> 128f
        speedMbps > 16f || (peakBps / (1024f * 1024f)) > 16f -> 64f
        else -> 16f
    }
    val fraction = (speedMbps / gaugeMaxMbps).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 350),
        label = "gauge_fraction"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(gradient)
    ) {
        // Subtle concentric background rings + live bottom speed sparkline
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height * 0.42f)
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.width * 0.36f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = size.width * 0.52f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            if (!collapsed && speedHistory.size >= 2) {
                val maxVal = (speedHistory.maxOrNull() ?: 1L).coerceAtLeast(1024L).toFloat()
                val baseY = size.height - 6.dp.toPx()
                val amp = 24.dp.toPx()
                val stepX = size.width / (speedHistory.size - 1).coerceAtLeast(1)
                val path = Path()
                speedHistory.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val y = baseY - (v / maxVal).coerceIn(0f, 1f) * amp
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.65f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top bar: Live dot + "Total speed" + collapse chevron + "Pause all" pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (hasActive) Color(0xFF4ADE80) else Color(0xFF93C5FD))
                        )
                    }
                    Text(
                        text = if (collapsed) "$title • ${formatHeroSpeed(speedBps)}" else title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onToggleCollapse)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (collapsed) "Expand" else "Collapse",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                        modifier = Modifier.clickable(onClick = onTogglePauseAll)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (hasActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (hasActive) "Pause all" else "Resume",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !collapsed) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tapping the dial switches between Speedometer Dial & 128T Connection Overview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggleDialMode),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!showConnectionMap) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                SpeedometerGaugeDial(
                                    fraction = animatedFraction,
                                    maxMbps = gaugeMaxMbps.toInt(),
                                    modifier = Modifier.size(width = 190.dp, height = 108.dp)
                                )
                                val (numStr, unitStr) = splitSpeed(speedBps)
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = numStr,
                                        color = Color.White,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        lineHeight = 36.sp
                                    )
                                    Text(
                                        text = unitStr,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 5.dp)
                                    )
                                }
                                Text(
                                    text = "Peak ${formatHeroSpeed(peakBps)} • Tap for thread map",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "$activeThreads / 128 Rust Threads Active",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    val lit = ((activeThreads / 128f) * 32).toInt().coerceIn(0, 32)
                                    for (i in 0 until 32) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (i < lit) Color(0xFF4ADE80)
                                                    else Color.White.copy(alpha = 0.2f)
                                                )
                                        )
                                    }
                                }
                                Text(
                                    text = "${formatHeroSpeed(speedBps)} • Tap for speed dial",
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Bottom 3 glassmorphic stat tiles: Active / Queued / Done
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeroStatTile(
                            icon = { Icon(Icons.Default.Bolt, null, tint = Color(0xFFFACC15), modifier = Modifier.size(16.dp)) },
                            count = activeCount,
                            label = "Active",
                            modifier = Modifier.weight(1f)
                        )
                        HeroStatTile(
                            icon = { Icon(Icons.Default.HourglassTop, null, tint = Color(0xFFFDE047), modifier = Modifier.size(16.dp)) },
                            count = queuedCount,
                            label = "Queued",
                            modifier = Modifier.weight(1f)
                        )
                        HeroStatTile(
                            icon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp)) },
                            count = doneCount,
                            label = "Done",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatTile(
    icon: @Composable () -> Unit,
    count: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                icon()
                Text(
                    text = count.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SpeedometerGaugeDial(
    fraction: Float,
    maxMbps: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 10.dp.toPx()
        val diameter = size.width * 0.78f
        val topLeft = Offset((size.width - diameter) / 2f, size.height - diameter * 0.56f)
        val arcSize = Size(diameter, diameter)
        val startAngle = 180f
        val sweepTotal = 180f

        // Background track
        drawArc(
            color = Color.White.copy(alpha = 0.25f),
            startAngle = startAngle,
            sweepAngle = sweepTotal,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Active white arc
        if (fraction > 0.005f) {
            drawArc(
                color = Color.White,
                startAngle = startAngle,
                sweepAngle = sweepTotal * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Tick marks along outer rim
        val center = Offset(topLeft.x + diameter / 2f, topLeft.y + diameter / 2f)
        val tickOuter = diameter / 2f + 10.dp.toPx()
        val tickInner = diameter / 2f + 5.dp.toPx()
        for (i in 0..24) {
            val angleDeg = 180.0 + (i / 24.0) * 180.0
            val rad = angleDeg * PI / 180.0
            val p1 = Offset(center.x + (cos(rad) * tickInner).toFloat(), center.y + (sin(rad) * tickInner).toFloat())
            val p2 = Offset(center.x + (cos(rad) * tickOuter).toFloat(), center.y + (sin(rad) * tickOuter).toFloat())
            drawLine(
                color = Color.White.copy(alpha = if (i % 6 == 0) 0.85f else 0.4f),
                start = p1,
                end = p2,
                strokeWidth = if (i % 6 == 0) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Needle
        val needleAngleDeg = 180.0 + fraction * 180.0
        val needleRad = needleAngleDeg * PI / 180.0
        val needleLen = diameter / 2f - 12.dp.toPx()
        val tip = Offset(
            center.x + (cos(needleRad) * needleLen).toFloat(),
            center.y + (sin(needleRad) * needleLen).toFloat()
        )
        drawLine(
            color = Color(0xFFFACC15),
            start = center,
            end = tip,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = Color.White, radius = 6.dp.toPx(), center = center)
        drawCircle(color = Color(0xFF2B44E7), radius = 3.dp.toPx(), center = center)
    }
}

private fun splitSpeed(bps: Long): Pair<String, String> {
    if (bps <= 0L) return "0.0" to "KB/s"
    val mb = bps / (1024.0 * 1024.0)
    return if (mb >= 1.0) String.format("%.1f", mb) to "MB/s"
    else String.format("%.0f", bps / 1024.0) to "KB/s"
}

private fun formatHeroSpeed(bps: Long): String {
    val (n, u) = splitSpeed(bps)
    return "$n $u"
}
