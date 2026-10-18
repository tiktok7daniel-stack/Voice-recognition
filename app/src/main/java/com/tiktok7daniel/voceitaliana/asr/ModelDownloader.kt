package com.tiktok7daniel.voceitaliana.asr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Downloads a model file by file, resuming partial downloads (.part) after a cut connection. */
object ModelDownloader {

    /** onProgress(downloadedBytes, totalBytes); totalBytes is -1 until every size is known. */
    suspend fun download(model: AsrModel, root: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val dir = model.dir(root).apply { mkdirs() }
        val sizes = model.files.map { name ->
            val f = File(dir, name)
            if (f.isFile) f.length() else contentLength(model.url(name))
        }
        val total = if (sizes.any { it < 0 }) -1L else sizes.sum()
        var done = 0L

        model.files.forEachIndexed { i, name ->
            val target = File(dir, name)
            if (target.isFile) {
                done += target.length()
                onProgress(done, total)
                return@forEachIndexed
            }
            val part = File(dir, "$name.part")
            val base = done
            fetch(model.url(name), part) { fileBytes -> onProgress(base + fileBytes, total) }
            if (!part.renameTo(target)) throw IOException("Cannot save $name")
            done = base + (if (sizes[i] > 0) sizes[i] else target.length())
        }
    }

    fun delete(model: AsrModel, root: File) {
        model.dir(root).deleteRecursively()
    }

    private fun open(url: String, method: String = "GET", from: Long = 0): HttpURLConnection {
        var current = url
        // Hugging Face answers with redirects to its CDN; follow them across hosts manually.
        repeat(6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.requestMethod = method
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", "VoceItaliana/2.0")
            if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("Redirect without location")
                current = URL(URL(current), loc).toString()
                c.disconnect()
            } else {
                return c
            }
        }
        throw IOException("Too many redirects")
    }

    private fun contentLength(url: String): Long = try {
        val c = open(url, "HEAD")
        val len = c.getHeaderField("Content-Length")?.toLongOrNull()
            ?: c.getHeaderField("X-Linked-Size")?.toLongOrNull()
            ?: -1L
        c.disconnect()
        len
    } catch (e: IOException) {
        -1L
    }

    private suspend fun fetch(url: String, part: File, onBytes: (Long) -> Unit) {
        var have = if (part.isFile) part.length() else 0L
        val c = open(url, from = have)
        val code = c.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")
        if (code == 200) have = 0 // server ignored Range: restart from scratch
        c.inputStream.use { input ->
            FileOutputStream(part, have > 0).use { out ->
                val buf = ByteArray(256 * 1024)
                var lastReport = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    have += n
                    if (have - lastReport > 512 * 1024) {
                        lastReport = have
                        onBytes(have)
                    }
                }
            }
        }
        onBytes(have)
        c.disconnect()
    }
}
