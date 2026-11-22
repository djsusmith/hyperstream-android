package com.hyperstream.downloadmanager.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hyperstream.downloadmanager.core.MdmUiState
import com.hyperstream.downloadmanager.core.SettingsUi
import com.hyperstream.downloadmanager.ui.theme.MdmThemeStyle
import com.hyperstream.downloadmanager.updater.UpdateChannel
import com.hyperstream.downloadmanager.updater.UpdateChannelManager
import com.hyperstream.downloadmanager.updater.UpdateCheckResult
import kotlinx.coroutines.launch

internal fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

internal fun isAllFilesAccessGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

internal fun requestAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.recoverCatching {
            val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    } else {
        Toast.makeText(context, "Storage permission is already handled by system", Toast.LENGTH_SHORT).show()
    }
}

internal fun requestIgnoreBatteryOptimizations(context: Context) {
    val pkgUri = Uri.parse("package:${context.packageName}")
    val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(directIntent)
    }.onFailure {
        runCatching {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}

private fun resolveTreeUriToFilePath(treeUri: Uri): String? {
    return runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri) // e.g. "primary:Download/MyFolder"
        when {
            docId.startsWith("primary:") -> {
                val rel = docId.removePrefix("primary:").trim('/')
                if (rel.isEmpty()) "/storage/emulated/0" else "/storage/emulated/0/$rel"
            }
            docId.startsWith("home:") -> {
                val rel = docId.removePrefix("home:").trim('/')
                if (rel.isEmpty()) "/storage/emulated/0/Documents" else "/storage/emulated/0/Documents/$rel"
            }
            docId.contains(":") -> {
                val parts = docId.split(":", limit = 2)
                val vol = parts[0]
                val rel = parts.getOrElse(1) { "" }.trim('/')
                if (rel.isEmpty()) "/storage/$vol" else "/storage/$vol/$rel"
            }
            else -> null
        }
    }.getOrNull()
}

@Composable
fun SettingsAndUpdaterScreen(
    state: MdmUiState,
    onUpdateSettings: ((SettingsUi) -> SettingsUi) -> Unit,
    onWhitelistDoze: () -> String = { "Doze whitelist requested via Shizuku" },
    onSilentSelfUpdate: () -> String = { "Silent APK update triggered via Shizuku" },
    onOpenCredits: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s = state.settings
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var shizukuStatusMessage by remember { mutableStateOf<String?>(null) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult>(UpdateCheckResult.Idle) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var customDirInput by remember(s.defaultDownloadDir) { mutableStateOf(s.defaultDownloadDir) }
    var batteryUnrestricted by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    var allFilesAccessGranted by remember { mutableStateOf(isAllFilesAccessGranted(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryUnrestricted = isBatteryOptimizationIgnored(context)
                allFilesAccessGranted = isAllFilesAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            val resolved = resolveTreeUriToFilePath(uri)
            if (!resolved.isNullOrBlank()) {
                customDirInput = resolved
                onUpdateSettings { it.copy(defaultDownloadDir = resolved) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    Toast.makeText(
                        context,
                        "Please grant 'All files access' so HyperStream can save downloads to $resolved",
                        Toast.LENGTH_LONG
                    ).show()
                    requestAllFilesAccess(context)
                }
            }
        }
    }

    (updateResult as? UpdateCheckResult.UpdateAvailable)?.let { upd ->
        if (showUpdateDialog) {
            UpdateDialog(update = upd, onDismiss = { showUpdateDialog = false })
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 128-Thread Engine & Speed Limiter
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Engine Concurrency (Unlocked 1..128 Threads)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Default Connections per Download: ${s.maxConnections} / 128")
                    Slider(
                        value = s.maxConnections.toFloat(),
                        onValueChange = { v -> onUpdateSettings { it.copy(maxConnections = v.toInt().coerceIn(1, 128)) } },
                        valueRange = 1f..128f
                    )
                    Text(
                        text = "Min Split Size: ${s.minSplitKb} KB • Global Speed Cap: ${if (s.speedLimitKbps == 0L) "Unlimited" else "${s.speedLimitKbps} KB/s"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 1b. Storage & Category Folders (Issue #6)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Storage & Category Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose your default download directory and whether HyperStream automatically creates category subfolders (Video, Programs, Music, etc.).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customDirInput,
                        onValueChange = { customDirInput = it },
                        label = { Text("Default Download Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { folderPickerLauncher.launch(null) }
                        ) {
                            Text("Choose Folder")
                        }
                        OutlinedButton(
                            onClick = {
                                val trimmed = customDirInput.trim().trimEnd('/')
                                if (trimmed.isNotEmpty()) {
                                    onUpdateSettings { it.copy(defaultDownloadDir = trimmed) }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                        Toast.makeText(
                                            context,
                                            "Please grant 'All files access' so HyperStream can save downloads to $trimmed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        requestAllFilesAccess(context)
                                    }
                                }
                            },
                            enabled = customDirInput.trim().trimEnd('/') != s.defaultDownloadDir
                        ) {
                            Text("Save Path")
                        }
                        OutlinedButton(
                            onClick = {
                                val def = "/storage/emulated/0/Download/HyperStream"
                                customDirInput = def
                                onUpdateSettings { it.copy(defaultDownloadDir = def) }
                            }
                        ) {
                            Text("Reset")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use Category By Default",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (s.useCategoryFolders) {
                                    "Enabled: Files sort into ${s.defaultDownloadDir}/<Category>/"
                                } else {
                                    "Disabled: All files save directly into ${s.defaultDownloadDir}/"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = s.useCategoryFolders,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(useCategoryFolders = enabled) }
                            }
                        )
                    }
                    Surface(
                        color = if (allFilesAccessGranted) Color(0xFF10B981).copy(alpha = 0.14f) else Color(0xFFF59E0B).copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (allFilesAccessGranted) "✓ All Files Access (Granted)" else "⚠ All Files Access Needed for Custom Folders",
                                    fontWeight = FontWeight.Bold,
                                    color = if (allFilesAccessGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = if (allFilesAccessGranted) {
                                        "HyperStream has full write access to save in any internal storage or SD card folder."
                                    } else {
                                        "Android requires 'All files access' permission to save files into custom folders (like ${s.defaultDownloadDir})."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!allFilesAccessGranted) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { requestAllFilesAccess(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                                ) {
                                    Text("Grant Access", color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1c. Background Reliability & Battery Optimization (Issue #5)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background Downloads & Battery Optimization",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Prevents Android (Xiaomi, Samsung, Oppo, Vivo) from pausing or killing 128-thread downloads when the screen is off.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        color = if (batteryUnrestricted) Color(0xFF10B981).copy(alpha = 0.14f) else Color(0xFFF59E0B).copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (batteryUnrestricted) "✓ Unrestricted (Active)" else "⚠ Battery Optimization Active",
                                    fontWeight = FontWeight.Bold,
                                    color = if (batteryUnrestricted) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = if (batteryUnrestricted) {
                                        "HyperStream is exempted from Doze & background battery restrictions."
                                    } else {
                                        "Downloads may pause in the background. Tap Disable to allow unrestricted transfers."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (!batteryUnrestricted) {
                                Button(
                                    onClick = { requestIgnoreBatteryOptimizations(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                                ) {
                                    Text("Disable", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        }
                                    }
                                ) {
                                    Text("Manage")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Upstream Sync & In-App Update Checker
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "App Updates & Upstream Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Current Installed Version: v${com.hyperstream.downloadmanager.BuildConfig.VERSION_NAME}\nUpdate Channel determines which releases you receive from GitHub.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = s.updateChannel == "stable",
                            onClick = {
                                onUpdateSettings { it.copy(updateChannel = "stable") }
                                updateResult = UpdateCheckResult.Idle
                            },
                            label = { Text("Stable Channel") }
                        )
                        FilterChip(
                            selected = s.updateChannel == "nightly",
                            onClick = {
                                onUpdateSettings { it.copy(updateChannel = "nightly") }
                                updateResult = UpdateCheckResult.Idle
                            },
                            label = { Text("Nightly / Bug-Fix") }
                        )
                    }

                    // Check for Updates Action Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    updateResult = UpdateCheckResult.Checking
                                    val channel = if (s.updateChannel == "nightly") {
                                        UpdateChannel.NIGHTLY_BUGFIX
                                    } else {
                                        UpdateChannel.STABLE
                                    }
                                    updateResult = UpdateChannelManager.checkForUpdates(
                                        currentVersion = com.hyperstream.downloadmanager.BuildConfig.VERSION_NAME,
                                        channel = channel
                                    )
                                }
                            },
                            enabled = updateResult !is UpdateCheckResult.Checking
                        ) {
                            if (updateResult is UpdateCheckResult.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(" Checking...", modifier = Modifier.padding(start = 8.dp))
                            } else {
                                Text("Check for Updates")
                            }
                        }
                    }

                    // Update Result Display Banner
                    when (val res = updateResult) {
                        is UpdateCheckResult.Checking -> {
                            Text(
                                text = "Connecting to GitHub Releases API...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is UpdateCheckResult.UpToDate -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "✓ Up to date (v${res.currentVersion})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Checked at ${res.checkedAt}. No new updates available on ${s.updateChannel} channel.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        is UpdateCheckResult.UpdateAvailable -> {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "🚀 Update Available: ${res.releaseName}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Tag: ${res.tagName} ${if (res.isPrerelease) "(Pre-release)" else "(Stable)"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (res.releaseNotes.isNotBlank()) {
                                        val notes = com.hyperstream.downloadmanager.updater.ApkSelfUpdater.plainReleaseNotes(res.releaseNotes)
                                        Text(
                                            text = notes.take(300) + if (notes.length > 300) "..." else "",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { showUpdateDialog = true }) {
                                            Text("Update now")
                                        }
                                        if (s.shizukuEnabled) {
                                            OutlinedButton(onClick = {
                                                shizukuStatusMessage = onSilentSelfUpdate()
                                            }) {
                                                Text("Silent Install via Shizuku")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is UpdateCheckResult.Error -> {
                            Text(
                                text = "Update check failed: ${res.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        UpdateCheckResult.Idle -> {
                            // No status yet
                        }
                    }
                }
            }
        }

        // 3. Optional Shizuku Integration
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shizuku Enhanced Mode (Optional • uid=2000)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "1-tap Doze exemption, silent APK install (`pm install -r`), `/Android/data` access, and native helper execution.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = s.shizukuEnabled,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(shizukuEnabled = enabled) } }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { shizukuStatusMessage = onWhitelistDoze() },
                            enabled = s.shizukuEnabled
                        ) {
                            Text("Whitelist Doze")
                        }
                        Button(
                            onClick = { shizukuStatusMessage = onSilentSelfUpdate() },
                            enabled = s.shizukuEnabled
                        ) {
                            Text("Silent Self-Update")
                        }
                    }
                    shizukuStatusMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 4. MDM Themes, 86 Locales & Network / Debrid / Remote Control
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Themes, 86 Languages (RTL Ready), Debrid & Network Security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MdmThemeStyle.entries.forEach { style ->
                            FilterChip(
                                selected = s.themeId == style.id,
                                onClick = { onUpdateSettings { it.copy(themeId = style.id) } },
                                label = { Text(style.title) }
                            )
                        }
                    }
                    Text(
                        text = "Active Locale: ${s.localeCode.uppercase()} (86 upstream TSV catalogs bundled, including RTL Arabic/Hebrew/Persian/Urdu) • DNS: ${s.dnsMode} (${s.customDnsServer}) • Remote Control HTTPS Port: ${s.remoteControlPort}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 5. Creator Credits Summary Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Browser External Download Manager",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "HyperStream supports browser handoff with filename, User-Agent, Referer, and Cookie headers:\n" +
                            "• Vivaldi: Settings → Web Pages → Downloads → External Download Manager → HyperStream\n" +
                            "• Quetta / Firefox / Opera: Enable External Downloader in browser download settings and choose HyperStream\n" +
                            "• Any browser: Tap Share → Download with HyperStream or copy a download URL to auto-probe on '+'",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }.onFailure {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                        }
                    ) {
                        Text("Open Android Default Apps Settings")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = HyperStreamCredits.HEADLINE,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Contact: ${HyperStreamCredits.CREATOR_EMAIL}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = onOpenCredits) {
                        Text("Open Full Credits Page")
                    }
                }
            }
        }
    }
}
