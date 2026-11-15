package com.hyperstream.downloadmanager.ui

import com.hyperstream.downloadmanager.core.SegmentUi
import com.hyperstream.downloadmanager.ui.components.SegmentMapCalculator
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
}
