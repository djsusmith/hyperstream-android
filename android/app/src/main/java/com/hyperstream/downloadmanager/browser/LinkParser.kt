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

data class ExternalDownloadRequest(
    val url: String,
    val kind: LinkKind,
    val fileName: String? = null,
    val mimeType: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val cookie: String? = null,
    val isExternalBrowserHandoff: Boolean = false
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
        return urlRegex.find(rawText)?.value?.trimEnd('.', ',', ';', ')', '!', '?', '>', '<', '"', '\'')
    }

    /** Extracts raw shared text or URL from any incoming Intent (EXTRA_TEXT, ClipData, EXTRA_SUBJECT, data URI). */
    fun extractRawTextFromIntent(intent: android.content.Intent?): String? {
        if (intent == null) return null
        val extraText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
        if (!extraText.isNullOrBlank()) return extraText

        val charSeq = intent.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT)?.toString()
        if (!charSeq.isNullOrBlank()) return charSeq

        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) {
            for (i in 0 until clip.itemCount) {
                val item = clip.getItemAt(i)
                val itemText = item.text?.toString() ?: item.uri?.toString()
                if (!itemText.isNullOrBlank()) return itemText
            }
        }

        val subject = intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT)
        if (!subject.isNullOrBlank()) return subject

        val data = intent.dataString
        if (!data.isNullOrBlank()) return data

        return null
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

    /**
     * Parses a download handoff Intent from browsers with External Download Manager support
     * (Vivaldi, Quetta, Firefox, Opera, ADM/1DM protocol) or standard Share/Open-with intents.
     */
    fun extractExternalDownloadRequest(intent: android.content.Intent?): ExternalDownloadRequest? {
        if (intent == null) return null
        val raw = extractRawTextFromIntent(intent)
        val target = extractDownloadableTarget(raw) ?: return null

        val mime = intent.type?.takeIf {
            it.isNotBlank() && it != "*/*" && !it.startsWith("text/plain", ignoreCase = true)
        }

        val extras = intent.extras
        fun firstExtra(vararg keys: String): String? {
            if (extras == null) return null
            for (k in keys) {
                val v = extras.get(k)?.toString()?.trim()
                if (!v.isNullOrBlank()) return v
            }
            return null
        }

        val browserHeaders = extras?.getBundle("com.android.browser.headers")
        fun headerExtra(vararg keys: String): String? {
            if (browserHeaders != null) {
                for (k in keys) {
                    val v = browserHeaders.getString(k)?.trim()
                    if (!v.isNullOrBlank()) return v
                }
                for (bk in browserHeaders.keySet()) {
                    if (keys.any { it.equals(bk, ignoreCase = true) }) {
                        val v = browserHeaders.getString(bk)?.trim()
                        if (!v.isNullOrBlank()) return v
                    }
                }
            }
            return firstExtra(*keys)
        }

        val suggestedFileName = firstExtra(
            android.content.Intent.EXTRA_TITLE,
            "filename",
            "file_name",
            "extra_filename",
            "name",
            "com.dv.adm.EXTRA_FILENAME"
        )?.takeIf { !it.startsWith("http://", true) && !it.startsWith("https://", true) }
            ?: target.suggestedName

        val userAgent = headerExtra("User-Agent", "user_agent", "useragent", "HTTP_USER_AGENT")
        val referer = headerExtra(
            "Referer",
            "referer",
            android.content.Intent.EXTRA_REFERRER_NAME,
            android.content.Intent.EXTRA_REFERRER
        )
        val cookie = headerExtra("Cookie", "cookie", "cookies")

        val isBrowserHandoff = intent.action == android.content.Intent.ACTION_VIEW &&
            (mime != null || userAgent != null || referer != null || cookie != null ||
                target.kind == LinkKind.DIRECT_FILE)

        return ExternalDownloadRequest(
            url = target.uri,
            kind = target.kind,
            fileName = suggestedFileName,
            mimeType = mime,
            userAgent = userAgent,
            referer = referer,
            cookie = cookie,
            isExternalBrowserHandoff = isBrowserHandoff
        )
    }
}
