package com.asok.medrecall.data.recordings

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/** Everything the structured visit-note PDF needs -- punch item #43 (2026-09-21): reorganizing the note file into Patient/Doctor/Date/Time header, Summary, Call Log (the transcript), and Next Steps. Summary and Next Steps are typed by Asok on the Stop-Recording dialog, not AI-generated (that's tabled as its own punch item). */
data class VisitNoteData(
    val patientName: String,
    val doctorName: String,
    val dateLabel: String,
    val timeLabel: String,
    val summary: String,
    val callLog: String,
    val nextSteps: String
)

/**
 * Renders a [VisitNoteData] into a simple paginated PDF, same plain-Canvas
 * approach as ReportPdfWriter (no external PDF library, US Letter at 72dpi).
 * Deliberately a separate object rather than reusing ReportPdfWriter directly
 * -- that one is wired to ReportType/ReportDocument for the Reports screen's
 * 7-tile grid, and this note has its own fixed shape (header block + three
 * named sections) rather than an arbitrary list of report sections.
 *
 * Output goes under the app's cache dir (cacheDir/recordings/), matching
 * where RecordVisitViewModel writes the paired audio file -- both get copied/
 * uploaded to wherever Asok picks in the destination dialog, then deleted
 * from cache once that's done.
 */
object VisitNotePdfWriter {

    private const val PAGE_WIDTH = 612
    private const val PAGE_HEIGHT = 792
    private const val MARGIN = 48f

    fun write(context: Context, note: VisitNoteData, fileStamp: String): File {
        val pdf = PdfDocument()
        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD }
        val headerLabelPaint = Paint().apply {
            textSize = 12f; isFakeBoldText = true; typeface = Typeface.DEFAULT_BOLD
            color = Color.parseColor("#666666")
        }
        val headerValuePaint = Paint().apply { textSize = 13f }
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
            val paragraphs = if (text.isBlank()) listOf("—") else text.split("\n")
            paragraphs.forEach { paragraph ->
                val words = paragraph.split(" ")
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
                ensureSpace(lineHeight)
                canvas.drawText(line.toString(), MARGIN, y, paint)
                y += lineHeight
            }
        }

        fun drawHeaderRow(label: String, value: String) {
            ensureSpace(18f)
            canvas.drawText(label, MARGIN, y, headerLabelPaint)
            canvas.drawText(value, MARGIN + 90f, y, headerValuePaint)
            y += 18f
        }

        fun drawSection(heading: String, body: String) {
            ensureSpace(28f)
            y += 10f
            canvas.drawText(heading, MARGIN, y, headingPaint)
            y += 18f
            drawWrapped(body, bodyPaint, 16f)
        }

        canvas.drawText("Visit Note", MARGIN, y, titlePaint)
        y += 28f

        drawHeaderRow("Patient:", note.patientName)
        drawHeaderRow("Doctor:", note.doctorName)
        drawHeaderRow("Date:", note.dateLabel)
        drawHeaderRow("Time:", note.timeLabel)

        drawSection("Summary", note.summary)
        drawSection("Call Log", note.callLog)
        drawSection("Next Steps", note.nextSteps)

        pdf.finishPage(page)

        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val file = File(dir, "VisitNote_$fileStamp.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }
}
