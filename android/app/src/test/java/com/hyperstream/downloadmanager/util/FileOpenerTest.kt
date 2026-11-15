package com.hyperstream.downloadmanager.util

import com.hyperstream.downloadmanager.core.DownloadItemUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class FileOpenerTest {

    @Test
    fun testMimeTypeDetection() {
        assertEquals("application/vnd.android.package-archive", FileOpener.detectMimeType(File("app-release.apk")))
        assertEquals("video/mp4", FileOpener.detectMimeType(File("video.mp4")))
        assertEquals("video/x-matroska", FileOpener.detectMimeType(File("movie.mkv")))
        assertEquals("audio/mpeg", FileOpener.detectMimeType(File("song.mp3")))
        assertEquals("audio/flac", FileOpener.detectMimeType(File("track.flac")))
        assertEquals("application/pdf", FileOpener.detectMimeType(File("document.pdf")))
        assertEquals("application/zip", FileOpener.detectMimeType(File("archive.zip")))
        assertEquals("application/x-7z-compressed", FileOpener.detectMimeType(File("data.7z")))
        assertEquals("application/x-iso9660-image", FileOpener.detectMimeType(File("ubuntu.iso")))
        assertEquals("*/*", FileOpener.detectMimeType(File("unknown.customextxyz")))
    }

    @Test
    fun testResolveFileFallback() {
        val item = DownloadItemUi(
            id = 42L,
            name = "sample-doc.pdf",
            savePath = "/storage/emulated/0/Download/HyperStream/Documents/sample-doc.pdf",
            status = "Completed"
        )
        val file = FileOpener.resolveFile(item)
        assertNotNull(file)
        assertEquals("/storage/emulated/0/Download/HyperStream/Documents/sample-doc.pdf", file.absolutePath)
    }
}
