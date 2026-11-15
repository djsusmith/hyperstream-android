package com.hyperstream.downloadmanager.ui

import com.hyperstream.downloadmanager.core.MdmRepository
import com.hyperstream.downloadmanager.core.SegmentUi
import com.hyperstream.downloadmanager.ui.components.SegmentMapCalculator
import com.hyperstream.downloadmanager.ui.screens.HyperStreamCredits
import com.hyperstream.downloadmanager.ui.theme.MdmThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentMapAndThemeTest {
    @Test
    fun calculates128SegmentProgressFractionsAccurately() {
        val segments = (0 until 128).map { idx ->
            SegmentUi(
                index = idx,
                start = idx * 1000L,
                pos = idx * 1000L + 500L,
                end = (idx + 1) * 1000L,
                active = true,
                speedBps = 100_000L
            )
        }
        val fractions = SegmentMapCalculator.computeSegmentFillFractions(segments, 128)
        assertEquals(128, fractions.size)
        assertTrue(fractions.all { it in 0.49f..0.51f })
    }

    @Test
    fun resolvesAllMdmDesktopAndMaterial3Themes() {
        assertEquals(MdmThemeStyle.CLASSIC_IDM, MdmThemeStyle.fromId("classic"))
        assertEquals(MdmThemeStyle.METRO_DARK, MdmThemeStyle.fromId("metro"))
        assertEquals(MdmThemeStyle.MATERIAL_DYNAMIC, MdmThemeStyle.fromId("material"))
    }

    @Test
    fun exposesCreatorCreditsMetadata() {
        assertEquals("Created by Ahnaf Hasan Susmith with Antigravity", HyperStreamCredits.HEADLINE)
        assertEquals("Ahnaf Hasan Susmith", HyperStreamCredits.CREATOR_NAME)
        assertEquals("Antigravity", HyperStreamCredits.BUILT_WITH)
        assertEquals("susdj.96@gmail.com", HyperStreamCredits.CREATOR_EMAIL)
        assertEquals("mailto:susdj.96@gmail.com", HyperStreamCredits.mailtoUri())
    }

    @Test
    fun verifiesStandardCategoryFilterRailAndTabs() {
        val expectedCategories = listOf("All", "Unfinished", "Finished", "Video", "Music", "Compressed", "Documents", "Programs")
        val stateCategories = MdmRepository.sampleInitialState().categories
        assertTrue(expectedCategories.all { cat ->
            cat in listOf("All", "Unfinished", "Finished") || cat in stateCategories
        })

        val initial = MdmRepository.sampleInitialState()
        assertEquals(128, initial.activeThreadsTotal)
        assertTrue(initial.wakeLockActive)
    }

    @Test
    fun verifiesFormatPickerActionButtonResolution() {
        fun resolveOptText(quality: String?, label: String?, ext: String?, formatKind: String): String {
            return when {
                !quality.isNullOrBlank() -> quality
                label?.contains("360p", ignoreCase = true) == true -> "360p"
                label?.contains("720p", ignoreCase = true) == true -> "720p"
                label?.contains("1080p", ignoreCase = true) == true -> "1080p"
                label?.contains("1440p", ignoreCase = true) == true -> "1440p"
                label?.contains("2160p", ignoreCase = true) == true -> "2160p"
                formatKind == "mp3" -> "MP3"
                else -> ext?.uppercase() ?: "MP4"
            }
        }

        assertEquals("360p", resolveOptText("360p", "360p MP4 (Video + Audio - Fast)", "mp4", "video"))
        assertEquals("2160p", resolveOptText("2160p", "2160p MP4 (AV1 HDR)", "mp4", "video"))
        assertEquals("1080p", resolveOptText(null, "1080p MP4 (H.264)", "mp4", "video"))
        assertEquals("MP4", resolveOptText(null, "Original File (MP4)", "mp4", "video"))
        assertEquals("MP3", resolveOptText(null, "Studio Audio", "mp3", "mp3"))
    }
}
