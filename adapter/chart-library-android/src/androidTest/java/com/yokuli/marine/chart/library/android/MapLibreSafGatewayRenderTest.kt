package com.yokuli.marine.chart.library.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.DocumentsContract
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.ChartAssetId
import com.yokuli.marine.map.domain.chartlibrary.ChartContentRevision
import com.yokuli.marine.map.domain.chartlibrary.ChartOpenResult
import com.yokuli.marine.map.domain.chartlibrary.ChartOpaqueLocator
import com.yokuli.marine.map.domain.chartlibrary.ChartReadRequest
import com.yokuli.marine.map.offline.ChartLoopbackTileGateway
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer

@RunWith(AndroidJUnit4::class)
class MapLibreSafGatewayRenderTest {
    @Test fun safOriginalRendersThroughLoopbackWithoutCopyOrMutation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val documents = File(context.filesDir, "chart-library-test-documents").apply { mkdirs() }
        val source = File(documents, "render.mbtiles").also(File::delete)
        val expectedColor = Color.rgb(38, 146, 214)
        createMbTiles(source, pngTile(expectedColor))
        val hashBefore = source.sha256()
        val request = ChartReadRequest(
            ChartAssetId("asset-render"),
            ChartOpaqueLocator(
                DocumentsContract.buildDocumentUri(
                    "${context.packageName}.chartlibrary.documents",
                    "render.mbtiles",
                ).toString(),
            ),
            ChartContentRevision("render.mbtiles", source.length(), source.lastModified()),
            sourceGeneration = 1,
        )
        val opened = AndroidChartResourceAccess(context.contentResolver).open(request) as ChartOpenResult.Opened
        ChartLoopbackTileGateway().use { gateway ->
            gateway.register(opened.session, MapTileScheme.MBTILES_TMS, 256, 0, 0).use { registration ->
                val finished = CountDownLatch(1)
                val snapshot = AtomicReference<Bitmap?>()
                ActivityScenario.launch(ChartLibraryMapTestActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.mapView.getMapAsync { map ->
                            activity.mapView.addOnDidFinishRenderingMapListener(
                                object : MapView.OnDidFinishRenderingMapListener {
                                    override fun onDidFinishRenderingMap(fully: Boolean) {
                                        if (!fully || snapshot.get() != null) return
                                        map.snapshot { bitmap -> snapshot.set(bitmap); finished.countDown() }
                                    }
                                },
                            )
                            map.cameraPosition = CameraPosition.Builder().target(LatLng(0.0, 0.0)).zoom(0.0).build()
                            map.setStyle(Style.Builder().fromJson(EMPTY_STYLE)) { style ->
                                style.addSource(registration.toRasterSource("saf-chart"))
                                style.addLayer(RasterLayer("saf-chart-layer", "saf-chart"))
                                map.triggerRepaint()
                            }
                        }
                    }
                    assertTrue("MapLibre did not render the SAF-backed tile", finished.await(20, TimeUnit.SECONDS))
                    assertTrue(requireNotNull(snapshot.get()).contains(expectedColor))
                }
            }
            assertTrue(gateway.requestCount.get() > 0L)
            assertTrue(gateway.localAddress.isLoopbackAddress)
        }
        assertEquals(hashBefore, source.sha256())
        assertTrue(context.filesDir.walkTopDown().none { it.isFile && it.name.endsWith(".mbtiles") && it != source })
    }

    private fun createMbTiles(file: File, tile: ByteArray) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB, UNIQUE(zoom_level,tile_column,tile_row))")
            db.execSQL("INSERT INTO metadata(name,value) VALUES ('name','render'),('format','png'),('minzoom','0'),('maxzoom','0'),('bounds','-180,-85,180,85')")
            db.execSQL("INSERT INTO tiles VALUES (0,0,0,?)", arrayOf(tile))
        }
    }

    private fun pngTile(color: Int): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            .compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }

    private fun Bitmap.contains(expected: Int): Boolean = (0 until width step 4).any { x ->
        (0 until height step 4).any { y -> getPixel(x, y).near(expected) }
    }

    private fun Int.near(expected: Int): Boolean =
        kotlin.math.abs(Color.red(this) - Color.red(expected)) <= 20 &&
            kotlin.math.abs(Color.green(this) - Color.green(expected)) <= 20 &&
            kotlin.math.abs(Color.blue(this) - Color.blue(expected)) <= 20

    private fun File.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(readBytes()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val EMPTY_STYLE = """{"version":8,"sources":{},"layers":[]}"""
    }
}
