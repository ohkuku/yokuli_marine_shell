package com.yokuli.marine.chart.library.android

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import com.yokuli.marine.map.domain.chartlibrary.*
import java.security.MessageDigest

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
        fun cancelled() = shouldCancel().also { if (it) cancellation.cancel() }
        fun timedOut() = (SystemClock.elapsedRealtime() - started >= timeBudgetMillis)
            .also { if (it) cancellation.cancel() }

        if (cancelled()) return ChartEnumerationResult.Cancelled(emptyList())
        if (timedOut()) return ChartEnumerationResult.Partial(
            emptyList(), listOf(ChartEnumerationIssue(null, ChartEnumerationIssueKind.TIME_BUDGET_REACHED)),
        )
        return try {
            when (source.kind) {
                ChartLibrarySourceKind.SINGLE_DOCUMENT -> {
                    queryDocument(root, root.lastPathSegment.orEmpty(), cancellation)?.let(documents::add)
                }
                ChartLibrarySourceKind.TREE -> {
                    val rootId = DocumentsContract.getTreeDocumentId(root)
                    val queue = ArrayDeque<DirectoryFrame>().apply { add(DirectoryFrame(rootId, "", 0)) }
                    var enumeratedEntries = 0
                    while (queue.isNotEmpty() && enumeratedEntries < maxDocuments && !cancelled() && !timedOut()) {
                        val frame = queue.removeFirst()
                        if (!visited.add(frame.documentId)) continue
                        if (frame.depth >= maxDepth) {
                            issues += ChartEnumerationIssue(frame.path, ChartEnumerationIssueKind.DEPTH_LIMIT)
                            continue
                        }
                        val children = try {
                            queryChildren(root, frame, cancellation, maxDocuments - enumeratedEntries)
                        } catch (_: SecurityException) {
                            return ChartEnumerationResult.Failed(
                                ChartEnumerationIssue(frame.path, ChartEnumerationIssueKind.PERMISSION_LOST),
                            )
                        } catch (_: Throwable) {
                            issues += ChartEnumerationIssue(frame.path, ChartEnumerationIssueKind.QUERY_FAILED)
                            continue
                        }
                        enumeratedEntries += children.rows.size
                        children.rows.forEach { child ->
                            if (child.directory) {
                                if (source.recursive) queue += DirectoryFrame(child.documentId, child.displayPath, frame.depth + 1)
                            } else if (child.chartCandidate) {
                                documents += child.toDocument(requireNotNull(root.authority), root, withinTree = true)
                            }
                        }
                        if (children.truncated) {
                            issues += ChartEnumerationIssue(frame.path, ChartEnumerationIssueKind.LIMIT_REACHED)
                            break
                        }
                    }
                    if (queue.isNotEmpty()) {
                        issues += ChartEnumerationIssue(null, ChartEnumerationIssueKind.LIMIT_REACHED)
                    }
                }
                ChartLibrarySourceKind.MANAGED -> return ChartEnumerationResult.Failed(
                    ChartEnumerationIssue(null, ChartEnumerationIssueKind.QUERY_FAILED),
                )
            }
            when {
                shouldCancel() -> ChartEnumerationResult.Cancelled(documents)
                timedOut() -> ChartEnumerationResult.Partial(
                    documents,
                    (issues + ChartEnumerationIssue(null, ChartEnumerationIssueKind.TIME_BUDGET_REACHED)).distinct(),
                )
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

    private fun queryDocument(uri: Uri, fallbackName: String, cancellation: CancellationSignal): ChartDiscoveredDocument? {
        val fallbackId = runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: "uri-${uri.toString().sha256()}"
        val cursor = runCatching {
            resolver.query(uri, PROJECTION, null, null, null, cancellation)
        }.getOrNull() ?: resolver.query(uri, OPENABLE_PROJECTION, null, null, null, cancellation)
        return cursor?.use {
            if (!it.moveToFirst()) return@use null
            val row = it.toRow(fallbackName, fallbackId)
            row.takeIf(Row::chartCandidate)?.toDocument(requireNotNull(uri.authority), uri, withinTree = false)
        }
    }

    private fun queryChildren(
        tree: Uri,
        frame: DirectoryFrame,
        cancellation: CancellationSignal,
        maximumRows: Int,
    ): ChildPage {
        require(maximumRows > 0)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, frame.documentId)
        return resolver.query(childrenUri, PROJECTION, null, null, null, cancellation)?.use { cursor ->
            val rows = buildList {
                while (size < maximumRows && cursor.moveToNext()) {
                    val row = cursor.toRow("")
                    val path = listOf(frame.path, row.name).filter(String::isNotBlank).joinToString("/")
                    add(row.copy(displayPath = path))
                }
            }
            ChildPage(rows, truncated = rows.size == maximumRows && cursor.moveToNext())
        } ?: throw IllegalStateException("Provider returned null cursor")
    }

    private fun android.database.Cursor.toRow(fallbackName: String, fallbackDocumentId: String? = null): Row {
        fun text(column: String): String? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)
        fun long(column: String): Long? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
        val id = (text(Document.COLUMN_DOCUMENT_ID) ?: fallbackDocumentId)
            ?.take(MAX_DOCUMENT_ID_LENGTH)
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Provider did not expose a stable document identity")
        val name = (text(Document.COLUMN_DISPLAY_NAME) ?: text(OpenableColumns.DISPLAY_NAME))
            ?.take(MAX_NAME_LENGTH)
            ?.ifBlank { fallbackName }
            ?: fallbackName
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
    private data class ChildPage(val rows: List<Row>, val truncated: Boolean)
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
        private val OPENABLE_PROJECTION = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
        )
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
