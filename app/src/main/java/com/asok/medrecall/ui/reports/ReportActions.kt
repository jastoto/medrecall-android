package com.asok.medrecall.ui.reports

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Opens Android's built-in print preview for [pdfFile] -- from there Asok
 * can page through it, save it as a PDF, or send it to a physical printer,
 * all from one system dialog. This doubles as "View" for a generated report.
 */
fun openPrintPreview(context: Context, pdfFile: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print(jobName, ExistingPdfPrintAdapter(pdfFile, jobName), PrintAttributes.Builder().build())
}

/** Hands [pdfFile] to the system share sheet (email, messaging, Drive, etc.) via a FileProvider content Uri. */
fun sharePdf(context: Context, pdfFile: File, subject: String) {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share $subject"))
}

/**
 * Streams an already-rendered PDF file straight into the print framework's
 * output pipe. MedRecall+ renders the PDF itself (see ReportPdfWriter), so
 * printing just needs to hand those existing bytes to PrintManager rather
 * than re-rendering anything.
 */
private class ExistingPdfPrintAdapter(
    private val pdfFile: File,
    private val jobName: String
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        try {
            FileInputStream(pdfFile).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    val buffer = ByteArray(8192)
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        read = input.read(buffer)
                    }
                }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        }
    }
}
