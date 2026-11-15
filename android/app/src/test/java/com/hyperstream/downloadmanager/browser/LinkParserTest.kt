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

    @Test
    fun linkParserExtractsSocialUrlsFromSharedTextAndDetectsPlatform() {
        val raw = "Check out this TikTok! https://vm.tiktok.com/ZM8abc123/ #fyp"
        val extracted = LinkParser.extractFirstUrl(raw)
        assertEquals("https://vm.tiktok.com/ZM8abc123/", extracted)
        val target = LinkParser.extractDownloadableTarget(raw)
        assertEquals(LinkKind.VIDEO_STREAM_PAGE, target?.kind)
        assertEquals("TikTok", LinkParser.detectPlatformName(extracted!!))
        assertEquals("Facebook", LinkParser.detectPlatformName("https://fb.watch/xyz123/"))
        assertEquals("Instagram", LinkParser.detectPlatformName("https://www.instagram.com/reel/C9abc/"))
        assertEquals("YouTube", LinkParser.detectPlatformName("https://youtube.com/watch?v=paZ0RGJ0cj0"))
    }

    @Test
    fun parseProbeResultJsonParsesWatermarkFreeAndMp3Streams() {
        val json = """{"ok":true,"platform":"TikTok","title":"Dance Clip","author":"@creator","watermark_free":true,"extractor_version":"2026.11.15","streams":[{"itag":1,"label":"1080p HD MP4 (No Watermark)","quality":"1080p","format_kind":"video","bitrate_kbps":0,"watermark_free":true,"ext":"mp4","category":"Video","file_name":"Dance_Clip_NoWM.mp4","size_bytes":15000000,"url":"https://cdn.tikwm.com/hd.mp4","user_agent":"Mozilla/5.0"},{"itag":320,"label":"MP3 320 kbps (Studio Audio)","quality":"320kbps","format_kind":"mp3","bitrate_kbps":320,"watermark_free":true,"ext":"mp3","category":"Music","file_name":"Dance_Clip (320kbps).mp3","size_bytes":3200000,"url":"https://cdn.tikwm.com/music.mp3","user_agent":"Mozilla/5.0"}]}"""
        val res = com.hyperstream.downloadmanager.core.MdmRepository.parseProbeResultJson(json)
        assertTrue(res.ok)
        assertEquals("TikTok", res.platform)
        assertTrue(res.watermarkFree)
        assertEquals(2, res.streams.size)
        assertEquals("mp3", res.streams[1].formatKind)
        assertEquals(320, res.streams[1].bitrateKbps)
    }
}
