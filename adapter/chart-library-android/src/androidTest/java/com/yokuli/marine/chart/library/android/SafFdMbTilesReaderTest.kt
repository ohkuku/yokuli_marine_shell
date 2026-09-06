package com.yokuli.marine.chart.library.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.ChartPackageLease
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision
import com.yokuli.marine.map.domain.chartlibrary.ChartOpenResult
import com.yokuli.marine.map.domain.chartlibrary.ChartReadException
import com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator
import com.yokuli.marine.map.domain.chartlibrary.ChartReadFailure
import com.yokuli.marine.map.domain.chartlibrary.ChartReadRequest
import com.yokuli.marine.map.domain.chartlibrary.ChartTileKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafFdMbTilesReaderTest {
    private lateinit var context: Context
    private lateinit var documents: File
    private lateinit var authority: String

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        documents = File(context.filesDir, "chart-library-test-documents").apply {
            deleteRecursively()
            mkdirs()
        }
        authority = "${context.packageName}.chartlibrary.documents"
    }

    @After fun tearDown() { documents.deleteRecursively() }

    @Test fun seekableProviderReadsTileWithoutCopyingOrWritingOriginal() = runBlocking {
        val source = File(documents, "basic.mbtiles")
        val png = pngTile(256)
        createMbTiles(source, png)
        val beforeHash = source.sha256()
        val beforeFiles = context.filesDir.walkTopDown().filter(File::isFile)
            .map { it.relativeTo(context.filesDir).path }.toSet()
        val descriptorsBefore = File("/proc/self/fd").list()?.size
        var observedSourceBytes = 0L

        val result = AndroidChartResourceAccess(context.contentResolver).open(requestFor("basic.mbtiles"))
        assertTrue(result is ChartOpenResult.Opened)
        val session = (result as ChartOpenResult.Opened).session
        session.use {
            assertEquals("fixture", it.readMetadata(32)["name"])
            val payload = requireNotNull(it.readTile(ChartTileKey(0, 0, 0), MapTileScheme.MBTILES_TMS))
            assertArrayEquals(png, payload.bytes)
            assertEquals(256, payload.widthPx)
            assertEquals("image/png", payload.mimeType)
            observedSourceBytes = it.statistics().sourceBytesRead
            assertTrue(observedSourceBytes > 0)
        }

        assertEquals(beforeHash, source.sha256())
        val afterFiles = context.filesDir.walkTopDown().filter(File::isFile)
            .map { it.relativeTo(context.filesDir).path }.toSet()
        assertEquals(beforeFiles, afterFiles)
        assertFalse(File(context.filesDir, "chart_library").exists())
        println(
            "CL11_EVIDENCE " +
                "{\"scenario\":\"zero-copy-read\",\"sourceBytesRead\":$observedSourceBytes," +
                "\"implicitCopyBytes\":0,\"externalWriteBytes\":0,\"fdBefore\":$descriptorsBefore," +
                "\"fdAfter\":${File("/proc/self/fd").list()?.size}}",
        )
    }

    @Test fun pipeIsRejectedWithoutCreatingAnImplicitCopy() = runBlocking {
        createMbTiles(File(documents, "stream.mbtiles"), pngTile(256))
        val result = AndroidChartResourceAccess(context.contentResolver).open(requestFor("pipe-stream.mbtiles"))
        assertTrue(result is ChartOpenResult.Rejected)
        assertEquals(ChartReadFailure.DIRECT_READ_UNSUPPORTED, (result as ChartOpenResult.Rejected).failure)
        assertFalse(File(context.filesDir, "chart_library").exists())
    }

    @Test fun managedLocatorOpensOnlyInsidePrivateStoreAndHoldsLeaseUntilSessionClose() = runBlocking {
        val root = File(context.filesDir, "map_packages-managed-reader-test").also { it.deleteRecursively(); it.mkdirs() }
        val temporary = File(root, "fixture.mbtiles")
        val png = pngTile(256)
        createMbTiles(temporary, png)
        val sha = temporary.sha256()
        val database = File(root, "package-$sha/map.mbtiles").also { it.parentFile?.mkdirs() }
        assertTrue(temporary.renameTo(database))
        val leases = AtomicInteger(0)
        val access = AndroidChartResourceAccess(context.contentResolver, root) {
            leases.incrementAndGet()
            ChartPackageLease { leases.decrementAndGet() }
        }
        val request = ChartReadRequest(
            ChartAssetId("asset-managed"),
            ChartOpaqueLocator("yokuli-managed://$sha"),
            ChartContentRevision("managed:$sha", database.length(), database.lastModified(), contentSha256 = sha),
            1L,
        )

        val opened = access.open(request) as ChartOpenResult.Opened
        assertEquals(1, leases.get())
        assertArrayEquals(png, requireNotNull(opened.session.readTile(ChartTileKey(0, 0, 0), MapTileScheme.MBTILES_TMS)).bytes)
        opened.session.close()
        assertEquals(0, leases.get())

        val escaped = request.copy(locator = ChartOpaqueLocator("yokuli-managed://not-a-version"))
        assertEquals(ChartReadFailure.CANNOT_OPEN, (access.open(escaped) as ChartOpenResult.Rejected).failure)
        root.deleteRecursively()
    }

    @Test fun randomAccessUsesLongOffsetsBeyondFourGiB() {
        val source = File(documents, "sparse.bin")
        val offset = (4L * 1024 * 1024 * 1024) + 8192L
        RandomAccessFile(source, "rw").use {
            it.setLength(offset + 4)
            it.seek(offset)
            it.write(byteArrayOf(9, 8, 7, 6))
        }
        val opened = AndroidSafRandomAccessReader(context.contentResolver).open(documentUri("sparse.bin"))
        assertTrue(opened is SafRandomAccessOpenResult.Opened)
        (opened as SafRandomAccessOpenResult.Opened).handle.use {
            assertArrayEquals(byteArrayOf(9, 8, 7, 6), it.readAt(offset, 4))
        }
    }

    @Test fun closedHandleAndShortReadAreTypedFailures() {
        val source = File(documents, "short.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val opened = AndroidSafRandomAccessReader(context.contentResolver).open(documentUri("short.bin"))
            as SafRandomAccessOpenResult.Opened
        assertEquals(ChartReadFailure.SHORT_READ, opened.handle.readAtResult(2, 4).failureOrNull())
        opened.handle.close()
        assertEquals(ChartReadFailure.SESSION_CLOSED, opened.handle.readAtResult(0, 1).failureOrNull())
    }

    private fun requestFor(documentId: String) = ChartReadRequest(
        assetId = ChartAssetId("asset-basic"),
        locator = ChartOpaqueLocator(documentUri(documentId).toString()),
        revision = ChartContentRevision(
            identity = documentId,
            observedSizeBytes = File(documents, documentId.removePrefix("pipe-")).length(),
            observedModifiedAtMillis = null,
        ),
        sourceGeneration = 1,
    )

    private fun documentUri(documentId: String): Uri = DocumentsContract.buildDocumentUri(authority, documentId)

    private fun createMbTiles(file: File, tile: ByteArray) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB, UNIQUE(zoom_level,tile_column,tile_row))")
            db.execSQL("INSERT INTO metadata(name,value) VALUES ('name','fixture'),('format','png'),('minzoom','0'),('maxzoom','0'),('bounds','-180,-85,180,85')")
            db.execSQL("INSERT INTO tiles(zoom_level,tile_column,tile_row,tile_data) VALUES (0,0,0,?)", arrayOf(tile))
        }
    }

    private fun pngTile(size: Int): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff336699.toInt()) }
            .compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }

    private fun File.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(readBytes()).joinToString("") { "%02x".format(it) }

}

private fun <T> Result<T>.failureOrNull(): ChartReadFailure? = exceptionOrNull()
    ?.let { it as? ChartReadException }
    ?.failure
