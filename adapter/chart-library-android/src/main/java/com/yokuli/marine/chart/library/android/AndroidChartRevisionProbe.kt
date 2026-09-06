package com.yokuli.marine.chart.library.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract.Document
import com.yokuli.marine.map.domain.chartlibrary.ChartAsset
import com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision
import com.yokuli.marine.map.domain.chartlibrary.ChartRevisionProbe

class AndroidChartRevisionProbe(private val resolver: ContentResolver) : ChartRevisionProbe {
    override suspend fun currentRevision(asset: ChartAsset): ChartContentRevision? = runCatching {
        resolver.query(
            Uri.parse(asset.locator.value),
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            fun long(name: String): Long? = cursor.getColumnIndex(name).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            val documentId = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID).takeIf { it >= 0 }?.let(cursor::getString)
                ?: asset.documentIdentity.documentId
            ChartContentRevision(
                identity = "${asset.documentIdentity.authority}:$documentId",
                observedSizeBytes = long(Document.COLUMN_SIZE),
                observedModifiedAtMillis = long(Document.COLUMN_LAST_MODIFIED)?.takeIf { it > 0L },
                providerRevisionHint = asset.revision.providerRevisionHint,
            )
        }
    }.getOrNull()
}
