package com.hyperstream.downloadmanager.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hyperstream.downloadmanager.service.HyperStreamDownloadService
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hyperstream.downloadmanager.a2ui.A2uiSurfaceRenderer
import com.hyperstream.downloadmanager.a2ui.HyperStreamA2uiCatalog
import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.core.MdmUiState
import com.hyperstream.downloadmanager.util.FileOpener

@Composable
fun NotificationsScreen(
    state: MdmUiState,
    onTogglePauseResume: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit,
    onCancelDownload: (Long, Boolean) -> Unit,
    onInspectDownload: (DownloadItemUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedFilter by remember { mutableStateOf("All") }
    var showA2uiWireInspector by remember { mutableStateOf(false) }
    var batteryUnrestricted by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    var systemNotifsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        systemNotifsEnabled = granted || NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (systemNotifsEnabled) {
            runCatching {
                context.startService(Intent(context, HyperStreamDownloadService::class.java))
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryUnrestricted = isBatteryOptimizationIgnored(context)
                systemNotifsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeDownloads = remember(state.downloads) {
        state.downloads.filter { it.status == "Downloading" || it.status == "Probing" }
    }
    val pausedOrFailedDownloads = remember(state.downloads) {
        state.downloads.filter { it.status == "Paused" || it.status == "Failed" || it.status == "Queued" }
    }
    val completedDownloads = remember(state.downloads) {
        state.downloads.filter { it.status == "Completed" }
    }

    val notificationsSurface = remember(
        state.downloads,
        state.totalDownSpeedBps,
        state.activeThreadsTotal,
        state.wakeLockActive,
        batteryUnrestricted,
        selectedFilter
    ) {
        HyperStreamA2uiCatalog.buildDownloadNotificationsSurface(
            downloads = state.downloads,
            totalDownSpeedBps = state.totalDownSpeedBps,
            activeThreadsTotal = state.activeThreadsTotal,
            wakeLockActive = state.wakeLockActive,
            batteryOptimizationIgnored = batteryUnrestricted,
            filter = selectedFilter
        )
    }

    val filterTabs = remember(
        state.downloads.size,
        activeDownloads.size,
        pausedOrFailedDownloads.size,
        completedDownloads.size
    ) {
        listOf(
            "All" to state.downloads.size,
            "Active" to activeDownloads.size,
            "Paused/Failed" to pausedOrFailedDownloads.size,
            "Completed" to completedDownloads.size
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header + Quick Actions + A2UI Protocol Inspector toggle
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Notification Center",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Live download progress cards & system shade sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Prompt if Android 13+ system shade notifications are disabled
                AnimatedVisibility(visible = !systemNotifsEnabled) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show progress in Android Notification Bar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Allow system notifications to see live download progress in your phone's top bar.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        runCatching {
                                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Enable", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Global Notification Shade Controls (Pause All / Resume All)
                if (state.downloads.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                activeDownloads.forEach { onTogglePauseResume(it.id) }
                            },
                            enabled = activeDownloads.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3F5EFB)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Pause All (${activeDownloads.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                pausedOrFailedDownloads.forEach { d ->
                                    if (d.status == "Failed") onRetryDownload(d.id) else onTogglePauseResume(d.id)
                                }
                            },
                            enabled = pausedOrFailedDownloads.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Resume All (${pausedOrFailedDownloads.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Filter Pills
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterTabs) { (label, count) ->
                    val selected = selectedFilter == label
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { selectedFilter = label }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = count.toString(),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Render A2UI Notification Cards Surface
        item {
            A2uiSurfaceRenderer(
                surface = notificationsSurface,
                onAction = { action ->
                    val targetId = (action.payload["id"] as? Number)?.toLong()
                    when (action.name) {
                        "banner_clicked" -> {
                            if (!batteryUnrestricted) {
                                requestIgnoreBatteryOptimizations(context)
                            }
                        }
                        "toggle_pause" -> if (targetId != null) onTogglePauseResume(targetId)
                        "retry" -> if (targetId != null) onRetryDownload(targetId)
                        "cancel" -> if (targetId != null) onCancelDownload(targetId, false)
                        "inspect" -> if (targetId != null) {
                            state.downloads.find { it.id == targetId }?.let { onInspectDownload(it) }
                        }
                        "open_file" -> if (targetId != null) {
                            state.downloads.find { it.id == targetId }?.let { item ->
                                FileOpener.openFile(context, item)
                            }
                        }
                    }
                }
            )
        }

        if (state.downloads.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No active or recent download notifications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Start any download to watch real-time 128-thread A2UI progress cards here and in your Android notification shade.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
