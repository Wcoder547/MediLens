package com.example.medilens

import android.content.Context
import android.net.Uri

/**
 * Stage 1 of the prescription pipeline.
 * Detects the MIME type of the uploaded file and returns a typed route.
 */
object InputRouter {

    sealed class RouteResult {
        data class Image(val uri: Uri) : RouteResult()
        data class Pdf(val uri: Uri) : RouteResult()
        data class Word(val uri: Uri, val mimeType: String) : RouteResult()
        data class Unsupported(val mimeType: String) : RouteResult()
    }

    fun route(context: Context, uri: Uri): RouteResult {
        val mimeType = context.contentResolver.getType(uri)
            ?: inferFromUri(uri)
            ?: "application/octet-stream"

        return when {
            mimeType.startsWith("image/")                             -> RouteResult.Image(uri)
            mimeType == "application/pdf"                             -> RouteResult.Pdf(uri)
            mimeType == "application/msword"                          -> RouteResult.Word(uri, mimeType)
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                                                      -> RouteResult.Word(uri, mimeType)
            // Some file managers report .docx as this
            mimeType == "application/octet-stream"                    -> inferOctetStream(uri)
            else                                                      -> RouteResult.Unsupported(mimeType)
        }
    }

    /**
     * Falls back to extension-based detection when content resolver returns null.
     */
    private fun inferFromUri(uri: Uri): String? {
        val path = uri.path?.lowercase() ?: return null
        return when {
            path.endsWith(".pdf")  -> "application/pdf"
            path.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            path.endsWith(".doc")  -> "application/msword"
            path.endsWith(".jpg")  -> "image/jpeg"
            path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png")  -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".heic") -> "image/heic"
            else                   -> null
        }
    }

    /**
     * When MIME is generic octet-stream, try to guess from extension.
     */
    private fun inferOctetStream(uri: Uri): RouteResult {
        val path = uri.path?.lowercase() ?: return RouteResult.Unsupported("application/octet-stream")
        return when {
            path.endsWith(".docx") -> RouteResult.Word(
                uri,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
            path.endsWith(".doc")  -> RouteResult.Word(uri, "application/msword")
            path.endsWith(".pdf")  -> RouteResult.Pdf(uri)
            else                   -> RouteResult.Unsupported("application/octet-stream")
        }
    }
}
