package com.hyperstream.downloadmanager.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hyperstream.downloadmanager.core.ProbeResultUi
import com.hyperstream.downloadmanager.core.ProbedStreamUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Hidden headless WebView extractor for YouTube share-to-download.
 *
 * YouTube GVS throttles or rejects (`HTTP 403`) unauthenticated InnerTube `ANDROID`/`IOS`
 * playback URLs past the initial ~60s buffer when they lack a BotGuard PO Token (`pot`).
 * Running `m.youtube.com/watch?v=<id>` briefly inside Chromium executes `base.js` + BotGuard
 * natively and yields `videoplayback` URLs with valid `pot`, `n`, and `sig`.
 */
object YouTubeWebViewExtractor {
    private const val TAG = "HyperStreamYT"
    const val CHROME_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"

    private val EPHEMERAL_PARAMS = setOf("range", "rn", "rbuf", "ump", "srfvp", "alr")

    private val hostActivityRef = AtomicReference<WeakReference<Activity>?>(null)

    fun attachHost(activity: Activity) {
        hostActivityRef.set(WeakReference(activity))
    }

    fun hasHost(): Boolean {
        val act = hostActivityRef.get()?.get()
        return act != null && !act.isFinishing && !act.isDestroyed
    }

    fun extractVideoId(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!host.contains("youtube.com") && !host.contains("youtu.be") && !host.contains("youtube-nocookie.com")) {
            return null
        }
        if (host.contains("youtu.be")) {
            return uri.pathSegments.firstOrNull()?.takeIf { it.isValidVideoId() }
        }
        uri.getQueryParameter("v")?.takeIf { it.isValidVideoId() }?.let { return it }
        val segs = uri.pathSegments
        for (prefix in listOf("shorts", "live", "embed", "v")) {
            val idx = segs.indexOf(prefix)
            if (idx >= 0 && idx + 1 < segs.size) {
                val cand = segs[idx + 1]
                if (cand.isValidVideoId()) return cand
            }
        }
        return null
    }

    private fun String.isValidVideoId(): Boolean =
        length == 11 && all { it.isLetterOrDigit() || it == '-' || it == '_' }

    fun normalizeGooglevideoUrl(rawUrl: String): String {
        val qIdx = rawUrl.indexOf('?')
        if (qIdx < 0) return rawUrl
        val base = rawUrl.substring(0, qIdx)
        val query = rawUrl.substring(qIdx + 1)
        val kept = query.split('&').filter { pair ->
            if (pair.isEmpty()) return@filter false
            val eqIdx = pair.indexOf('=')
            val key = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
            key !in EPHEMERAL_PARAMS
        }
        return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
    }

    private data class CapturedStream(
        val itag: Int,
        val cleanUrl: String,
        val mime: String,
        val contentLength: Long,
        val hasPot: Boolean,
        val method: String,
        val pot: String?
    )

    private data class FormatMeta(
        val qualityLabel: String,
        val mime: String,
        val contentLength: Long,
        val bitrateKbps: Int
    )

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(videoId: String): ProbeResultUi? {
        val activity = hostActivityRef.get()?.get()
            ?.takeUnless { it.isFinishing || it.isDestroyed }
            ?: return null

        val captured = ConcurrentHashMap<Int, CapturedStream>()
        val pageTitle = AtomicReference("")
        val pageAuthor = AtomicReference("")
        val metaByItag = ConcurrentHashMap<Int, FormatMeta>()
        val pageFinished = AtomicBoolean(false)

        return withTimeoutOrNull(9_000L) {
            withContext(Dispatchers.Main) {
                val decor = activity.window?.decorView as? ViewGroup
                val webView = WebView(activity).apply {
                    // Use a real mobile viewport shifted offscreen so Chromium + m.youtube.com
                    // initialize <video> and BotGuard identically to the visible in-app browser.
                    alpha = 0.01f
                    translationX = -10_000f
                    isFocusable = false
                    isFocusableInTouchMode = false
                    layoutParams = ViewGroup.LayoutParams(480, 800)
                }
                decor?.addView(webView)
                Log.d(TAG, "Starting offscreen WebView extraction for videoId=$videoId")

                try {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        userAgentString = CHROME_MOBILE_UA
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            // Force YouTube mobile player out of SABR/MSE protobuf mode into direct progressive HTTP videoplayback
                            // so Chromium solves n=/sig= and mints a real BotGuard PO Token (pot=) on a standard GET+Range MP4 URL.
                            view?.evaluateJavascript(
                                "try{window.MediaSource=undefined;window.ManagedMediaSource=undefined;}catch(e){}",
                                null
                            )
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageFinished.set(true)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            if (!reqUrl.contains("googlevideo.com")) return null

                            val uri = request.url
                            val method = request.method ?: "GET"
                            val rawItag = uri.getQueryParameter("itag")?.toIntOrNull()
                            val pot = uri.getQueryParameter("pot")?.takeIf { it.isNotBlank() }
                            val mime = uri.getQueryParameter("mime")?.lowercase().orEmpty()
                            val clen = uri.getQueryParameter("clen")?.toLongOrNull() ?: 0L
                            val isSabr = uri.getQueryParameter("sabr") == "1" || uri.getQueryParameter("ump") == "1"
                            Log.d(
                                TAG,
                                "Intercepted googlevideo method=$method itag=$rawItag hasPot=${pot != null} sabr=$isSabr clen=$clen mime=$mime"
                            )

                            if (!reqUrl.contains("/videoplayback")) return null
                            // Skip SABR binary protobuf POST endpoints (cannot be downloaded via plain HTTP GET).
                            if (method.equals("POST", ignoreCase = true) && rawItag == null) return null

                            val itag = rawItag ?: 18
                            val clean = normalizeGooglevideoUrl(reqUrl)

                            val existing = captured[itag]
                            // Prefer entries with PO Token (`pot`) and HTTP GET.
                            val shouldReplace = existing == null ||
                                (!existing.hasPot && pot != null) ||
                                (existing.hasPot == (pot != null) && existing.method != "GET" && method == "GET")
                            if (shouldReplace) {
                                captured[itag] = CapturedStream(
                                    itag = itag,
                                    cleanUrl = clean,
                                    mime = mime,
                                    contentLength = clen,
                                    hasPot = pot != null,
                                    method = method,
                                    pot = pot
                                )
                            }
                            return null
                        }
                    }

                    webView.loadUrl("https://m.youtube.com/watch?v=$videoId")

                    val probeJs = """
                        (function(){
                          var v = document.querySelector('video');
                          if (v) {
                            v.muted = true;
                            v.volume = 0;
                            if (v.paused) { try { v.play(); } catch(e){} }
                          }
                          var btn = document.querySelector('.ytp-large-play-button, button[aria-label="Play"], button[aria-label*="Play video"]');
                          if (btn && (!v || v.paused)) { try { btn.click(); } catch(e){} }
                          var p = document.getElementById('movie_player');
                          if (p && typeof p.setPlaybackQualityRange === 'function') {
                            try { p.setPlaybackQualityRange('hd1080', 'hd1080'); } catch(e){}
                          }
                          var pr = window.ytInitialPlayerResponse;
                          var title = (pr && pr.videoDetails && pr.videoDetails.title) ? pr.videoDetails.title : (document.title || '');
                          var author = (pr && pr.videoDetails && pr.videoDetails.author) ? pr.videoDetails.author : '';
                          var fmts = [];
                          if (pr && pr.streamingData) {
                            var all = (pr.streamingData.formats || []).concat(pr.streamingData.adaptiveFormats || []);
                            for (var i = 0; i < all.length; i++) {
                              var f = all[i];
                              if (f && f.itag) {
                                fmts.push({
                                  itag: f.itag,
                                  q: f.qualityLabel || '',
                                  mime: f.mimeType || '',
                                  len: Number(f.contentLength || 0),
                                  br: Number(f.bitrate || 0)
                                });
                              }
                            }
                          }
                          var videoSrc = (v && (v.currentSrc || v.src)) ? String(v.currentSrc || v.src) : '';
                          return JSON.stringify({ title: title, author: author, videoSrc: videoSrc, formats: fmts });
                        })();
                    """.trimIndent()

                    var firstVideoAt = 0L
                    val deadline = System.currentTimeMillis() + 7_800L
                    while (System.currentTimeMillis() < deadline) {
                        webView.evaluateJavascript(probeJs) { raw ->
                            parseJsMeta(raw, pageTitle, pageAuthor, metaByItag, captured)
                        }

                        val hasVideo = captured.values.any { !it.mime.startsWith("audio/") }
                        val hasVideoWithPot = captured.values.any {
                            !it.mime.startsWith("audio/") && it.hasPot
                        }
                        val hasHdStream = captured.values.any {
                            !it.mime.startsWith("audio/") && it.hasPot && it.itag != 18
                        }
                        val now = System.currentTimeMillis()
                        if ((hasVideoWithPot || hasVideo) && firstVideoAt == 0L) {
                            firstVideoAt = now
                        }
                        if (hasHdStream && now - firstVideoAt >= 800L) {
                            break
                        }
                        if (hasVideoWithPot && now - firstVideoAt >= 1_800L) {
                            break
                        }
                        if (hasVideo && now - firstVideoAt >= 500L) {
                            break
                        }
                        delay(250L)
                    }

                    Log.d(TAG, "Finished extraction videoId=$videoId captured=${captured.size} itags=${captured.keys}")
                    buildProbeResult(
                        videoId = videoId,
                        captured = captured.values.toList(),
                        rawTitle = pageTitle.get(),
                        rawAuthor = pageAuthor.get(),
                        metaByItag = metaByItag
                    )
                } finally {
                    runCatching {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView.destroy()
                    }
                }
            }
        }
    }

    private fun parseJsMeta(
        rawResult: String?,
        titleOut: AtomicReference<String>,
        authorOut: AtomicReference<String>,
        metaOut: ConcurrentHashMap<Int, FormatMeta>,
        capturedOut: ConcurrentHashMap<Int, CapturedStream>
    ) {
        if (rawResult.isNullOrBlank() || rawResult == "null") return
        runCatching {
            // evaluateJavascript returns a JSON-encoded string literal.
            val unescaped = org.json.JSONTokener(rawResult).nextValue()?.toString() ?: return
            val obj = JSONObject(unescaped)
            val title = obj.optString("title", "").removeSuffix(" - YouTube").trim()
            if (title.isNotBlank()) titleOut.set(title)
            val author = obj.optString("author", "").trim()
            if (author.isNotBlank()) authorOut.set(author)
            val videoSrc = obj.optString("videoSrc", "").trim()
            if (videoSrc.contains("googlevideo.com/videoplayback")) {
                val uri = Uri.parse(videoSrc)
                val itag = uri.getQueryParameter("itag")?.toIntOrNull() ?: 18
                val pot = uri.getQueryParameter("pot")?.takeIf { it.isNotBlank() }
                val mime = uri.getQueryParameter("mime")?.lowercase().orEmpty()
                val clen = uri.getQueryParameter("clen")?.toLongOrNull() ?: 0L
                val existing = capturedOut[itag]
                if (existing == null || (!existing.hasPot && pot != null)) {
                    capturedOut[itag] = CapturedStream(
                        itag = itag,
                        cleanUrl = normalizeGooglevideoUrl(videoSrc),
                        mime = mime,
                        contentLength = clen,
                        hasPot = pot != null,
                        method = "GET",
                        pot = pot
                    )
                    Log.d(TAG, "Captured video.currentSrc itag=$itag hasPot=${pot != null} clen=$clen")
                }
            }
            val fmts = obj.optJSONArray("formats") ?: return
            for (i in 0 until fmts.length()) {
                val f = fmts.optJSONObject(i) ?: continue
                val itag = f.optInt("itag", 0)
                if (itag <= 0) continue
                metaOut[itag] = FormatMeta(
                    qualityLabel = f.optString("q", ""),
                    mime = f.optString("mime", ""),
                    contentLength = f.optLong("len", 0L),
                    bitrateKbps = (f.optLong("br", 0L) / 1000L).toInt()
                )
            }
        }
    }

    private fun buildProbeResult(
        videoId: String,
        captured: List<CapturedStream>,
        rawTitle: String,
        rawAuthor: String,
        metaByItag: Map<Int, FormatMeta>
    ): ProbeResultUi? {
        if (captured.isEmpty()) return null
        val title = rawTitle.ifBlank { "YouTube_$videoId" }
        val safeBase = sanitizeFilename(title)
        val streams = captured
            .sortedWith(
                compareByDescending<CapturedStream> { !it.mime.startsWith("audio/") }
                    .thenByDescending { it.hasPot }
                    .thenByDescending { it.itag == 18 }
                    .thenByDescending { it.contentLength }
            )
            .map { cap ->
                val meta = metaByItag[cap.itag]
                val effectiveMime = cap.mime.ifBlank { meta?.mime?.lowercase().orEmpty() }
                val isAudio = effectiveMime.startsWith("audio/")
                val ext = when {
                    effectiveMime.contains("audio/mp4") -> "m4a"
                    effectiveMime.contains("webm") -> "webm"
                    isAudio -> "m4a"
                    else -> "mp4"
                }
                val quality = when {
                    !meta?.qualityLabel.isNullOrBlank() -> meta!!.qualityLabel
                    cap.itag == 18 -> "360p"
                    cap.itag == 22 -> "720p"
                    cap.itag in listOf(137, 248, 299, 303) -> "1080p"
                    cap.itag in listOf(136, 247, 298, 302) -> "720p"
                    cap.itag in listOf(135, 244) -> "480p"
                    isAudio -> "Audio"
                    else -> ext.uppercase()
                }
                val size = if (cap.contentLength > 0) cap.contentLength else (meta?.contentLength ?: 0L)
                val suffixTag = if (cap.hasPot) "Verified Stream" else "Browser Stream"
                val label = if (isAudio) {
                    "YouTube Audio • $suffixTag (${ext.uppercase()})"
                } else {
                    val muxNote = if (cap.itag == 18 || cap.itag == 22) "Muxed" else "Video"
                    "$quality $muxNote • $suffixTag (${ext.uppercase()})"
                }
                ProbedStreamUi(
                    itag = cap.itag,
                    label = label,
                    quality = quality,
                    formatKind = if (isAudio) "audio" else "video",
                    bitrateKbps = meta?.bitrateKbps ?: 0,
                    watermarkFree = true,
                    ext = ext,
                    category = if (isAudio) "Music" else "Video",
                    fileName = "${safeBase}_$quality.$ext",
                    sizeBytes = size,
                    url = cap.cleanUrl,
                    userAgent = CHROME_MOBILE_UA
                )
            }

        return ProbeResultUi(
            ok = true,
            platform = "YouTube",
            title = title,
            author = rawAuthor.ifBlank { "YouTube" },
            watermarkFree = true,
            extractorVersion = "webview-pot-v1",
            streams = streams
        )
    }

    private fun sanitizeFilename(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
        return cleaned.ifBlank { "YouTube_Video" }.take(64)
    }
}
