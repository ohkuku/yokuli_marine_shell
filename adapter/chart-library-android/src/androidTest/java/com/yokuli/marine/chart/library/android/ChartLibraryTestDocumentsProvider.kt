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

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = parentDocumentId.removePrefix("root/").trimEnd('/')
        val child = documentId.removePrefix("root/")
        return parentDocumentId == "root" || child.startsWith("$parent/")
    }

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(
        projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS),
    ).apply { addRow(arrayOf("root", "root", "Chart tests", Root.FLAG_SUPPORTS_CREATE)) }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        documentCursor(documentId, projection)

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (parentDocumentId.contains("unreadable-dir")) error("Synthetic unreadable subtree")
        val parent = sourceFile(parentDocumentId)
        val columns = projection ?: DEFAULT_DOCUMENT_PROJECTION
        return MatrixCursor(columns).apply {
            parent.listFiles().orEmpty().sortedBy(File::getName).forEach { file ->
                val id = file.relativeTo(documentRoot()).invariantSeparatorsPath
                addDocumentRow(id, file, columns)
            }
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        require(mode == "r")
        val source = sourceFile(documentId)
        require(source.isFile)
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
            addDocumentRow(documentId, file, columns)
        }
    }

    private fun MatrixCursor.addDocumentRow(documentId: String, file: File, columns: Array<out String>) {
        val row = newRow()
        columns.forEach { column ->
            row.add(
                column,
                when (column) {
                    Document.COLUMN_DOCUMENT_ID -> documentId
                    Document.COLUMN_DISPLAY_NAME -> file.name
                    Document.COLUMN_MIME_TYPE -> when {
                        file.isDirectory -> Document.MIME_TYPE_DIR
                        file.extension.equals("mbtiles", ignoreCase = true) -> "application/vnd.sqlite3"
                        else -> "text/plain"
                    }
                    Document.COLUMN_SIZE -> if (file.name.startsWith("unknown-size")) null else file.length()
                    Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                    Document.COLUMN_FLAGS -> if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_THUMBNAIL
                    else -> null
                },
            )
        }
    }

    private fun sourceFile(documentId: String): File {
        val clean = documentId.removePrefix("pipe-")
        require(!clean.contains("..") && !clean.startsWith('/'))
        return if (clean == "root") documentRoot() else File(documentRoot(), clean)
    }

    private fun documentRoot(): File = File(requireNotNull(context).filesDir, "chart-library-test-documents")

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
