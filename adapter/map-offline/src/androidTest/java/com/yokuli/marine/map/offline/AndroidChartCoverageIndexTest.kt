package com.yokuli.marine.map.offline

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
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
