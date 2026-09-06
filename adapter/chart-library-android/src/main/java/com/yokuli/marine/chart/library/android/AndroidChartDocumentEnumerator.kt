package com.yokuli.marine.chart.library.android

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.yokuli.marine.map.domain.chartlibrary.*

class AndroidChartDocumentEnumerator(
    private val resolver: ContentResolver,
    private val maxDocuments: Int = MAX_DOCUMENTS,
    private val maxDepth: Int = MAX_DEPTH,
    private val timeBudgetMillis: Long = TIME_BUDGET_MILLIS,
) : ChartDocumentEnumerationPort {
    init {
        require(maxDocuments in 1..MAX_DOCUMENTS)
        require(maxDepth in 1..MAX_DEPTH)
        require(timeBudgetMillis in 1..TIME_BUDGET_MILLIS)
    }

    override suspend fun enumerate(
        source: ChartLibrarySource,
        shouldCancel: () -> Boolean,
    ): ChartEnumerationResult {
        val root = Uri.parse(source.locator.value)
        val started = SystemClock.elapsedRealtime()
        val documents = mutableListOf<ChartDiscoveredDocument>()
        val issues = mutableListOf<ChartEnumerationIssue>()
        val visited = mutableSetOf<String>()
        val cancellation = CancellationSignal()
        fun stopped(): Boolean {
            val stop = shouldCancel() || SystemClock.elapsedRealtime() - started >= timeBudgetMillis
            if (stop) cancellation.cancel()
            return stop
        }

        if (stopped()) return ChartEnumerationResult.Cancelled(emptyList())
        return try {
            when (source.kind) {
                ChartLibrarySourceKind.SINGLE_DOCUMENT -> {
                    queryDocument(root, root.lastPathSegment.orEmpty(), cancellation)?.let(documents::add)
                }
                ChartLibrarySourceKind.TREE -> {
                    val rootId = DocumentsContract.getTreeDocumentId(root)
                    val queue = ArrayDeque<DirectoryFrame>().apply { add(DirectoryFrame(rootId, "", 0)) }
                    while (queue.isNotEmpty() && documents.size < maxDocuments && !stopped()) {
                        val frame = queue.removeFirst()
                        if (!visited.add(frame.documentId)) continue
                        if (frame.depth >= maxDepth) {
                            issues += ChartEnumerationIssue(frame.path, ChartEnumerationIssueKind.DEPTH_LIMIT)
                            continue
                        }
                        val children = try {
                            queryChildren(root, frame, cancellation)
                        } catch (_: SecurityException) {
                            return ChartEnumerationResult.Failed(
                                ChartEnumerationIssue(frame.path, ChartEnumerationIssueKind.PERMISSION_LOST),
                            )
                        } catch (_: Throwable) {
                            issues += ChartEnumerationIssue(frame.path, ChartEnumerationIssueKind.QUERY_FAILED)
                            continue
                        }
                        children.forEach { child ->
                            if (child.directory) {
                                if (source.recursive) queue += DirectoryFrame(child.documentId, child.displayPath, frame.depth + 1)
                            } else if (child.chartCandidate) {
                                documents += child.toDocument(requireNotNull(root.authority), root, withinTree = true)
                            }
                        }
                    }
                    if (documents.size >= maxDocuments || queue.isNotEmpty()) {
                        issues += ChartEnumerationIssue(null, ChartEnumerationIssueKind.LIMIT_REACHED)
                    }
                }
                ChartLibrarySourceKind.MANAGED -> return ChartEnumerationResult.Failed(
                    ChartEnumerationIssue(null, ChartEnumerationIssueKind.QUERY_FAILED),
                )
            }
            when {
                shouldCancel() -> ChartEnumerationResult.Cancelled(documents)
                issues.isNotEmpty() -> ChartEnumerationResult.Partial(documents, issues)
                else -> ChartEnumerationResult.Complete(documents)
            }
        } catch (_: SecurityException) {
            ChartEnumerationResult.Failed(ChartEnumerationIssue(null, ChartEnumerationIssueKind.PERMISSION_LOST))
        } catch (_: Throwable) {
            ChartEnumerationResult.Failed(ChartEnumerationIssue(null, ChartEnumerationIssueKind.QUERY_FAILED))
        } finally {
            cancellation.cancel()
        }
    }

    private fun queryDocument(uri: Uri, fallbackName: String, cancellation: CancellationSignal): ChartDiscoveredDocument? =
        resolver.query(uri, PROJECTION, null, null, null, cancellation)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val row = cursor.toRow(fallbackName)
            row.takeIf(Row::chartCandidate)?.toDocument(requireNotNull(uri.authority), uri, withinTree = false)
        }

    private fun queryChildren(tree: Uri, frame: DirectoryFrame, cancellation: CancellationSignal): List<Row> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, frame.documentId)
        return resolver.query(childrenUri, PROJECTION, null, null, null, cancellation)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val row = cursor.toRow("")
                    val path = listOf(frame.path, row.name).filter(String::isNotBlank).joinToString("/")
                    add(row.copy(displayPath = path))
                }
            }
        } ?: throw IllegalStateException("Provider returned null cursor")
    }

    private fun android.database.Cursor.toRow(fallbackName: String): Row {
        fun text(column: String): String? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)
        fun long(column: String): Long? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
        val id = requireNotNull(text(Document.COLUMN_DOCUMENT_ID)).take(MAX_DOCUMENT_ID_LENGTH)
        val name = text(Document.COLUMN_DISPLAY_NAME)?.take(MAX_NAME_LENGTH)?.ifBlank { fallbackName } ?: fallbackName
        val mime = text(Document.COLUMN_MIME_TYPE)?.take(MAX_MIME_LENGTH)
        return Row(
            documentId = id,
            name = name,
            displayPath = name,
            mimeType = mime,
            sizeBytes = long(Document.COLUMN_SIZE)?.takeIf { it >= 0L },
            modifiedAtMillis = long(Document.COLUMN_LAST_MODIFIED)?.takeIf { it > 0L },
            directory = mime == Document.MIME_TYPE_DIR,
        )
    }

    private data class DirectoryFrame(val documentId: String, val path: String, val depth: Int)
    private data class Row(
        val documentId: String,
        val name: String,
        val displayPath: String,
        val mimeType: String?,
        val sizeBytes: Long?,
        val modifiedAtMillis: Long?,
        val directory: Boolean,
    ) {
        val chartCandidate: Boolean get() = !directory && (
            name.endsWith(".mbtiles", ignoreCase = true) ||
                mimeType in CHART_MIME_TYPES
            )
        val pending: Boolean get() = sizeBytes == 0L || name.endsWith(".part", true) || name.endsWith(".tmp", true)
        fun toDocument(authority: String, sourceUri: Uri, withinTree: Boolean): ChartDiscoveredDocument = ChartDiscoveredDocument(
            identity = ChartDocumentIdentity(authority, documentId),
            locator = ChartOpaqueLocator(
                if (withinTree) DocumentsContract.buildDocumentUriUsingTree(sourceUri, documentId).toString()
                else sourceUri.toString(),
            ),
            displayPath = displayPath,
            sizeBytes = sizeBytes,
            modifiedAtMillis = modifiedAtMillis,
            mimeType = mimeType,
            pending = pending,
        )
    }

    companion object {
        private const val MAX_DOCUMENTS = 10_000
        private const val MAX_DEPTH = 32
        private const val TIME_BUDGET_MILLIS = 30_000L
        private const val MAX_DOCUMENT_ID_LENGTH = 1_024
        private const val MAX_NAME_LENGTH = 256
        private const val MAX_MIME_LENGTH = 256
        private val CHART_MIME_TYPES = setOf("application/vnd.sqlite3", "application/x-sqlite3")
        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
