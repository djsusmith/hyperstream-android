package com.hyperstream.downloadmanager.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hyperstream.downloadmanager.core.DownloadItemUi
import com.hyperstream.downloadmanager.core.MdmRepository
import java.io.File

object FileOpener {

    /**
     * Attempts to resolve the downloaded file across known download paths.
     */
    fun resolveFile(item: DownloadItemUi): File {
        if (item.savePath.isNotBlank()) {
            val direct = File(item.savePath)
            if (direct.exists()) return direct
        }

        val baseDir = File(MdmRepository.DEFAULT_DOWNLOADS_DIR)
        val categorized = File(baseDir, "${item.category}/${item.name}")
        if (categorized.exists()) return categorized

        val flat = File(baseDir, item.name)
        if (flat.exists()) return flat

        val publicDownload = File("/storage/emulated/0/Download", item.name)
        if (publicDownload.exists()) return publicDownload

        // Fallback to designated path even if not present on disk yet
        return if (item.savePath.isNotBlank()) File(item.savePath) else categorized
    }

    /**
     * Determines the appropriate MIME type for an open intent based on file extension.
     */
    fun detectMimeType(file: File): String {
        val ext = file.extension.lowercase()
        val mapped = runCatching {
            MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(ext)
        }.getOrNull()

        return mapped ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "iso" -> "application/x-iso9660-image"
            "txt" -> "text/plain"
            "html" -> "text/html"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "*/*"
        }
    }

    /**
     * Opens the file with the system default viewer or package installer.
     */
    fun openFile(context: Context, item: DownloadItemUi) {
        val file = resolveFile(item)
        if (!file.exists()) {
            Toast.makeText(
                context,
                "File not found: ${item.name}\n(Checked: ${file.absolutePath})",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            val mimeType = detectMimeType(file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (mimeType == "application/vnd.android.package-archive") {
                // APK install flow
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, "Open \"${file.name}\" with...")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No application available to open this file format (${file.extension}).",
                Toast.LENGTH_SHORT
            ).show()
        } catch (t: Throwable) {
            Toast.makeText(
                context,
                "Unable to open file: ${t.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Safely deletes file and its part files from storage.
     */
    fun deletePhysicalFiles(item: DownloadItemUi): Boolean {
        return runCatching {
            val file = resolveFile(item)
            val part = File("${file.absolutePath}.mdm.part")
            var deleted = false
            if (file.exists()) {
                deleted = file.delete() || deleted
            }
            if (part.exists()) {
                part.delete()
            }
            deleted
        }.getOrDefault(false)
    }
}
