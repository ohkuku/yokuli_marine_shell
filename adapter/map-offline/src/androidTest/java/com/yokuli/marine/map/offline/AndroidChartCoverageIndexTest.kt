package com.yokuli.marine.map.offline

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokuli.marine.map.domain.ChartPackage
import com.yokuli.marine.map.domain.ChartPackageId
import com.yokuli.marine.map.domain.GeoBounds
import com.yokuli.marine.map.domain.SlippyTileKey
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidChartCoverageIndexTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun exactXyzKeysComeFromTmsRowsAndARealHoleStaysMissing() = runBlocking {
        val file = temporary.newFile("coverage.mbtiles")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL(
                "CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)",
            )
            database.execSQL("INSERT INTO tiles VALUES (3, 3, 3, X'01')") // XYZ y=4
            database.execSQL("INSERT INTO tiles VALUES (3, 4, 4, X'01')") // XYZ y=3
            database.execSQL("INSERT INTO tiles VALUES (2, 2, 1, X'01')") // lower zoom must not help
        }
        val required = setOf(
            SlippyTileKey(3, 3, 4),
            SlippyTileKey(3, 4, 3),
            SlippyTileKey(3, 4, 4),
        )

        val available = AndroidChartCoverageIndex().availableKeys(chart(file), required)

        assertEquals(setOf(SlippyTileKey(3, 3, 4), SlippyTileKey(3, 4, 3)), available)
        assertFalse(SlippyTileKey(3, 4, 4) in available)
    }

    @Test
    fun tracedNoaaSubsetUsesItsRealTileRowsAndDoesNotTurnBoundsIntoAvailability() = runBlocking {
        val file = temporary.newFile("noaa-subset.mbtiles")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("fixtures/noaa_ncds21_real_chart_subset.mbtiles")
            .use { input -> file.outputStream().use(input::copyTo) }
        val expected = setOf(
            SlippyTileKey(5, 4, 10),
            SlippyTileKey(7, 17, 41),
            SlippyTileKey(10, 139, 338),
            SlippyTileKey(12, 557, 1354),
        )
        val holeInsideDeclaredBounds = SlippyTileKey(12, 558, 1354)

        val available = AndroidChartCoverageIndex().availableKeys(
            chart(file).copy(
                displayName = "NOAA NCDS traced subset",
                source = "NOAA Chart Display Service ncds_21",
                license = "U.S. public domain / CC0-1.0 for NOAA data",
                attribution = "Provided by NOAA Office of Coast Survey",
                sha256 = "7da5ed14bc0b79585ec39f0afeb4fdde9d449a1f811054ea754929525530064f",
                minZoom = 5,
                maxZoom = 12,
            ),
            expected + holeInsideDeclaredBounds,
        )

        assertEquals(expected, available)
        assertFalse(holeInsideDeclaredBounds in available)
    }

    private fun chart(file: File) = ChartPackage(
        id = ChartPackageId("a".repeat(64)),
        displayName = "fixture",
        source = "fixture",
        license = "fixture",
        attribution = "fixture",
        sha256 = "a".repeat(64),
        localUri = "mbtiles://${file.absolutePath}",
        coverage = GeoBounds(-85.0, -180.0, 85.0, 180.0),
        minZoom = 0,
        maxZoom = 14,
        version = "1",
    )
}
