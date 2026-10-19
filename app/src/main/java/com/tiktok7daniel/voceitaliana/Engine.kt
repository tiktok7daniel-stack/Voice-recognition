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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** State of a model download (shown in its own card). */
sealed interface Download {
    data class Running(val model: AsrModel, val done: Long, val total: Long) : Download
    data class Failed(val detail: String) : Download
}

sealed interface JobState {
    data object Importing : JobState
    data object Waiting : JobState
    data object Loading : JobState
    data class Working(val progress: Float) : JobState
    data class Done(val seconds: Float) : JobState
    data class Failed(val title: String, val detail: String) : JobState
    data object Cancelled : JobState
}

/** One audio file being (or already) transcribed. Each job has its own card. */
class Job(val id: Long, name: String, val model: AsrModel) {
    var name by mutableStateOf(name)
    var uri by mutableStateOf<Uri?>(null)
    var durationMs by mutableStateOf(0L)
    var state by mutableStateOf<JobState>(JobState.Importing)
    val segments = mutableStateListOf<Segment>()
    var text by mutableStateOf("")
    var expanded by mutableStateOf(true)
    internal var work: kotlinx.coroutines.Job? = null

    val busy get() = state is JobState.Importing || state is JobState.Waiting || state is JobState.Loading || state is JobState.Working

    fun baseName(): String = name.substringBeforeLast('.').ifBlank { "trascrizione" }
}

/**
 * Process-wide engine. It lives as long as the app process (not the screen), and while it is
 * busy [WorkService] keeps the process in the foreground, so downloads and transcriptions carry
 * on when the user leaves the app or turns the screen off.
 */
class Engine(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("voce", Context.MODE_PRIVATE)
    private val modelsRoot = File(app.filesDir, "models")
    private val jobsDir = File(app.filesDir, "jobs").apply { mkdirs() }
    private val transcriber = Transcriber(app, modelsRoot)
    private val parallel = Semaphore(MAX_PARALLEL)
    private var downloadWork: kotlinx.coroutines.Job? = null

    var model by mutableStateOf(
        runCatching { AsrModel.valueOf(prefs.getString("model", null) ?: "") }.getOrDefault(AsrModel.Parakeet),
    )
        private set
    val installed = mutableStateMapOf<AsrModel, Boolean>()
    var download by mutableStateOf<Download?>(null)
        private set
    val jobs = mutableStateListOf<Job>()
    var showTimestamps by mutableStateOf(false)
        private set

    val downloading get() = download is Download.Running
    val busy get() = downloading || jobs.any { it.busy }

    init {
        AsrModel.entries.forEach { installed[it] = it.isInstalled(modelsRoot) }
        restoreJobs()
    }

    fun select(m: AsrModel) {
        if (downloading) return
        model = m
        prefs.edit().putString("model", m.name).apply()
    }

    // ---- Model download -------------------------------------------------------------------

    fun downloadModel() {
        if (downloading) return
        val m = model
        download = Download.Running(m, 0, -1)
        WorkService.start(app)
        downloadWork = scope.launch {
            try {
                ModelDownloader.download(m, modelsRoot) { done, total ->
                    scope.launch { if (download is Download.Running) download = Download.Running(m, done, total) }
                }
                installed[m] = true
                download = null
            } catch (e: CancellationException) {
                download = null
            } catch (e: Exception) {
                download = Download.Failed("اتأكد من النت وجرب تاني — هيكمّل من مكان ما وقف. (${e.message})")
            }
        }
    }

    fun cancelDownload() {
        downloadWork?.cancel()
    }

    fun dismissDownloadError() {
        if (download is Download.Failed) download = null
    }

    fun deleteModel(m: AsrModel) {
        if (busy) return
        transcriber.release()
        ModelDownloader.delete(m, modelsRoot)
        installed[m] = false
    }

    // ---- Transcription jobs ---------------------------------------------------------------

    /**
     * Adds one job per audio file and starts it. Up to [MAX_PARALLEL] files are transcribed at
     * the same time; the rest wait their turn.
     *
     * The audio is copied into app storage first: the read permission another app grants us ends
     * with the screen, but the transcription may run long after it is closed.
     */
    fun add(uris: List<Uri>) {
        if (installed[model] != true || uris.isEmpty()) return
        val m = model
        uris.forEach { uri ->
            val job = Job(System.nanoTime(), "…", m)
            jobs.add(job)
            job.work = scope.launch { run(job, uri) }
        }
        WorkService.start(app)
    }

    private suspend fun run(job: Job, source: Uri) {
        try {
            withContext(Dispatchers.IO) {
                val name = app.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: source.lastPathSegment?.substringAfterLast('/') ?: "audio"
                val dir = File(jobsDir, job.id.toString()).apply { mkdirs() }
                val copy = File(dir, "audio")
                app.contentResolver.openInputStream(source)!!.use { input -> copy.outputStream().use { input.copyTo(it, 1 shl 20) } }
                File(dir, "meta").writeText("$name\n${job.model.name}\n")
                val local = Uri.fromFile(copy)
                val duration = AudioDecoder.durationMs(app, local)
                withContext(Dispatchers.Main) {
                    job.name = name
                    job.uri = local
                    job.durationMs = duration
                }
            }
            job.state = JobState.Waiting
            parallel.withPermit {
                // Jobs that run together must share one model; wait for any job on another model.
                while (jobs.any { it !== job && it.model != job.model && (it.state is JobState.Loading || it.state is JobState.Working) }) {
                    delay(500)
                }
                job.state = JobState.Loading
                val t0 = System.currentTimeMillis()
                withContext(Dispatchers.Default) { transcriber.load(job.model) }
                job.state = JobState.Working(0f)
                val log = File(File(jobsDir, job.id.toString()), "segments.tsv")
                withContext(Dispatchers.Default) {
                    transcriber.transcribe(
                        job.uri!!, job.model,
                        onProgress = { p -> scope.launch { if (job.state is JobState.Working) job.state = JobState.Working(p) } },
                        onSegment = { s ->
                            log.appendText("${s.start}\t${s.end}\t${s.text.replace('\t', ' ').replace('\n', ' ')}\n")
                            scope.launch {
                                job.segments.add(s)
                                job.text = render(job)
                            }
                        },
                    )
                }
                job.state = JobState.Done((System.currentTimeMillis() - t0) / 1000f)
                File(File(jobsDir, job.id.toString()), "done").writeText(job.state.let { (it as JobState.Done).seconds.toString() })
            }
        } catch (e: CancellationException) {
            job.state = JobState.Cancelled
        } catch (e: OutOfMemoryError) {
            transcriber.release()
            job.state = JobState.Failed("الرام مش كفاية", "جرّب موديل أخف زي Parakeet v3 أو Whisper Small")
        } catch (e: Exception) {
            job.state = JobState.Failed("حصل خطأ", e.message ?: e.javaClass.simpleName)
        }
    }

    fun cancel(job: Job) {
        job.work?.cancel()
    }

    fun cancelAll() {
        jobs.forEach { it.work?.cancel() }
        cancelDownload()
    }

    fun remove(job: Job) {
        job.work?.cancel()
        jobs.remove(job)
        File(jobsDir, job.id.toString()).deleteRecursively()
    }

    fun setAllExpanded(expanded: Boolean) {
        jobs.forEach { it.expanded = expanded }
    }

    /** Brings back earlier transcripts after the app was closed and reopened. */
    private fun restoreJobs() {
        jobsDir.listFiles()?.sortedBy { it.name }?.forEach { dir ->
            val id = dir.name.toLongOrNull() ?: return@forEach
            val meta = File(dir, "meta").takeIf { it.isFile }?.readLines()
            val segments = File(dir, "segments.tsv").takeIf { it.isFile }?.readLines()?.mapNotNull { line ->
                val p = line.split('\t', limit = 3)
                if (p.size == 3) runCatching { Segment(p[0].toFloat(), p[1].toFloat(), p[2]) }.getOrNull() else null
            }.orEmpty()
            if (meta == null || meta.size < 2 || segments.isEmpty()) {
                dir.deleteRecursively()
                return@forEach
            }
            val m = runCatching { AsrModel.valueOf(meta[1]) }.getOrDefault(AsrModel.Parakeet)
            val job = Job(id, meta[0], m)
            val audio = File(dir, "audio")
            if (audio.isFile) job.uri = Uri.fromFile(audio)
            job.segments.addAll(segments)
            job.text = render(job)
            job.expanded = false
            val seconds = File(dir, "done").takeIf { it.isFile }?.readText()?.toFloatOrNull()
            job.state = if (seconds != null) JobState.Done(seconds) else JobState.Failed("وقف قبل ما يخلص", "النص اللي اتطلع لحد ما وقف موجود")
            jobs.add(job)
        }
    }

    fun toggleTimestamps() {
        showTimestamps = !showTimestamps
        jobs.forEach { if (it.segments.isNotEmpty()) it.text = render(it) }
    }

    private fun render(job: Job): String =
        if (showTimestamps) {
            job.segments.joinToString("\n") { "[${clock(it.start)}] ${it.text}" }
        } else {
            // Joins utterances into paragraphs, breaking where the speaker paused for a while.
            val sb = StringBuilder()
            job.segments.forEachIndexed { i, s ->
                if (i > 0) sb.append(if (s.start - job.segments[i - 1].end > 1.5f) "\n\n" else " ")
                sb.append(s.text)
            }
            sb.toString()
        }

    fun srt(job: Job): String = job.segments.mapIndexed { i, s ->
        "${i + 1}\n${srtTime(s.start)} --> ${srtTime(s.end)}\n${s.text}\n"
    }.joinToString("\n")

    companion object {
        const val MAX_PARALLEL = 2

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
