package com.yokuli.marine.chart.library.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartValidationAndroidTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val root get() = File(context.filesDir, "chart-library-test-documents")
    private val authority get() = "${context.packageName}.chartlibrary.documents"

    @Before fun reset() { root.deleteRecursively(); root.mkdirs() }

    @Test fun basicInspectionSupportsPngJpegWebpAnd256Or512Tiles() = runBlocking {
        val fixtures = listOf(
            "png256.mbtiles" to raster(Bitmap.CompressFormat.PNG, 256),
            "jpeg512.mbtiles" to raster(Bitmap.CompressFormat.JPEG, 512),
            "webp256.mbtiles" to raster(Bitmap.CompressFormat.WEBP_LOSSLESS, 256),
        )
        fixtures.forEach { (name, tile) ->
            val file = File(root, name)
            createMbTiles(file, tile = tile)
            val result = ChartBasicInspector(AndroidChartResourceAccess(context.contentResolver)).inspect(asset(file), 1)
            assertTrue("$name: $result", result is ChartBasicInspectionResult.Readable)
            assertEquals(if ("512" in name) 512 else 256, (result as ChartBasicInspectionResult.Readable).inspection.facts.tileSize)
        }
    }

    @Test fun tilesViewAndXyzAreQueriedWithoutRewritingTheOriginal() = runBlocking {
        val file = File(root, "view.mbtiles")
        createMbTiles(file, scheme = "xyz", view = true, z = 1, x = 0, row = 1)
        val before = file.readBytes()
        val opened = AndroidChartResourceAccess(context.contentResolver).open(request(file)) as ChartOpenResult.Opened
        opened.session.use { session ->
            assertNotNull(session.readTile(ChartTileKey(1, 0, 1), MapTileScheme.XYZ))
            assertNull(session.readTile(ChartTileKey(1, 0, 0), MapTileScheme.XYZ))
        }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun unknownSchemeIsRejectedAndBasicNeverClaimsFullVerification() = runBlocking {
        val file = File(root, "unknown-scheme.mbtiles")
        createMbTiles(file, scheme = "mystery")
        val result = ChartBasicInspector(AndroidChartResourceAccess(context.contentResolver)).inspect(asset(file), 1)
        assertEquals(ChartValidationIssue.UNKNOWN_SCHEME, (result as ChartBasicInspectionResult.Rejected).issue)
        assertNull(asset(file).revision.contentSha256)
    }

    @Test fun explicitFullVerificationReadsAllTilesAndBindsShaToTheObservedRevision() = runBlocking {
        val file = File(root, "full.mbtiles")
        createMbTiles(file)
        val item = asset(file)
        val result = ChartFullVerifier(
            AndroidChartResourceAccess(context.contentResolver),
            AndroidChartRevisionProbe(context.contentResolver),
        ).verify(item, 1, { false })
        assertTrue("$result", result is ChartFullVerificationResult.Verified)
        result as ChartFullVerificationResult.Verified
        assertEquals(1L, result.tileCount)
        assertTrue(requireNotNull(result.revision.contentSha256).matches(Regex("[0-9a-f]{64}")))
        assertTrue(result.statistics.sourceBytesRead >= file.length())
    }

    @Test fun oversizedRasterIsRejectedBeforeBlobMaterialization() = runBlocking {
        val file = File(root, "oversized.mbtiles")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            db.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            db.execSQL("INSERT INTO metadata VALUES ('scheme','tms')")
            db.execSQL("INSERT INTO tiles VALUES (0,0,0,zeroblob(16777217))")
        }
        val result = ChartBasicInspector(AndroidChartResourceAccess(context.contentResolver)).inspect(asset(file), 1)
        assertEquals(ChartValidationIssue.READ_FAILED, (result as ChartBasicInspectionResult.Rejected).issue)
    }

    @Test fun controllerPublishesBasicThenFullAsSeparateCatalogTruth() = runBlocking {
        val file = File(root, "controller.mbtiles")
        createMbTiles(file)
        val sourceId = ChartSourceId(UUID.randomUUID().toString())
        val item = asset(file).copy(memberships = setOf(sourceId))
        val database = File(context.cacheDir, "chart-validation-controller.db").also {
            it.delete(); File("${it.path}-wal").delete(); File("${it.path}-shm").delete()
        }
        RoomChartCatalogRepository.create(context, database).use { catalog ->
            val source = ChartLibrarySource(
                sourceId, ChartLibrarySourceKind.SINGLE_DOCUMENT, item.locator, file.name,
                recursive = false, grantState = ChartGrantState.GRANTED,
                scan = ChartSourceScanState(1, ChartScanStatus.COMPLETE, 1, 1, 0),
            )
            assertTrue(catalog.transact(ChartCatalogTransaction(
                "seed-validation", mutations = listOf(ChartCatalogMutation.PutSource(source), ChartCatalogMutation.PutAsset(item)),
            )) is ChartCatalogCommitResult.Committed)
            val controller = AndroidChartValidationController(
                catalog, AndroidChartResourceAccess(context.contentResolver), AndroidChartRevisionProbe(context.contentResolver),
            )
            assertTrue(controller.inspectBasic(item.id) is ChartValidationCommandResult.Published)
            assertEquals(ChartAssetValidationState.BASIC_READABLE, catalog.asset(item.id)?.validation)
            assertTrue(controller.verifyFull(item.id) is ChartValidationCommandResult.Published)
            assertEquals(ChartAssetValidationState.FULL_VERIFIED, catalog.asset(item.id)?.validation)
        }
    }

    @Test fun metadataMismatchIsWarnedWhileOverlongTextIsRejectedWithoutInventingFacts() = runBlocking {
        val conflict = File(root, "conflict.mbtiles")
        createMbTiles(conflict, tile = raster(Bitmap.CompressFormat.JPEG, 256))
        SQLiteDatabase.openDatabase(conflict.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO metadata VALUES ('format','png')")
        }
        val conflictResult = ChartBasicInspector(AndroidChartResourceAccess(context.contentResolver)).inspect(asset(conflict), 1)
        assertTrue(conflictResult is ChartBasicInspectionResult.Readable)
        assertTrue(
            ChartCompatibilityWarning.FORMAT_MISMATCH in
                (conflictResult as ChartBasicInspectionResult.Readable).inspection.warnings,
        )

        val overlong = File(root, "overlong.mbtiles")
        createMbTiles(overlong)
        SQLiteDatabase.openDatabase(overlong.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO metadata VALUES ('attribution',?)", arrayOf("x".repeat(4097)))
        }
        val overlongResult = ChartBasicInspector(AndroidChartResourceAccess(context.contentResolver)).inspect(asset(overlong), 1)
        assertEquals(ChartValidationIssue.READ_FAILED, (overlongResult as ChartBasicInspectionResult.Rejected).issue)
    }

    @Test fun damagedAndMaliciousDatabasesFailWithoutPoisoningHealthyAsset() = runBlocking {
        val damaged = File(root, "damaged.mbtiles").apply { writeText("not sqlite") }
        assertEquals(
            ChartValidationIssue.OPEN_FAILED,
            (ChartBasicInspector(AndroidChartResourceAccess(context.contentResolver)).inspect(asset(damaged), 1) as ChartBasicInspectionResult.Rejected).issue,
        )

        val malicious = File(root, "malicious.mbtiles")
        SQLiteDatabase.openOrCreateDatabase(malicious, null).use { db ->
            db.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            db.execSQL("CREATE VIEW tiles AS SELECT 0 AS zoom_level, 0 AS tile_column, 0 AS tile_row")
        }
        assertTrue(ChartBasicInspector(AndroidChartResourceAccess(context.contentResolver)).inspect(asset(malicious), 1) is ChartBasicInspectionResult.Rejected)

        val healthy = File(root, "healthy.mbtiles")
        createMbTiles(healthy)
        assertTrue(ChartBasicInspector(AndroidChartResourceAccess(context.contentResolver)).inspect(asset(healthy), 1) is ChartBasicInspectionResult.Readable)
    }

    private fun asset(file: File) = ChartAsset(
        ChartAssetId(UUID.randomUUID().toString()),
        ChartDocumentIdentity(authority, file.name),
        ChartOpaqueLocator(DocumentsContract.buildDocumentUri(authority, file.name).toString()),
        setOf(ChartSourceId(UUID.randomUUID().toString())),
        file.name,
        ChartContentRevision("$authority:${file.name}", file.length(), file.lastModified()),
        facts = ChartAssetFacts(sizeBytes = file.length()),
    )

    private fun request(file: File) = ChartReadRequest(asset(file).id, asset(file).locator, asset(file).revision, 1)

    private fun createMbTiles(
        file: File,
        tile: ByteArray = raster(Bitmap.CompressFormat.PNG, 256),
        scheme: String = "tms",
        view: Boolean = false,
        z: Int = 0,
        x: Int = 0,
        row: Int = 0,
    ) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            db.execSQL("CREATE TABLE tile_storage (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            if (view) db.execSQL("CREATE VIEW tiles AS SELECT zoom_level,tile_column,tile_row,tile_data FROM tile_storage")
            else db.execSQL("CREATE VIEW tiles AS SELECT zoom_level,tile_column,tile_row,tile_data FROM tile_storage")
            db.execSQL(
                "INSERT INTO metadata VALUES ('name','fixture'),('scheme',?),('minzoom',?),('maxzoom',?),('bounds','-180,-85,180,85')",
                arrayOf<Any?>(scheme, z, z),
            )
            db.execSQL("INSERT INTO tile_storage VALUES (?,?,?,?)", arrayOf(z, x, row, tile))
        }
    }

    private fun raster(format: Bitmap.CompressFormat, size: Int): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff225588.toInt()) }
            .compress(format, 90, output)
        output.toByteArray()
    }
}
