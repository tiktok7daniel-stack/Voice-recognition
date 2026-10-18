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
import com.tiktok7daniel.voceitaliana.asr.AsrModel
import com.tiktok7daniel.voceitaliana.asr.AudioDecoder
import com.tiktok7daniel.voceitaliana.asr.ModelDownloader
import com.tiktok7daniel.voceitaliana.asr.Segment
import com.tiktok7daniel.voceitaliana.asr.Transcriber
import com.tiktok7daniel.voceitaliana.library.Library
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface Phase {
    data object Idle : Phase
    data object Importing : Phase
    data class Downloading(val model: AsrModel, val done: Long, val total: Long) : Phase
    data object Loading : Phase
    data class Working(val progress: Float) : Phase
    data class Done(val seconds: Float) : Phase
    data class Failed(val title: String, val detail: String) : Phase

    /** Several files transcribed one after another; [index] is 0-based. */
    data class Batch(val index: Int, val total: Int, val name: String, val progress: Float) : Phase
    data class BatchDone(val saved: Int, val failed: Int) : Phase
}

/** One file of a multi-file job and how it went. */
data class BatchItem(val name: String, val state: String, val ok: Boolean? = null)

data class PickedFile(val uri: Uri, val name: String, val durationMs: Long)

/**
 * Process-wide engine. It lives as long as the app process (not the screen), and while it is
 * busy [WorkService] keeps the process in the foreground, so downloads and transcriptions carry
 * on when the user leaves the app or turns the screen off.
 */
class Engine(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("voce", Context.MODE_PRIVATE)
    private val modelsRoot = File(app.filesDir, "models")
    private val inbox = File(app.filesDir, "inbox")
    private val lastResult = File(app.filesDir, "last.tsv")
    private val transcriber = Transcriber(app, modelsRoot)
    val library = Library(File(app.filesDir, "library"))
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

    val busy get() = phase is Phase.Importing || phase is Phase.Downloading || phase is Phase.Loading || phase is Phase.Working ||
        phase is Phase.Batch

    /** Files of the current/last multi-file job, shown as a checklist. */
    val batch = mutableStateListOf<BatchItem>()

    init {
        refreshInstalled()
        restoreLast()
    }

    private fun refreshInstalled() {
        AsrModel.entries.forEach { m ->
            // Drop files an older build saved from an error page, so they get downloaded again.
            m.files.map { File(m.dir(modelsRoot), it) }.filter { ModelDownloader.looksLikeHtml(it) }.forEach { it.delete() }
            installed[m] = m.isInstalled(modelsRoot)
        }
    }

    /**
     * Picks an unfinished download back up (the phone may have killed the app, or the network
     * dropped) instead of making the user press "download" again. Called when the screen opens,
     * because Android only lets the foreground service start from the foreground.
     */
    fun resumePendingDownload() {
        if (busy) return
        val saved = prefs.getString("downloading", null)
        val m = if (saved != null) {
            // "" means the user stopped the last download on purpose.
            AsrModel.entries.firstOrNull { it.name == saved } ?: return
        } else {
            // Builds before this one did not record the download; fall back to leftover .part files.
            AsrModel.entries.firstOrNull { installed[it] != true && ModelDownloader.hasPartial(it, modelsRoot) } ?: return
        }
        if (installed[m] == true) {
            prefs.edit().putString("downloading", "").apply()
            return
        }
        select(m)
        download()
    }

    fun select(m: AsrModel) {
        if (busy) return
        model = m
        prefs.edit().putString("model", m.name).apply()
    }

    /**
     * Copies the picked/shared audio into app storage first: the read permission another app
     * grants us ends with the screen, but the transcription may run long after it is closed.
     */
    fun pick(uri: Uri) {
        if (busy) return
        phase = Phase.Importing
        job = scope.launch {
            try {
                file = withContext(Dispatchers.IO) {
                    val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
                    inbox.deleteRecursively()
                    inbox.mkdirs()
                    val copy = File(inbox, name.replace('/', '_'))
                    app.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it, 1 shl 20) } }
                    val local = Uri.fromFile(copy)
                    PickedFile(local, name, AudioDecoder.durationMs(app, local))
                }
                prefs.edit().putString("file", file?.name).apply()
                phase = Phase.Idle
            } catch (e: CancellationException) {
                phase = Phase.Idle
            } catch (e: Exception) {
                phase = Phase.Failed("مش قادر أفتح الملف", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun download() {
        if (busy) return
        val m = model
        phase = Phase.Downloading(m, 0, -1)
        prefs.edit().putString("downloading", m.name).apply()
        WorkService.start(app)
        job = scope.launch {
            try {
                // Mobile connections drop often on a 1 GB file: retry by itself, resuming each time.
                var attempt = 0
                while (true) {
                    try {
                        ModelDownloader.download(m, modelsRoot) { done, total ->
                            scope.launch { if (phase is Phase.Downloading) phase = Phase.Downloading(m, done, total) }
                        }
                        break
                    } catch (e: IOException) {
                        if (++attempt >= 6) throw e
                        delay(3_000L * attempt)
                    }
                }
                refreshInstalled()
                prefs.edit().putString("downloading", "").apply()
                phase = if (installed[m] == true) {
                    Phase.Idle
                } else {
                    Phase.Failed("الموديل ناقص", "في ملف ماتحمّلش صح — دوس تحميل تاني")
                }
            } catch (e: CancellationException) {
                // Stopped by the user: don't resume it on the next launch.
                prefs.edit().putString("downloading", "").apply()
                phase = Phase.Idle
            } catch (e: Exception) {
                phase = Phase.Failed("التحميل وقف", "اتأكد من النت — هيكمّل لوحده من مكان ما وقف أول ما تفتح البرنامج. (${e.message})")
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
        lastResult.delete()
        phase = Phase.Loading
        WorkService.start(app)
        job = scope.launch {
            val t0 = System.currentTimeMillis()
            try {
                withContext(Dispatchers.Default) { transcriber.load(m) }
                phase = Phase.Working(0f)
                withContext(Dispatchers.Default) {
                    transcriber.transcribe(
                        f.uri, m,
                        onProgress = { p -> scope.launch { if (phase is Phase.Working) phase = Phase.Working(p) } },
                        onSegment = { s ->
                            lastResult.appendText("${s.start}\t${s.end}\t${s.text.replace('\t', ' ').replace('\n', ' ')}\n")
                            scope.launch {
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

    /**
     * Transcribes several files back to back and saves each transcript straight into the given
     * group/section, named after its file. One at a time on purpose: a phone has the RAM and
     * cores for one model run, and two in parallel would each go at half speed anyway.
     */
    fun startBatch(uris: List<Uri>, groupId: String, sectionId: String) {
        if (busy || uris.isEmpty()) return
        val m = model
        if (installed[m] != true) {
            phase = Phase.Failed("حمّل الموديل الأول", "دوس تحميل ${m.title} وبعدين اختار الملفات تاني")
            return
        }
        val dir = File(app.filesDir, "batch")
        batch.clear()
        phase = Phase.Importing
        WorkService.start(app)
        job = scope.launch {
            var saved = 0
            var failed = 0
            try {
                // Copy first: the read grant from the file picker does not outlive the screen.
                val files = withContext(Dispatchers.IO) {
                    dir.deleteRecursively()
                    dir.mkdirs()
                    uris.mapIndexed { i, uri ->
                        val name = displayName(uri)
                        val copy = File(dir, "$i-${name.replace('/', '_')}")
                        runCatching {
                            app.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it, 1 shl 20) } }
                        }
                        name to copy
                    }
                }
                batch.addAll(files.map { BatchItem(it.first, "مستني") })
                phase = Phase.Loading
                withContext(Dispatchers.Default) { transcriber.load(m) }
                files.forEachIndexed { i, (name, copy) ->
                    phase = Phase.Batch(i, files.size, name, 0f)
                    batch[i] = BatchItem(name, "شغال…")
                    try {
                        if (!copy.isFile || copy.length() == 0L) throw java.io.IOException("الملف ماتنسخش")
                        val found = mutableListOf<Segment>()
                        withContext(Dispatchers.Default) {
                            transcriber.transcribe(
                                Uri.fromFile(copy), m,
                                onProgress = { p -> scope.launch { if (phase is Phase.Batch) phase = Phase.Batch(i, files.size, name, p) } },
                                onSegment = { found.add(it) },
                            )
                        }
                        val note = library.addNote(groupId, sectionId, name.substringBeforeLast('.').ifBlank { name }, paragraphs(found), name)
                        if (note == null) throw java.io.IOException("المجموعة اتمسحت")
                        batch[i] = BatchItem(name, "اتحفظ — ${found.size} جملة", ok = true)
                        saved++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: OutOfMemoryError) {
                        transcriber.release()
                        batch[i] = BatchItem(name, "الرام مش كفاية — جرّب موديل أخف", ok = false)
                        failed++
                        withContext(Dispatchers.Default) { transcriber.load(m) }
                    } catch (e: Exception) {
                        batch[i] = BatchItem(name, "فشل: ${e.message ?: e.javaClass.simpleName}", ok = false)
                        failed++
                    } finally {
                        copy.delete()
                    }
                }
                phase = Phase.BatchDone(saved, failed)
            } catch (e: CancellationException) {
                batch.indices.forEach { if (batch[it].ok == null) batch[it] = batch[it].copy(state = "اتوقف") }
                phase = if (saved > 0) Phase.BatchDone(saved, failed) else Phase.Idle
            } catch (e: Exception) {
                phase = Phase.Failed("حصل خطأ", e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun clearBatch() {
        if (busy) return
        batch.clear()
        if (phase is Phase.BatchDone) phase = Phase.Idle
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "audio"

    /** Brings back the last transcript after the app was closed and reopened. */
    private fun restoreLast() {
        if (!lastResult.isFile) return
        runCatching {
            lastResult.readLines().mapNotNull { line ->
                val p = line.split('\t', limit = 3)
                if (p.size == 3) Segment(p[0].toFloat(), p[1].toFloat(), p[2]) else null
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
            segments.addAll(it)
            text = render()
            val name = prefs.getString("file", null) ?: return@let
            val copy = File(inbox, name.replace('/', '_'))
            if (copy.isFile) file = PickedFile(Uri.fromFile(copy), name, AudioDecoder.durationMs(app, Uri.fromFile(copy)))
        }
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
    private fun paragraphs(segments: List<Segment> = this.segments): String {
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
