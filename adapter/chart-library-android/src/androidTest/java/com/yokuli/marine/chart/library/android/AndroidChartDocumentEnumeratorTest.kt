package com.yokuli.marine.chart.library.android

import android.content.Context
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.map.domain.chartlibrary.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidChartDocumentEnumeratorTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val authority get() = "${context.packageName}.chartlibrary.documents"
    private val root get() = File(context.filesDir, "chart-library-test-documents")

    @Before fun resetFiles() {
        root.deleteRecursively()
        File(root, "coast/nested").mkdirs()
        File(root, "coast/a.mbtiles").writeBytes(byteArrayOf(1))
        File(root, "coast/nested/unknown-size-b.mbtiles").writeBytes(byteArrayOf(2))
        File(root, "coast/readme.txt").writeText("not a chart")
    }

    @Test fun recursivelyEnumeratesOnlyCandidatesAndKeepsProviderUnknownSize() = runBlocking {
        val result = AndroidChartDocumentEnumerator(context.contentResolver).enumerate(treeSource()) { false }
        assertTrue("unexpected enumeration: $result", result is ChartEnumerationResult.Complete)
        val documents = result.documents.sortedBy { it.displayPath }
        assertEquals(listOf("coast/a.mbtiles", "coast/nested/unknown-size-b.mbtiles"), documents.map { it.displayPath })
        assertNull(documents.last().sizeBytes)
        assertTrue(documents.all { it.identity.authority == authority })
    }

    @Test fun singleDocumentUsesSameEnumeratorAndCancellationNeverClaimsComplete() = runBlocking {
        val uri = DocumentsContract.buildDocumentUri(authority, "coast/a.mbtiles")
        val source = ChartLibrarySource(
            ChartSourceId(UUID.randomUUID().toString()), ChartLibrarySourceKind.SINGLE_DOCUMENT,
            ChartOpaqueLocator(uri.toString()), "a.mbtiles", recursive = false, grantState = ChartGrantState.GRANTED,
        )
        val single = AndroidChartDocumentEnumerator(context.contentResolver).enumerate(source) { false }
        assertEquals("unexpected enumeration: $single", 1, single.documents.size)
        assertTrue(AndroidChartDocumentEnumerator(context.contentResolver).enumerate(treeSource()) { true } is ChartEnumerationResult.Cancelled)
    }

    @Test fun failedSubtreeIsPartialAndDoesNotHideSuccessfulSiblings() = runBlocking {
        File(root, "unreadable-dir").mkdirs()
        File(root, "unreadable-dir/hidden.mbtiles").writeBytes(byteArrayOf(3))
        val result = AndroidChartDocumentEnumerator(context.contentResolver).enumerate(treeSource()) { false }
        assertTrue("unexpected enumeration: $result", result is ChartEnumerationResult.Partial)
        assertTrue(result.documents.any { it.displayPath == "coast/a.mbtiles" })
        assertTrue((result as ChartEnumerationResult.Partial).issues.any { it.kind == ChartEnumerationIssueKind.QUERY_FAILED })
    }

    private fun treeSource() = ChartLibrarySource(
        ChartSourceId(UUID.randomUUID().toString()), ChartLibrarySourceKind.TREE,
        ChartOpaqueLocator(DocumentsContract.buildTreeDocumentUri(authority, "root").toString()),
        "root", grantState = ChartGrantState.GRANTED,
    )
}
