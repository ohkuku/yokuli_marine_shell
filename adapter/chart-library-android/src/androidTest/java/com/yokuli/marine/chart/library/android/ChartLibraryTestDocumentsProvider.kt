package com.yokuli.marine.chart.library.android

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import kotlin.concurrent.thread

class ChartLibraryTestDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(
        projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS),
    ).apply { addRow(arrayOf("root", "root", "Chart tests", Root.FLAG_SUPPORTS_CREATE)) }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        documentCursor(documentId, projection)

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        require(mode == "r")
        val source = sourceFile(documentId)
        if (documentId.startsWith("pipe-")) {
            val (readSide, writeSide) = ParcelFileDescriptor.createPipe()
            thread(name = "chart-library-pipe", isDaemon = true) {
                runCatching {
                    writeSide.use { output ->
                        ParcelFileDescriptor.AutoCloseOutputStream(output).use { sink ->
                            source.inputStream().use { it.copyTo(sink) }
                        }
                    }
                }
            }
            return readSide
        }
        return ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun documentCursor(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val file = sourceFile(documentId)
        return MatrixCursor(columns).apply {
            val row = newRow()
            columns.forEach { column ->
                row.add(
                    column,
                    when (column) {
                        Document.COLUMN_DOCUMENT_ID -> documentId
                        Document.COLUMN_DISPLAY_NAME -> documentId.removePrefix("pipe-")
                        Document.COLUMN_MIME_TYPE -> "application/vnd.sqlite3"
                        Document.COLUMN_SIZE -> file.length()
                        Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                        Document.COLUMN_FLAGS -> Document.FLAG_SUPPORTS_THUMBNAIL
                        else -> null
                    },
                )
            }
        }
    }

    private fun sourceFile(documentId: String): File = File(
        requireNotNull(context).filesDir,
        "chart-library-test-documents/${documentId.removePrefix("pipe-")}",
    )

    companion object {
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
    }
}
