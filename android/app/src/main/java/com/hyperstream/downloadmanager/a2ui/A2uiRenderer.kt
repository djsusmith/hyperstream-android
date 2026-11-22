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
                    val bgColor = when (component.tone) {
                        "warning" -> Color(0xFFF59E0B).copy(alpha = 0.16f)
                        "active" -> Color(0xFF3F5EFB).copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val borderColor = when (component.tone) {
                        "warning" -> Color(0xFFF59E0B).copy(alpha = 0.5f)
                        "active" -> Color(0xFF3F5EFB).copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bgColor,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clickable {
                                onAction(A2uiAction("banner_clicked", surface.surfaceId, mapOf("tone" to component.tone)))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (component.tone == "warning") Icons.Default.HourglassTop else Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (component.tone == "warning") Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(component.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    component.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                is A2uiComponent.MetricGrid -> Unit
                is A2uiComponent.DownloadNotificationCard -> {
                    val dm = surface.dataModel
                    val fileName = dm[component.fileNamePath] as? String ?: "Download"
                    val category = dm[component.categoryPath] as? String ?: "General"
                    val status = dm[component.statusPath] as? String ?: "Queued"
                    val pct = (dm[component.progressPercentPath] as? Number)?.toFloat() ?: 0f
                    val doneBytes = (dm[component.downloadedBytesPath] as? Number)?.toLong() ?: 0L
                    val totalBytes = (dm[component.totalBytesPath] as? Number)?.toLong() ?: 0L
                    val speedBps = (dm[component.speedBpsPath] as? Number)?.toLong() ?: 0L
                    val etaSec = (dm[component.etaSecondsPath] as? Number)?.toLong() ?: -1L
                    val actConns = (dm[component.activeThreadsPath] as? Number)?.toInt() ?: 0
                    val maxConns = (dm[component.maxThreadsPath] as? Number)?.toInt() ?: 32
                    @Suppress("UNCHECKED_CAST")
                    val segBuckets = (dm[component.segmentStatesPath] as? List<Int>) ?: emptyList()
                    val savePath = dm[component.savePathKey] as? String ?: ""
                    val errorMsg = dm[component.errorMessagePath] as? String ?: ""

                    A2uiDownloadNotificationCardView(
                        downloadId = component.downloadId,
                        fileName = fileName,
                        category = category,
                        status = status,
                        progressPct = pct,
                        downloadedBytes = doneBytes,
                        totalBytes = totalBytes,
                        speedBps = speedBps,
                        etaSeconds = etaSec,
                        activeThreads = actConns,
                        maxThreads = maxConns,
                        segmentBuckets = segBuckets,
                        savePath = savePath,
                        errorMessage = errorMsg,
                        onAction = { actionName ->
                            onAction(
                                A2uiAction(
                                    name = actionName,
                                    surfaceId = surface.surfaceId,
                                    payload = mapOf("id" to component.downloadId)
                                )
                            )
                        }
                    )
                }
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
                                    text = "$activeThreads / 128 Threads • Peak ${formatHeroSpeed(peakBps)}",
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
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

@Composable
private fun A2uiDownloadNotificationCardView(
    downloadId: Long,
    fileName: String,
    category: String,
    status: String,
    progressPct: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    speedBps: Long,
    etaSeconds: Long,
    activeThreads: Int,
    maxThreads: Int,
    segmentBuckets: List<Int>,
    savePath: String,
    errorMessage: String,
    onAction: (String) -> Unit
) {
    val isDownloading = status == "Downloading" || status == "Probing"
    val isCompleted = status == "Completed"
    val isFailed = status == "Failed"

    val accentColor = when {
        isDownloading -> Color(0xFF3F5EFB)
        isCompleted -> Color(0xFF10B981)
        isFailed -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (progressPct / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "notif_progress"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isDownloading) 6.dp else 2.dp,
        border = BorderStroke(
            width = if (isDownloading) 1.5.dp else 1.dp,
            color = if (isDownloading) accentColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clickable { onAction("inspect") }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Notification Header Row (Android System Shade style + Live Status Pill)
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
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isCompleted -> Icons.Default.CheckCircle
                                isDownloading -> Icons.Default.Bolt
                                else -> Icons.Default.HourglassTop
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = "HYPERSTREAM • ${category.uppercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = when {
                            isDownloading -> "LIVE • ${String.format("%.1f%%", progressPct)}"
                            isCompleted -> "COMPLETED • 100%"
                            isFailed -> "FAILED"
                            else -> "${status.uppercase()} • ${String.format("%.1f%%", progressPct)}"
                        },
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Filename & Telemetry Row
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                val sizeStr = if (totalBytes > 0L) {
                    "${formatNotifBytes(downloadedBytes)} / ${formatNotifBytes(totalBytes)}"
                } else {
                    formatNotifBytes(downloadedBytes)
                }
                val speedAndEta = if (isDownloading) {
                    val etaTxt = if (etaSeconds in 1..86400) " • ETA ${formatNotifEta(etaSeconds)}" else ""
                    " • ${formatHeroSpeed(speedBps)}$etaTxt • ${activeThreads}/${maxThreads}T"
                } else if (isCompleted) {
                    " • Saved to ${savePath.substringBeforeLast('/').removePrefix("/storage/emulated/0/")}"
                } else {
                    " • ${maxThreads}T configured"
                }
                Text(
                    text = "$sizeStr$speedAndEta",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isFailed && errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444),
                        maxLines = 2
                    )
                }
            }

            // Smooth Gradient Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.75f))
                            )
                        )
                )
            }

            // 16-Bucket A2UI Thread Segment Heatmap Strip
            if (segmentBuckets.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    segmentBuckets.forEach { stateCode ->
                        val segColor = when (stateCode) {
                            2 -> Color(0xFF10B981) // Done
                            1 -> Color(0xFF3F5EFB) // Actively receiving
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(segColor)
                        )
                    }
                }
            }

            // Interactive Notification Action Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.clickable {
                            onAction(if (isFailed) "retry" else "toggle_pause")
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isDownloading) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    isDownloading -> "Pause"
                                    isFailed -> "Retry"
                                    else -> "Resume"
                                },
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.16f),
                        modifier = Modifier.clickable { onAction("open_file") }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Open File",
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onAction("inspect") }
                ) {
                    Text(
                        text = "128T Inspector",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                    modifier = Modifier.clickable { onAction("cancel") }
                ) {
                    Text(
                        text = if (isCompleted) "Dismiss" else "Cancel",
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

private fun formatNotifBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        bytes >= kb -> String.format("%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

private fun formatNotifEta(seconds: Long): String {
    if (seconds <= 0L) return "0s"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
