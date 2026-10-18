package com.tiktok7daniel.voceitaliana.asr

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes any audio (or the audio track of a video) Android can play — mp3, m4a/aac, opus/ogg
 * (WhatsApp voice notes), wav, flac, amr, mp4 — into 16 kHz mono float PCM.
 *
 * Audio is streamed out in chunks so hour-long recordings never sit in memory at once.
 */
object AudioDecoder {
    const val SAMPLE_RATE = 16_000

    fun durationMs(context: Context, uri: Uri): Long = try {
        MediaMetadataRetriever().run {
            setDataSource(context, uri)
            val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            release()
            d
        }
    } catch (e: Exception) {
        0L
    }

    /** onChunk gets 16 kHz mono samples; onProgress gets 0..1 through the file. */
    suspend fun decode(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit,
        onChunk: suspend (FloatArray) -> Unit,
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            throw IOException("No audio track")
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var pcmFloat = false
        var resampler = Resampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), SAMPLE_RATE)

        val codec = MediaCodec.createDecoderByType(mime)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            if (durationUs > 0) onProgress(min(1f, extractor.sampleTime.toFloat() / durationUs))
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    pcmFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (rate != resampler.from) resampler = Resampler(rate, SAMPLE_RATE)
                } else if (outIdx >= 0) {
                    val out = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.nativeOrder())
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    val mono: FloatArray
                    if (pcmFloat) {
                        val fb = out.asFloatBuffer()
                        mono = FloatArray(fb.remaining() / channels) {
                            var s = 0f
                            repeat(channels) { s += fb.get() }
                            s / channels
                        }
                    } else {
                        val sb = out.asShortBuffer()
                        mono = FloatArray(sb.remaining() / channels) {
                            var s = 0f
                            repeat(channels) { s += sb.get() / 32768f }
                            s / channels
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    val chunk = resampler.process(mono)
                    if (chunk.isNotEmpty()) onChunk(chunk)
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        onProgress(1f)
    }

    /** Streaming resampler: moving-average low-pass over the decimation ratio, then linear interpolation. */
    private class Resampler(val from: Int, to: Int) {
        private val ratio = from.toDouble() / to
        private val window = if (ratio > 1.0) max(1, floor(ratio).toInt()) else 1
        private val ring = FloatArray(window)
        private var ringSum = 0f
        private var index = -1L // index of the last filtered input sample
        private var prev = 0f
        private var nextPos = 0.0

        fun process(input: FloatArray): FloatArray {
            if (from == SAMPLE_RATE) return input
            val out = FloatArray((input.size / ratio).toInt() + 2)
            var n = 0
            for (x in input) {
                val slot = ((index + 1) % window).toInt()
                ringSum += x - ring[slot]
                ring[slot] = x
                index++
                val cur = ringSum / min(index + 1, window.toLong()).toFloat()
                if (index == 0L) prev = cur
                while (nextPos <= index) {
                    val frac = (nextPos - (index - 1)).toFloat().coerceIn(0f, 1f)
                    if (n == out.size) break
                    out[n++] = prev + (cur - prev) * frac
                    nextPos += ratio
                }
                prev = cur
            }
            return out.copyOf(n)
        }
    }
}
