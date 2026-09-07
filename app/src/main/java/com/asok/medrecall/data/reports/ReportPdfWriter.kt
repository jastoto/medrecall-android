package com.asok.medrecall.data.reports

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.asok.medrecall.ui.reports.ReportType
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Renders a ReportDocument into a simple paginated PDF (US Letter,
 * 612x792pt at PdfDocument's default 72dpi) using plain Canvas.drawText --
 * no external PDF library needed. Wraps long lines by measuring against
 * the page's usable width and starts a new page when content runs out of
 * room. Output goes under the app's cache dir (cacheDir/reports/) so it
 * matches the FileProvider path declared in res/xml/file_paths.xml.
 */
object ReportPdfWriter {

    private const val PAGE_WIDTH = 612
    private const val PAGE_HEIGHT = 792
    private const val MARGIN = 48f

    fun write(context: Context, reportType: ReportType, document: ReportDocument): File {
        val pdf = PdfDocument()
        val titlePaint = Paint().apply { textSize = 22f; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD }
        val subtitlePaint = Paint().apply { textSize = 11f; color = Color.parseColor("#666666") }
        val headingPaint = Paint().apply {
            textSize = 15f; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD
            color = Color.parseColor("#3E5C7C")
        }
        val bodyPaint = Paint().apply { textSize = 12f }

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) newPage()
        }

        val maxWidth = PAGE_WIDTH - 2 * MARGIN

        fun drawWrapped(text: String, paint: Paint, lineHeight: Float) {
            val words = text.split(" ")
            var line = StringBuilder()
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    ensureSpace(lineHeight)
                    canvas.drawText(line.toString(), MARGIN, y, paint)
                    y += lineHeight
                    line = StringBuilder(word)
                } else {
                    line = StringBuilder(candidate)
                }
            }
            if (line.isNotEmpty()) {
                ensureSpace(lineHeight)
                canvas.drawText(line.toString(), MARGIN, y, paint)
                y += lineHeight
            }
        }

        canvas.drawText(document.title, MARGIN, y, titlePaint)
        y += 26f
        canvas.drawText(document.subtitle, MARGIN, y, subtitlePaint)
        y += 24f

        document.sections.forEach { section ->
            ensureSpace(28f)
            y += 6f
            canvas.drawText(section.heading, MARGIN, y, headingPaint)
            y += 18f
            if (section.lines.isEmpty()) {
                drawWrapped("—", bodyPaint, 16f)
            } else {
                section.lines.forEach { line -> drawWrapped("• $line", bodyPaint, 16f) }
            }
            y += 10f
        }

        pdf.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        val file = File(dir, "${reportType.id}_$stamp.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }
}
