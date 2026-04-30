package com.example.medilens

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Stage 2C — extracts text from Word files (.docx and .doc).
 *
 * NO external dependencies — uses only Android built-in APIs:
 *   - ZipInputStream  (docx is just a ZIP)
 *   - XmlPullParser   (reads word/document.xml inside the ZIP)
 *
 * Works on minSdk 24+.
 *
 * .doc (old binary format) is not supported by this approach.
 * If user uploads a .doc, we show a clear error asking them to
 * save as .docx or upload as PDF/image instead.
 */
object WordTextExtractor {

    fun extract(context: Context, uri: Uri, mimeType: String): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open Word file — check file permissions")

        return stream.use { inputStream ->
            if (isDocx(mimeType, uri)) {
                extractDocx(inputStream)
            } else {
                // .doc binary format — cannot parse without POI which requires API 26
                throw Exception(
                    "Old .doc format is not supported.\n\n" +
                            "Please open the file in Microsoft Word or Google Docs " +
                            "and save it as .docx, then upload again.\n\n" +
                            "Alternatively, export it as a PDF."
                )
            }
        }
    }

    private fun isDocx(mimeType: String, uri: Uri): Boolean {
        if (mimeType.contains("openxmlformats") || mimeType.contains("docx")) return true
        if (mimeType == "application/msword") return false
        // Fallback: check file extension
        val path = uri.path?.lowercase() ?: ""
        return path.endsWith(".docx")
    }

    /**
     * A .docx file is a ZIP archive. The actual text lives in
     * word/document.xml inside that ZIP. We unzip, find that entry,
     * and parse the XML to extract all <w:t> text nodes.
     */
    private fun extractDocx(inputStream: InputStream): String {
        val zip = ZipInputStream(inputStream)
        val sb  = StringBuilder()

        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                sb.append(parseDocumentXml(zip))
                break
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        zip.close()

        return sb.toString().trim().also {
            if (it.isBlank()) throw Exception(
                "Word document contains no readable text. " +
                        "It may be image-only — please upload as an image file instead."
            )
        }
    }

    /**
     * Parses word/document.xml and extracts all text content from <w:t> tags.
     * Also inserts newlines at paragraph boundaries (<w:p> tags) to preserve
     * prescription structure (medicine name, dose, frequency on separate lines).
     */
    private fun parseDocumentXml(stream: InputStream): String {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        val sb           = StringBuilder()
        var insideTextTag = false
        var lastWasSpace = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "t"  -> insideTextTag = true      // <w:t>
                        "p"  -> {                         // <w:p> = new paragraph
                            if (sb.isNotEmpty()) sb.append("\n")
                            lastWasSpace = false
                        }
                        "br", "cr" -> sb.append("\n")     // line break
                        "tab"      -> sb.append(" ")      // tab → space
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") insideTextTag = false
                }
                XmlPullParser.TEXT -> {
                    if (insideTextTag) {
                        val text = parser.text ?: ""
                        sb.append(text)
                        lastWasSpace = text.endsWith(" ")
                    }
                }
            }
            eventType = parser.next()
        }

        return sb.toString()
    }
}