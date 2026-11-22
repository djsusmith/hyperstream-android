package com.hyperstream.downloadmanager.core

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Losslessly muxes YouTube adaptive `video/mp4` (1080p/720p/4K) and companion `audio/mp4`
 * (`<savePath>.audio.m4a`, itag 140 AAC) into a single playable `.mp4` using Android's
 * built-in `MediaExtractor` and `MediaMuxer` (zero re-encoding).
 */
object YouTubeMp4Muxer {
    private const val TAG = "HyperStreamMuxer"
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun hasPendingCompanionAudio(videoPath: String): Boolean {
        if (videoPath.isBlank()) return false
        val audioFile = File("$videoPath.audio.m4a")
        val partFile = File("$videoPath.audio.m4a.mdm.part")
        return audioFile.exists() && audioFile.length() > 0L && !partFile.exists() && videoPath !in inFlight
    }

    @SuppressLint("WrongConstant")
    fun muxCompanionAudioInPlace(videoPath: String): Boolean {
        if (!inFlight.add(videoPath)) return false
        val videoFile = File(videoPath)
        val audioFile = File("$videoPath.audio.m4a")
        val tempOut = File("$videoPath.muxed.tmp.mp4")

        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            if (!videoFile.exists() || !audioFile.exists()) return false
            if (tempOut.exists()) tempOut.delete()

            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoTrackIndex = i
                    videoFormat = fmt
                    break
                }
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }

            if (videoTrackIndex < 0 || videoFormat == null || audioTrackIndex < 0 || audioFormat == null) {
                Log.w(TAG, "Missing video ($videoTrackIndex) or audio ($audioTrackIndex) track; skipping mux")
                audioFile.delete()
                return false
            }

            muxer = MediaMuxer(tempOut.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxVideoTrack = muxer.addTrack(videoFormat)
            val muxAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()

            videoExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (true) {
                info.offset = 0
                info.size = videoExtractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = videoExtractor.sampleTime
                info.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxVideoTrack, buffer, info)
                videoExtractor.advance()
            }

            audioExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (true) {
                info.offset = 0
                info.size = audioExtractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = audioExtractor.sampleTime
                info.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(muxAudioTrack, buffer, info)
                audioExtractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            if (tempOut.exists() && tempOut.length() > videoFile.length()) {
                if (videoFile.delete()) {
                    tempOut.renameTo(videoFile)
                } else {
                    tempOut.copyTo(videoFile, overwrite = true)
                    tempOut.delete()
                }
                audioFile.delete()
                Log.d(TAG, "Successfully muxed video + audio into ${videoFile.name} (${videoFile.length()} bytes)")
                return true
            } else {
                tempOut.delete()
                audioFile.delete()
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaMuxer failed for $videoPath (${e.message}); keeping video stream", e)
            runCatching { tempOut.delete() }
            runCatching { audioFile.delete() }
            return false
        } finally {
            runCatching { videoExtractor?.release() }
            runCatching { audioExtractor?.release() }
            runCatching { muxer?.release() }
            inFlight.remove(videoPath)
        }
    }
}
