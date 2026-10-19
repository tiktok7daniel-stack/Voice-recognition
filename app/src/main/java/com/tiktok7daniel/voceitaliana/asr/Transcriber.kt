package com.tiktok7daniel.voceitaliana.asr

import android.content.Context
import android.net.Uri
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import java.io.File

data class Segment(val start: Float, val end: Float, val text: String)

/**
 * Pipeline: decode file -> Silero VAD cuts speech into short utterances -> each utterance is
 * recognized by the selected model. Silence is never sent to the model, which avoids the
 * "hallucinated" sentences Whisper-style decoders produce on quiet parts.
 */
class Transcriber(private val context: Context, private val modelsRoot: File) {
    // One recognizer per model, shared by every running job: sherpa-onnx decoding is
    // thread-safe across streams, so two files can be transcribed at the same time.
    private val recognizers = HashMap<AsrModel, OfflineRecognizer>()

    private fun threads() = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    @Synchronized
    fun load(model: AsrModel): OfflineRecognizer = recognizers.getOrPut(model) {
        // Keep only one big model in memory at a time.
        recognizers.values.forEach { it.release() }
        recognizers.clear()
        val config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = AudioDecoder.SAMPLE_RATE, featureDim = 80),
            modelConfig = model.modelConfig(modelsRoot, threads()),
            decodingMethod = "greedy_search",
        )
        OfflineRecognizer(null, config)
    }

    @Synchronized
    fun release() {
        recognizers.values.forEach { it.release() }
        recognizers.clear()
    }

    suspend fun transcribe(
        uri: Uri,
        model: AsrModel,
        onProgress: (Float) -> Unit,
        onSegment: (Segment) -> Unit,
    ) {
        val rec = load(model)
        val isWhisper = model == AsrModel.WhisperTurbo || model == AsrModel.WhisperSmall
        val vad = Vad(
            context.assets,
            VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "silero_vad.onnx",
                    threshold = 0.45f,
                    minSilenceDuration = 0.35f,
                    minSpeechDuration = 0.25f,
                    windowSize = WINDOW,
                    // Whisper is limited to 30 s windows; the others are happiest with sentence-sized chunks.
                    maxSpeechDuration = if (isWhisper) 25f else 20f,
                ),
                sampleRate = AudioDecoder.SAMPLE_RATE,
                numThreads = 1,
            ),
        )

        fun drain() {
            while (!vad.empty()) {
                val seg = vad.front()
                vad.pop()
                val stream = rec.createStream()
                // Short padding on both sides helps the models with clipped first/last syllables.
                val pad = FloatArray(AudioDecoder.SAMPLE_RATE / 5)
                stream.acceptWaveform(pad + seg.samples + pad, AudioDecoder.SAMPLE_RATE)
                rec.decode(stream)
                val text = rec.getResult(stream).text.trim()
                stream.release()
                if (text.isNotEmpty() && !isNoise(text)) {
                    val start = seg.start.toFloat() / AudioDecoder.SAMPLE_RATE
                    onSegment(Segment(start, start + seg.samples.size.toFloat() / AudioDecoder.SAMPLE_RATE, text))
                }
            }
        }

        val pending = FloatArray(WINDOW)
        var filled = 0
        try {
            AudioDecoder.decode(context, uri, onProgress) { chunk ->
                var i = 0
                while (i < chunk.size) {
                    val n = minOf(WINDOW - filled, chunk.size - i)
                    System.arraycopy(chunk, i, pending, filled, n)
                    filled += n
                    i += n
                    if (filled == WINDOW) {
                        vad.acceptWaveform(pending.copyOf())
                        filled = 0
                        drain()
                    }
                }
            }
            if (filled > 0) vad.acceptWaveform(pending.copyOf(filled))
            vad.flush()
            drain()
        } finally {
            vad.release()
        }
    }

    // Typical filler that ASR models emit for music/noise-only fragments.
    private fun isNoise(text: String): Boolean {
        val t = text.lowercase().trim(' ', '.', '!', '?', '…')
        return t.isEmpty() || t in setOf("sottotitoli a cura di qtss", "grazie per la visione", "[musica]", "musica")
    }

    companion object {
        private const val WINDOW = 512
    }
}
