package com.tiktok7daniel.voceitaliana

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tiktok7daniel.voceitaliana.asr.AsrModel
import com.tiktok7daniel.voceitaliana.asr.AudioDecoder
import com.tiktok7daniel.voceitaliana.asr.ModelDownloader
import com.tiktok7daniel.voceitaliana.asr.Segment
import com.tiktok7daniel.voceitaliana.asr.Transcriber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Phase {
    data object Idle : Phase
    data class Downloading(val model: AsrModel, val done: Long, val total: Long) : Phase
    data object Loading : Phase
    data class Working(val progress: Float) : Phase
    data class Done(val seconds: Float) : Phase
    data class Failed(val title: String, val detail: String) : Phase
}

data class PickedFile(val uri: Uri, val name: String, val durationMs: Long)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("voce", Context.MODE_PRIVATE)
    private val modelsRoot = File(app.filesDir, "models")
    private val transcriber = Transcriber(app, modelsRoot)
    private var job: Job? = null

    var model by mutableStateOf(
        runCatching { AsrModel.valueOf(prefs.getString("model", null) ?: "") }.getOrDefault(AsrModel.Parakeet),
    )
        private set
    val installed = mutableStateMapOf<AsrModel, Boolean>()
    var file by mutableStateOf<PickedFile?>(null)
        private set
    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set
    val segments = mutableStateListOf<Segment>()
    var showTimestamps by mutableStateOf(false)
    var text by mutableStateOf("")

    val busy get() = phase is Phase.Downloading || phase is Phase.Loading || phase is Phase.Working

    init {
        refreshInstalled()
    }

    private fun refreshInstalled() {
        AsrModel.entries.forEach { installed[it] = it.isInstalled(modelsRoot) }
    }

    fun select(m: AsrModel) {
        if (busy) return
        model = m
        prefs.edit().putString("model", m.name).apply()
    }

    fun pick(uri: Uri) {
        if (busy) return
        val ctx = getApplication<Application>()
        val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "audio"
        file = PickedFile(uri, name, AudioDecoder.durationMs(ctx, uri))
        phase = Phase.Idle
    }

    fun download() {
        if (busy) return
        val m = model
        job = viewModelScope.launch {
            phase = Phase.Downloading(m, 0, -1)
            try {
                ModelDownloader.download(m, modelsRoot) { done, total ->
                    phase = Phase.Downloading(m, done, total)
                }
                refreshInstalled()
                phase = Phase.Idle
            } catch (e: CancellationException) {
                phase = Phase.Idle
            } catch (e: Exception) {
                phase = Phase.Failed("التحميل وقف", "اتأكد من النت وجرب تاني — هيكمّل من مكان ما وقف. (${e.message})")
            }
        }
    }

    fun deleteModel(m: AsrModel) {
        if (busy) return
        transcriber.release()
        ModelDownloader.delete(m, modelsRoot)
        refreshInstalled()
    }

    fun start() {
        val f = file ?: return
        if (busy || installed[model] != true) return
        val m = model
        segments.clear()
        text = ""
        job = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                phase = Phase.Loading
                withContext(Dispatchers.Default) { transcriber.load(m) }
                phase = Phase.Working(0f)
                withContext(Dispatchers.Default) {
                    transcriber.transcribe(
                        f.uri, m,
                        onProgress = { p -> launch(Dispatchers.Main) { if (phase is Phase.Working) phase = Phase.Working(p) } },
                        onSegment = { s ->
                            launch(Dispatchers.Main) {
                                segments.add(s)
                                text = render()
                            }
                        },
                    )
                }
                phase = Phase.Done((System.currentTimeMillis() - t0) / 1000f)
            } catch (e: CancellationException) {
                phase = Phase.Idle
            } catch (e: OutOfMemoryError) {
                transcriber.release()
                phase = Phase.Failed("الرام مش كفاية", "جرّب موديل أخف زي Parakeet v3 أو Whisper Small")
            } catch (e: Exception) {
                phase = Phase.Failed("حصل خطأ", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun toggleTimestamps() {
        showTimestamps = !showTimestamps
        if (segments.isNotEmpty()) text = render()
    }

    private fun render(): String =
        if (showTimestamps) {
            segments.joinToString("\n") { "[${clock(it.start)}] ${it.text}" }
        } else {
            paragraphs()
        }

    /** Joins utterances into paragraphs, breaking where the speaker paused for a while. */
    private fun paragraphs(): String {
        val sb = StringBuilder()
        segments.forEachIndexed { i, s ->
            if (i > 0) sb.append(if (s.start - segments[i - 1].end > 1.5f) "\n\n" else " ")
            sb.append(s.text)
        }
        return sb.toString()
    }

    fun srt(): String = segments.mapIndexed { i, s ->
        "${i + 1}\n${srtTime(s.start)} --> ${srtTime(s.end)}\n${s.text}\n"
    }.joinToString("\n")

    fun baseName(): String = file?.name?.substringBeforeLast('.')?.ifBlank { null } ?: "trascrizione"

    override fun onCleared() {
        transcriber.release()
    }

    companion object {
        fun clock(sec: Float): String {
            val s = sec.toInt()
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
        }

        private fun srtTime(sec: Float): String {
            val ms = (sec * 1000).toLong()
            return "%02d:%02d:%02d,%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
        }
    }
}
