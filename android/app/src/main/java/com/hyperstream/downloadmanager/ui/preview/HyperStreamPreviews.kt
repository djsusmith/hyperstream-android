package com.hyperstream.downloadmanager.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.ui.HyperStreamAppContent
import com.hyperstream.downloadmanager.ui.screens.ProgressInspectorSheet
import com.hyperstream.downloadmanager.ui.theme.HyperStreamTheme

@Preview(showBackground = true, widthDp = 412, heightDp = 892, name = "1. Main Downloads (128 Threads)")
@Composable
fun MainDownloadsDashboardPreview() {
    HyperStreamAppContent(
        state = MdmRepository.sampleInitialState(),
        initialTab = 0
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 520, name = "2. 128-Thread Segment Inspector")
@Composable
fun ProgressInspector128ThreadsPreview() {
    val sample = MdmRepository.sampleInitialState().downloads.first()
    HyperStreamTheme(themeId = "classic", darkModePref = "dark") {
        ProgressInspectorSheet(item = sample)
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892, name = "3. BitTorrent Swarm & RSS")
@Composable
fun TorrentSwarmInspectorPreview() {
    HyperStreamAppContent(
        state = MdmRepository.sampleInitialState(),
        initialTab = 1
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892, name = "4. Video & Batch Grabber")
@Composable
fun VideoAndBatchGrabberPreview() {
    HyperStreamAppContent(
        state = MdmRepository.sampleInitialState(),
        initialTab = 2
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892, name = "5. Queues & Overnight Scheduler")
@Composable
fun QueuesAndSchedulerPreview() {
    HyperStreamAppContent(
        state = MdmRepository.sampleInitialState(),
        initialTab = 3
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892, name = "6. Settings, Shizuku & Upstream Updater")
@Composable
fun SettingsShizukuUpdaterPreview() {
    HyperStreamAppContent(
        state = MdmRepository.sampleInitialState(),
        initialTab = 4
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892, name = "7. Credits — Ahnaf Hasan Susmith")
@Composable
fun CreditsPagePreview() {
    HyperStreamAppContent(
        state = MdmRepository.sampleInitialState(),
        initialTab = 5
    )
}
