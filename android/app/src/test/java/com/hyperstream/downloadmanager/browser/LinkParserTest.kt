package com.hyperstream.downloadmanager.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
