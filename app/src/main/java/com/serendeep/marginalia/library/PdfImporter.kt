package com.serendeep.marginalia.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.serendeep.marginalia.data.DocumentEntity
import com.serendeep.marginalia.data.MarginaliaRepository
import com.serendeep.marginalia.pdf.PdfDocumentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies a SAF-picked PDF into app storage and registers it with the repository.
 * The original SAF document is only ever read, never modified.
 */
class PdfImporter(
    private val context: Context,
    private val repository: MarginaliaRepository,
) {
    sealed interface Result {
        data class Success(val document: DocumentEntity) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun import(lectureId: String, uri: Uri): Result = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.pdf")

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            file.delete()
            return@withContext Result.Failure(ERROR_MESSAGE)
        }

        val pageCount = runCatching {
            val source = PdfDocumentSource.open(context, file)
            try {
                source.pageCount
            } finally {
                source.close()
            }
        }.getOrDefault(-1)
        if (pageCount <= 0) {
            file.delete()
            return@withContext Result.Failure(ERROR_MESSAGE)
        }

        val document = runCatching {
            repository.importDocument(lectureId, displayName(uri), file.absolutePath, pageCount)
        }.getOrElse {
            file.delete()
            return@withContext Result.Failure(ERROR_MESSAGE)
        }
        Result.Success(document)
    }

    fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return uri.lastPathSegment ?: "document.pdf"
    }

    private companion object {
        const val ERROR_MESSAGE = "Couldn't import — password-protected or damaged PDF"
    }
}
