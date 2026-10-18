package com.tiktok7daniel.voceitaliana.asr

import com.k2fsa.sherpa.onnx.OfflineCohereTranscribeModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/**
 * Free, open models packaged for sherpa-onnx (k2-fsa).
 * Every model here handles Italian; the ones that accept a language hint are forced to "it".
 */
enum class AsrModel(
    val title: String,
    val tagline: String,
    val sizeLabel: String,
    val hfRepo: String,
    val files: List<String>,
    // Hugging Face by default; Cohere is only mirrored on ModelScope.
    private val baseUrl: String = "https://huggingface.co/$hfRepo/resolve/main",
) {
    Parakeet(
        title = "Parakeet v3",
        tagline = "NVIDIA · دقيق جداً وسريع، بيحط علامات ترقيم",
        sizeLabel = "≈ 670 MB",
        hfRepo = "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
        files = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
    ),
    WhisperTurbo(
        title = "Whisper Turbo",
        tagline = "OpenAI large-v3-turbo · متثبت على الإيطالي",
        sizeLabel = "≈ 1.04 GB",
        hfRepo = "csukuangfj/sherpa-onnx-whisper-turbo",
        files = listOf("turbo-encoder.int8.onnx", "turbo-decoder.int8.onnx", "turbo-tokens.txt"),
    ),
    Cohere(
        title = "Cohere Transcribe",
        tagline = "الأدق على الإطلاق · محتاج موبايل قوي (8GB رام)",
        sizeLabel = "≈ 2.1 GB",
        hfRepo = "csukuangfj/sherpa-onnx-cohere-transcribe-14-lang-int8-2026-04-01",
        files = listOf("encoder.int8.onnx", "encoder.int8.onnx.data", "decoder.int8.onnx", "tokens.txt"),
        baseUrl = "https://modelscope.cn/models/csukuangfj/sherpa-onnx-cohere-transcribe-14-lang-int8-2026-04-01/resolve/master",
    ),
    WhisperSmall(
        title = "Whisper Small",
        tagline = "خفيف للموبايلات الضعيفة · متثبت على الإيطالي",
        sizeLabel = "≈ 375 MB",
        hfRepo = "csukuangfj/sherpa-onnx-whisper-small",
        files = listOf("small-encoder.int8.onnx", "small-decoder.int8.onnx", "small-tokens.txt"),
    ),
    ;

    fun url(file: String) = "$baseUrl/$file"

    fun dir(root: File) = File(root, name)

    fun isInstalled(root: File) = files.all { File(dir(root), it).isFile }

    fun modelConfig(root: File, threads: Int): OfflineModelConfig {
        val d = dir(root).absolutePath
        return when (this) {
            Parakeet -> OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = "$d/encoder.int8.onnx",
                    decoder = "$d/decoder.int8.onnx",
                    joiner = "$d/joiner.int8.onnx",
                ),
                tokens = "$d/tokens.txt",
                modelType = "nemo_transducer",
                numThreads = threads,
            )
            WhisperTurbo, WhisperSmall -> {
                val p = if (this == WhisperTurbo) "turbo" else "small"
                OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$d/$p-encoder.int8.onnx",
                        decoder = "$d/$p-decoder.int8.onnx",
                        language = "it",
                        task = "transcribe",
                    ),
                    tokens = "$d/$p-tokens.txt",
                    modelType = "whisper",
                    numThreads = threads,
                )
            }
            Cohere -> OfflineModelConfig(
                cohereTranscribe = OfflineCohereTranscribeModelConfig(
                    encoder = "$d/encoder.int8.onnx",
                    decoder = "$d/decoder.int8.onnx",
                    language = "it",
                    usePunct = true,
                    useItn = true,
                ),
                tokens = "$d/tokens.txt",
                numThreads = threads,
            )
        }
    }
}
