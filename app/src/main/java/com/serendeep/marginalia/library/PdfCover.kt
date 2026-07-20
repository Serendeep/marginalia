package com.serendeep.marginalia.library

import android.content.Context
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import com.serendeep.marginalia.pdf.PdfDocumentSource
import java.io.File

/** Coil model: the first page of an imported PDF, rendered as cover art. */
data class PdfCover(val path: String)

class PdfCoverKeyer : Keyer<PdfCover> {
    override fun key(data: PdfCover, options: Options): String = "pdfcover:${data.path}"
}

/** Renders page one via pdfium at the requested width; Coil caches the result. */
class PdfCoverFetcher(
    private val context: Context,
    private val data: PdfCover,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val source = PdfDocumentSource.open(context, File(data.path))
        try {
            val width = options.size.width.pxOrElse { DEFAULT_WIDTH_PX }.coerceAtMost(MAX_WIDTH_PX)
            val bitmap = source.renderFullPage(0, width)
            return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
        } finally {
            source.close()
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<PdfCover> {
        override fun create(data: PdfCover, options: Options, imageLoader: ImageLoader): Fetcher =
            PdfCoverFetcher(context, data, options)
    }

    private companion object {
        const val DEFAULT_WIDTH_PX = 480
        const val MAX_WIDTH_PX = 1024
    }
}
