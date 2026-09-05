package com.yokuli.marine.map.offline

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokuli.marine.map.domain.ChartPackageImportRequest
import com.yokuli.marine.map.domain.ChartPackageImportException
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import java.io.File
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMbTilesRepositoryTest {
    @Test
    fun viewsSchemaAndMissingRecommendedMetadataAreDerived() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testRoot = File(context.cacheDir, "mbtiles-view-derived-test").also { it.deleteRecursively(); it.mkdirs() }
        val source = File(testRoot, "views.mbtiles")
        SQLiteDatabase.openOrCreateDatabase(source, null).use { database ->
            database.execSQL("CREATE TABLE raw_metadata (name TEXT, value TEXT)")
            database.execSQL("CREATE TABLE raw_tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            database.execSQL("CREATE VIEW metadata AS SELECT name, value FROM raw_metadata")
            database.execSQL("CREATE VIEW tiles AS SELECT zoom_level, tile_column, tile_row, tile_data FROM raw_tiles")
            database.execSQL("INSERT INTO raw_metadata(name,value) VALUES('format','png')")
            database.execSQL(
                "INSERT INTO raw_tiles(zoom_level,tile_column,tile_row,tile_data) VALUES(2,2,1,?)",
                arrayOf(asymmetricTile(256, Bitmap.CompressFormat.PNG)),
            )
        }
        val repository = AndroidMbTilesRepository(context.contentResolver, File(testRoot, "packages"))

        val candidate = repository.inspect(source.toURI().toString())

        assertEquals(2, candidate.minZoom)
        assertEquals(2, candidate.maxZoom)
        assertTrue(candidate.coverage.west < candidate.coverage.east)
        assertEquals(com.yokuli.marine.map.domain.ChartPackageValidationLevel.FULL_TILE_DECODED, candidate.validationLevel)
        testRoot.deleteRecursively()
        Unit
    }

    @Test
    fun invalidCoordinatesDuplicatesAndCorruptPayloadsAreRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testRoot = File(context.cacheDir, "mbtiles-invalid-index-test").also { it.deleteRecursively(); it.mkdirs() }

        suspend fun failure(name: String, mutate: (SQLiteDatabase) -> Unit): ChartPackageImportFailure {
            val source = File(testRoot, "$name.mbtiles")
            createRasterMbTiles(source)
            SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use(mutate)
            val repository = AndroidMbTilesRepository(context.contentResolver, File(testRoot, "packages-$name"))
            return try {
                repository.inspect(source.toURI().toString())
                error("Expected $name to fail")
            } catch (error: ChartPackageImportException) {
                error.reason
            }
        }

        assertEquals(
            ChartPackageImportFailure.INVALID_TILE_INDEX,
            failure("coordinate") { it.execSQL("UPDATE tiles SET tile_column=2 WHERE zoom_level=0") },
        )
        assertEquals(
            ChartPackageImportFailure.DUPLICATE_TILE,
            failure("duplicate") {
                it.execSQL("INSERT INTO tiles SELECT zoom_level,tile_column,tile_row,tile_data FROM tiles")
            },
        )
        assertEquals(
            ChartPackageImportFailure.CORRUPT_TILE,
            failure("payload") { it.execSQL("UPDATE tiles SET tile_data=X'01'") },
        )
        testRoot.deleteRecursively()
        Unit
    }

    @Test
    fun crashJournalReconciliationKeepsAnUsableVersion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testRoot = File(context.cacheDir, "mbtiles-journal-test").also { it.deleteRecursively(); it.mkdirs() }
        val packages = File(testRoot, "packages")
        val v1 = File(testRoot, "v1.mbtiles")
        createRasterMbTiles(v1)
        val initial = AndroidMbTilesRepository(context.contentResolver, packages)
        val first = initial.inspect(v1.toURI().toString())
        val installedV1 = initial.commit(
            ChartPackageImportRequest(first.stagedImportId, "Harbour", "Unknown", "Unknown", "Unknown", "1"),
        )
        val v2 = File(testRoot, "v2.mbtiles")
        createRasterMbTiles(v2, tileData = asymmetricTile(512, Bitmap.CompressFormat.PNG))
        val crashing = AndroidMbTilesRepository(
            context.contentResolver,
            packages,
            installCheckpoint = { checkpoint ->
                if (checkpoint == InstallCheckpoint.AFTER_PUBLISH) error("simulated process death")
            },
        )
        val second = crashing.inspect(v2.toURI().toString())
        try {
            crashing.commit(
                ChartPackageImportRequest(
                    second.stagedImportId,
                    "Harbour",
                    "Unknown",
                    "Unknown",
                    "Unknown",
                    "2",
                    replaceLogicalPackageId = installedV1.logicalId,
                ),
            )
        } catch (_: Throwable) {
            // A new process does not receive this exception; it only sees the journal and files.
        }

        val recovered = AndroidMbTilesRepository(context.contentResolver, packages).listInstalled()
        assertEquals(1, recovered.size)
        assertEquals(installedV1.logicalId, recovered.single().logicalId)
        assertTrue(File(android.net.Uri.parse(recovered.single().localUri).path!!).isFile)
        assertFalse(File(packages, ".install-journal.properties").exists())
        testRoot.deleteRecursively()
        Unit
    }
    @Test
    fun importIsValidatedAndAtomicAndDeleteKeepsUnrelatedUserFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testRoot = File(context.cacheDir, "mbtiles-repository-test").also { it.deleteRecursively(); it.mkdirs() }
        val source = File(testRoot, "source.mbtiles")
        createRasterMbTiles(source)
        val packagesRoot = File(testRoot, "packages")
        val unrelated = File(testRoot, "user-route.txt").also { it.writeText("keep") }
        val repository = AndroidMbTilesRepository(context.contentResolver, packagesRoot)

        val candidate = repository.inspect(source.toURI().toString())
        val installed = repository.commit(
            ChartPackageImportRequest(
                candidate.stagedImportId, "Test chart", "Test source", "CC-BY", "Test attribution", "1",
            ),
        )

        assertEquals(listOf(installed), repository.listInstalled())
        assertTrue(File(android.net.Uri.parse(installed.localUri).path!!).isFile)
        repository.delete(installed.id)
        assertTrue(repository.listInstalled().isEmpty())
        assertTrue(unrelated.isFile)
        assertFalse(packagesRoot.listFiles().orEmpty().any { it.name.startsWith(".staging-") })

        val corruptTile = File(testRoot, "corrupt-tile.mbtiles")
        createRasterMbTiles(corruptTile, tileData = byteArrayOf(0x01))
        val corruptFailure = try {
            repository.inspect(corruptTile.toURI().toString())
            null
        } catch (error: ChartPackageImportException) {
            error
        }
        assertEquals(ChartPackageImportFailure.CORRUPT_TILE, corruptFailure?.reason)

        val invalid = File(testRoot, "invalid.mbtiles").also { it.writeText("not sqlite") }
        val failure = try {
            repository.inspect(invalid.toURI().toString())
            null
        } catch (error: ChartPackageImportException) {
            error
        }
        assertEquals(ChartPackageImportFailure.INVALID_DATABASE, failure?.reason)
        assertFalse(packagesRoot.listFiles().orEmpty().any { it.name.startsWith(".staging-") })
        testRoot.deleteRecursively()
        Unit
    }

    @Test
    fun pngAndJpegTileSizesAreDetectedAndXyzRowsAreNormalisedToMbTilesTms() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testRoot = File(context.cacheDir, "mbtiles-format-test").also { it.deleteRecursively(); it.mkdirs() }
        val repository = AndroidMbTilesRepository(context.contentResolver, File(testRoot, "packages"))
        val png = File(testRoot, "small-xyz.mbtiles")
        createRasterMbTiles(
            png,
            format = "png",
            zoom = 1,
            row = 0,
            scheme = "xyz",
            tileData = asymmetricTile(128, Bitmap.CompressFormat.PNG),
        )
        val pngCandidate = repository.inspect(png.toURI().toString())
        assertEquals(128, pngCandidate.tileSize)
        assertEquals("png", pngCandidate.rasterFormat)

        val installed = repository.commit(
            ChartPackageImportRequest(
                pngCandidate.stagedImportId, "XYZ fixture", "Fixture", "CC0", "Fixture", "1",
            ),
        )
        SQLiteDatabase.openDatabase(
            android.net.Uri.parse(installed.localUri).path!!,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            val normalisedRow = database.rawQuery("SELECT tile_row FROM tiles", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
            assertEquals(1, normalisedRow)
        }

        val jpeg = File(testRoot, "large-jpeg.mbtiles")
        createRasterMbTiles(
            jpeg,
            format = "jpeg",
            tileData = asymmetricTile(512, Bitmap.CompressFormat.JPEG),
        )
        val jpegCandidate = repository.inspect(jpeg.toURI().toString())
        assertEquals(512, jpegCandidate.tileSize)
        assertEquals("jpeg", jpegCandidate.rasterFormat)
        testRoot.deleteRecursively()
        Unit
    }

    @Test
    fun tracedNoaaSubsetPassesTheSameImportPathAsUserDocuments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testRoot = File(context.cacheDir, "mbtiles-noaa-test").also { it.deleteRecursively(); it.mkdirs() }
        val source = File(testRoot, "noaa-subset.mbtiles")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("fixtures/noaa_ncds21_real_chart_subset.mbtiles")
            .use { input -> source.outputStream().use(input::copyTo) }
        val repository = AndroidMbTilesRepository(context.contentResolver, File(testRoot, "packages"))

        val candidate = repository.inspect(source.toURI().toString())
        assertEquals("7da5ed14bc0b79585ec39f0afeb4fdde9d449a1f811054ea754929525530064f", candidate.sha256)
        assertEquals("NOAA Chart Display Service ncds_21", candidate.suggestedSource)
        assertEquals("Provided by NOAA Office of Coast Survey", candidate.suggestedAttribution)
        assertEquals(256, candidate.tileSize)
        val installed = repository.commit(
            ChartPackageImportRequest(
                candidate.stagedImportId,
                candidate.suggestedDisplayName,
                candidate.suggestedSource,
                candidate.suggestedLicense,
                candidate.suggestedAttribution,
                candidate.suggestedVersion,
            ),
        )
        assertEquals(candidate.sha256, installed.sha256)
        assertTrue(File(android.net.Uri.parse(installed.localUri).path!!).isFile)
        testRoot.deleteRecursively()
        Unit
    }

    private fun createRasterMbTiles(
        file: File,
        format: String = "png",
        zoom: Int = 0,
        row: Int = 0,
        scheme: String = "tms",
        tileData: ByteArray = asymmetricTile(256, Bitmap.CompressFormat.PNG),
    ) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            database.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            mapOf(
                "name" to "Fixture", "bounds" to "170,-47,179,-34", "minzoom" to "0",
                "maxzoom" to zoom.toString(), "format" to format, "scheme" to scheme,
            ).forEach { (name, value) ->
                database.execSQL("INSERT INTO metadata(name,value) VALUES(?,?)", arrayOf(name, value))
            }
            database.execSQL(
                "INSERT INTO tiles(zoom_level,tile_column,tile_row,tile_data) VALUES(?,0,?,?)",
                arrayOf(zoom, row, tileData),
            )
        }
    }

    private fun asymmetricTile(size: Int, format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { style = Paint.Style.FILL }
        paint.color = Color.rgb(210, 50, 45)
        canvas.drawRect(0f, 0f, size * 0.65f, size * 0.35f, paint)
        paint.color = Color.rgb(25, 145, 210)
        canvas.drawRect(size * 0.65f, 0f, size.toFloat(), size * 0.7f, paint)
        paint.color = Color.rgb(245, 205, 40)
        canvas.drawRect(0f, size * 0.35f, size * 0.65f, size.toFloat(), paint)
        paint.color = Color.rgb(35, 170, 100)
        canvas.drawRect(size * 0.65f, size * 0.7f, size.toFloat(), size.toFloat(), paint)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(format, 92, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
