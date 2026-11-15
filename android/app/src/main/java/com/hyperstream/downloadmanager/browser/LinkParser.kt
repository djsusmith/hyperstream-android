package com.hyperstream.downloadmanager.browser

enum class LinkKind {
    DIRECT_FILE,
    MAGNET,
    TORRENT_FILE,
    VIDEO_STREAM_PAGE,
    GENERIC_URL
}

data class ParsedLinkTarget(
    val uri: String,
    val kind: LinkKind,
    val suggestedName: String? = null
)

object LinkParser {
    private val magnetRegex = Regex("""magnet:\?xt=urn:btih:[A-Za-z0-9%&=\-._~+]+""", RegexOption.IGNORE_CASE)
    private val urlRegex = Regex("""(?:https?|ftps?)://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    private val directExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "xz", "bz2", "zst",
        "apk", "xapk", "apks", "iso", "img", "exe", "msi", "dmg", "deb", "rpm",
        "mp4", "mkv", "webm", "avi", "mov", "flv", "m3u8", "mpd",
        "mp3", "flac", "wav", "m4a", "ogg", "opus",
        "pdf", "epub", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    )

    private val videoDomains = listOf(
        "youtube.com",
        "youtu.be",
        "vimeo.com",
        "dailymotion.com",
        "twitch.tv",
        "tiktok.com",
        "twitter.com",
        "x.com",
        "instagram.com",
        "instagr.am",
        "facebook.com",
        "fb.watch",
        "fb.com",
        "reddit.com",
        "threads.net"
    )

    /** First http(s)/ftp URL inside shared text (captions, hashtags and trailing punctuation stripped). */
    fun extractFirstUrl(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        return urlRegex.find(rawText)?.value?.trimEnd('.', ',', ';', ')', '!', '?')
    }

    /** Human-readable source platform for badges and the format picker. */
    fun detectPlatformName(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("tiktok.com") -> "TikTok"
            lower.contains("instagram.com") || lower.contains("instagr.am") -> "Instagram"
            lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> "Facebook"
            lower.contains("twitter.com") || lower.contains("x.com/") -> "X"
            lower.contains("vimeo.com") -> "Vimeo"
            lower.contains("reddit.com") -> "Reddit"
            lower.startsWith("magnet:") -> "BitTorrent"
            else -> "Direct"
        }
    }

    fun extractDownloadableTarget(rawText: String?): ParsedLinkTarget? {
        if (rawText.isNullOrBlank()) return null

        magnetRegex.find(rawText)?.value?.let { mag ->
            val dn = mag.substringAfter("dn=", "").substringBefore("&").ifBlank { null }
            return ParsedLinkTarget(uri = mag, kind = LinkKind.MAGNET, suggestedName = dn)
        }

        val urlMatch = urlRegex.find(rawText)?.value?.trimEnd('.', ',', ';', ')') ?: return null
        val lower = urlMatch.lowercase()
        val pathPart = lower.substringBefore('?').substringBefore('#')
        val ext = pathPart.substringAfterLast('.', "")

        if (ext == "torrent") {
            return ParsedLinkTarget(
                uri = urlMatch,
                kind = LinkKind.TORRENT_FILE,
                suggestedName = urlMatch.substringBefore('?').substringAfterLast('/')
            )
        }

        if (videoDomains.any { lower.contains(it) }) {
            return ParsedLinkTarget(uri = urlMatch, kind = LinkKind.VIDEO_STREAM_PAGE)
        }

        if (ext in directExtensions || lower.contains("drive.google.com") || lower.contains("mediafire.com")) {
            return ParsedLinkTarget(
                uri = urlMatch,
                kind = LinkKind.DIRECT_FILE,
                suggestedName = urlMatch.substringBefore('?').substringAfterLast('/').ifBlank { null }
            )
        }

        return ParsedLinkTarget(uri = urlMatch, kind = LinkKind.GENERIC_URL)
    }

    fun isLikelyDirectDownload(url: String, mimeType: String? = null): Boolean {
        if (mimeType != null && !mimeType.startsWith("text/html") && !mimeType.startsWith("text/plain")) {
            return true
        }
        val target = extractDownloadableTarget(url) ?: return false
        return target.kind == LinkKind.DIRECT_FILE || target.kind == LinkKind.TORRENT_FILE || target.kind == LinkKind.MAGNET
    }
}
