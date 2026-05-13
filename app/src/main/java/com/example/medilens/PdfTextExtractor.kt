package com.example.medilens

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfTextExtractor {

    private const val TAG = "PdfTextExtractor"

    fun init(context: Context) {
        // Safety net only — real init happens in MediLensApplication.onCreate()
        // PDFBoxResourceLoader.init() is safe to call multiple times
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun extract(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open PDF file, check file permissions")

        return stream.use { inputStream ->
            val document = try {
                PDDocument.load(inputStream)
            } catch (e: Exception) {
                Log.e(TAG, "PDDocument.load failed: ${e.message}")
                throw Exception("Not a valid PDF file: ${e.message}")
            }

            document.use { doc ->
                if (doc.isEncrypted) {
                    throw Exception("PDF is password-protected. Please provide an unencrypted PDF.")
                }

                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                    addMoreFormatting = false
                }

                val text = stripper.getText(doc).trim()

                if (text.isBlank()) {
                    throw Exception(
                        "PDF contains no extractable text. " +
                                "It may be a scanned image, please upload as an image file instead."
                    )
                }

                Log.d(TAG, "Extracted ${text.length} chars from PDF")
                text
            }
        }
    }
}