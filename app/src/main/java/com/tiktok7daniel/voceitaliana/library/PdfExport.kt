package com.tiktok7daniel.voceitaliana.library

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders notes (transcript + the user's summary) into an A4 PDF with Android's built-in
 * [PdfDocument]: no library, works offline, and Arabic/Italian mix correctly through the
 * system text layout (each paragraph picks its own direction).
 */
object PdfExport {
    private const val PAGE_W = 595 // A4 in points
    private const val PAGE_H = 842
    private const val MARGIN = 44f
    private val WIDTH = (PAGE_W - 2 * MARGIN).toInt()

    /** One recording to print, with the section it belongs to (blank for none). */
    data class Entry(val note: Note, val section: String)

    fun write(out: OutputStream, docTitle: String, entries: List<Entry>) {
        val doc = PdfDocument()
        val w = Writer(doc)
        w.block(docTitle, paint(22f, bold = true, color = 0xFF2B2140.toInt()), after = 4f)
        w.block("Voce Italiana · ${date(System.currentTimeMillis())} · ${entries.size} تسجيل", paint(10f, color = Color.GRAY), after = 18f)
        entries.forEachIndexed { i, e ->
            if (i > 0) w.rule()
            val n = e.note
            w.block(n.title, paint(16f, bold = true), after = 2f)
            val meta = listOfNotNull(e.section.ifBlank { null }, "${date(n.created)} ${time(n.created)}", n.source.ifBlank { null })
            w.block(meta.joinToString(" · "), paint(9.5f, color = Color.GRAY), after = 10f)
            w.block("النص", paint(11f, bold = true, color = 0xFF7A4DFF.toInt()), after = 4f)
            w.block(n.text.ifBlank { "—" }, paint(11.5f), ltr = true, after = 12f)
            if (n.summary.isNotBlank()) {
                w.block("الملخص والمختصرات", paint(11f, bold = true, color = 0xFFE07B00.toInt()), after = 4f)
                w.block(n.summary, paint(11.5f), after = 12f)
            }
        }
        w.finish()
        doc.writeTo(out)
        doc.close()
    }

    /** Writes the PDF into the app cache and opens the share sheet (WhatsApp, Drive, Files…). */
    fun share(ctx: Context, fileName: String, docTitle: String, entries: List<Entry>) {
        val dir = File(ctx.cacheDir, "pdf").apply { deleteRecursively(); mkdirs() }
        val f = File(dir, safeName(fileName))
        f.outputStream().use { write(it, docTitle, entries) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "مشاركة PDF"))
    }

    fun safeName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "Voce" } +
        if (name.endsWith(".pdf")) "" else ".pdf"

    private fun paint(size: Float, bold: Boolean = false, color: Int = 0xFF1B1B1F.toInt()) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun date(t: Long) = SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")).format(Date(t))

    private fun time(t: Long) = SimpleDateFormat("h:mm a", Locale("ar")).format(Date(t))

    /** Lays text out top-to-bottom, starting a new page whenever the current one is full. */
    private class Writer(private val doc: PdfDocument) {
        private var page: PdfDocument.Page? = null
        private var pageNo = 0
        private var y = MARGIN

        private val canvas: Canvas
            get() = (page ?: newPage()).canvas

        private fun newPage(): PdfDocument.Page {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            p.canvas.drawText("$pageNo", PAGE_W / 2f, PAGE_H - 20f, TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f
                color = Color.GRAY
                textAlign = android.graphics.Paint.Align.CENTER
            })
            page = p
            y = MARGIN
            return p
        }

        fun block(text: String, paint: TextPaint, ltr: Boolean = false, after: Float = 0f) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if (ltr) TextDirectionHeuristics.FIRSTSTRONG_LTR else TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .setLineSpacing(0f, 1.25f)
                .build()
            var line = 0
            while (line < layout.lineCount) {
                if (page == null || y + (layout.getLineBottom(line) - layout.getLineTop(line)) > PAGE_H - MARGIN) newPage()
                // Take as many lines as fit on this page, then draw just that slice of the layout.
                val top = layout.getLineTop(line)
                var last = line
                while (last + 1 < layout.lineCount && y + (layout.getLineBottom(last + 1) - top) <= PAGE_H - MARGIN) last++
                val bottom = layout.getLineBottom(last)
                val c = canvas
                c.save()
                c.translate(MARGIN, y - top)
                c.clipRect(0, top, WIDTH, bottom)
                layout.draw(c)
                c.restore()
                y += bottom - top
                line = last + 1
            }
            y += after
        }

        fun rule() {
            if (page == null || y + 24f > PAGE_H - MARGIN) newPage()
            y += 10f
            canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, TextPaint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 1f })
            y += 16f
        }

        fun finish() {
            if (page == null) newPage()
            page?.let { doc.finishPage(it) }
            page = null
        }
    }
}
