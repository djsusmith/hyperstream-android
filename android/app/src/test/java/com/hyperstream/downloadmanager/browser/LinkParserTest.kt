package com.hyperstream.downloadmanager.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParserTest {
    @Test
    fun extractsUrlAndMagnetFromSharedText() {
        val shared = "Download ISO here: https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso fast!"
        val target = LinkParser.extractDownloadableTarget(shared)
        assertNotNull(target)
        assertEquals("https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso", target?.uri)
        assertEquals(LinkKind.DIRECT_FILE, target?.kind)

        val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=debian"
        val magnetTarget = LinkParser.extractDownloadableTarget("Here is the magnet $magnet enjoy")
        assertEquals(LinkKind.MAGNET, magnetTarget?.kind)

        assertNull(LinkParser.extractDownloadableTarget("hello world with no link"))
    }

    @Test
    fun identifiesVideoPagesVsDirectArchives() {
        val yt = LinkParser.extractDownloadableTarget("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(LinkKind.VIDEO_STREAM_PAGE, yt?.kind)
    }

    @Test
    fun coldStartLinkDispatchInitializesNativeBeforeExecutingCommand() {
        val events = mutableListOf<String>()
        val dispatched = LinkCatcherDispatcher.dispatchSharedText(
            rawText = "Check https://example.com/archive.zip",
            dataDir = "/tmp/data",
            downloadsDir = "/tmp/dl",
            initFn = { d, dl ->
                events.add("init:$d:$dl")
                true
            },
            executeFn = { cmd ->
                events.add("exec:$cmd")
                """{"ok":true}"""
            }
        )
        assertNotNull(dispatched)
        assertEquals(2, events.size)
        assertTrue(events[0].startsWith("init:"))
        assertTrue(events[1].contains("\"op\":\"add_download\""))
    }

    @Test
    fun videoGrabberRecordsMediaUrlOnMainPosterWithoutDuplicates() {
        val list = mutableListOf<String>()
        var postedOnMain = false
        VideoGrabberMediaTracker.onInterceptedUrl(
            reqUrl = "https://cdn.example.org/video/master.m3u8?token=1",
            detectedMediaUrls = list,
            mainPoster = { block ->
                postedOnMain = true
                block()
            }
        )
        assertTrue(postedOnMain)
        assertEquals(listOf("https://cdn.example.org/video/master.m3u8?token=1"), list)

        // Non-media URL should not be added
        postedOnMain = false
        VideoGrabberMediaTracker.onInterceptedUrl(
            reqUrl = "https://cdn.example.org/style.css",
            detectedMediaUrls = list,
            mainPoster = { block ->
                postedOnMain = true
                block()
            }
        )
        assertFalse(postedOnMain)
        assertEquals(1, list.size)
    }
}
