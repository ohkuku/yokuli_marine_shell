package com.yokuli.marine.map.offline

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

@RunWith(AndroidJUnit4::class)
class MapLibreMbTilesRenderTest {
    @Test
    fun localMbTilesActuallyRendersDirectionalPixelsAndStableOverlayWithoutNetworkStyle() {
        val bitmap = captureFixture(
            name = "directional",
            center = LatLng(0.0, 0.0),
            zoom = 0.0,
            overlayPoint = LatLng(0.0, 0.0),
            prepare = { _, file -> createFixture(file) },
        )
        val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val fixtureColors = listOf(
            Color.rgb(210, 45, 35),
            Color.rgb(25, 135, 220),
            Color.rgb(245, 205, 35),
            Color.rgb(30, 165, 95),
        )
        val renderedFixtureColors = fixtureColors.count { expected ->
            (0 until bitmap.width step 4).sumOf { x ->
                (0 until bitmap.height step 4).count { y -> bitmap.getPixel(x, y).near(expected) }
            } > 80
        }
        assertTrue("at least three asymmetric raster regions must be visible", renderedFixtureColors >= 3)
        assertTrue("stable overlay must be visibly bright", Color.red(center) > 220 && Color.green(center) > 220)
        assertTrue("transparent fixture edge must expose the empty local style", bitmap.getPixel(3, 3).near(Color.rgb(8, 27, 39)))

        val rotated = captureFixture(
            name = "directional-rotated",
            center = LatLng(0.0, 0.0),
            zoom = 0.0,
            bearing = 90.0,
            prepare = { _, file -> createFixture(file) },
        )
        val before = bitmap.getPixel(bitmap.width / 4, bitmap.height / 4)
        val after = rotated.getPixel(rotated.width / 4, rotated.height / 4)
        assertTrue("a 90 degree bearing must rotate known raster content", !before.near(after, tolerance = 18))
        assertTrue("rotation must not lose the known raster palette", fixtureColors.count { rotated.contains(it) } >= 3)
    }

    @Test
    fun higherZoomTilesReplaceTheOverviewTileAtTheKnownLatLonTarget() {
        val bitmap = captureFixture(
            name = "multi-zoom",
            center = LatLng(30.0, -120.0),
            zoom = 1.0,
            prepare = { _, file -> createMultiZoomFixture(file) },
        )
        val detail = Color.rgb(155, 45, 210)
        assertTrue("z=1 detail tile was not selected for latitude/longitude target", bitmap.countNear(detail) > 1_000)
    }

    @Test
    fun tracedNoaaSubsetRendersRecognisableChartPaletteFromLocalMbTiles() {
        val bitmap = captureFixture(
            name = "noaa-ncds21",
            center = LatLng(51.9443, -130.957),
            zoom = 10.0,
            prepare = { _, file ->
                InstrumentationRegistry.getInstrumentation().context.assets
                    .open("fixtures/noaa_ncds21_real_chart_subset.mbtiles")
                    .use { input -> file.outputStream().use(input::copyTo) }
            },
        )
        val expectedPalette = listOf(
            Color.rgb(254, 245, 206),
            Color.rgb(180, 210, 225),
            Color.rgb(235, 40, 135),
            Color.rgb(35, 35, 35),
        )
        val represented = expectedPalette.count { expected ->
            (0 until bitmap.width step 3).sumOf { x ->
                (0 until bitmap.height step 3).count { y -> bitmap.getPixel(x, y).near(expected, tolerance = 35) }
            } > 25
        }
        assertTrue("real NOAA chart pixels were not distinguishable from the empty style", represented >= 3)
    }

    private fun captureFixture(
        name: String,
        center: LatLng,
        zoom: Double,
        bearing: Double = 0.0,
        overlayPoint: LatLng? = null,
        prepare: (MapRendererTestActivity, File) -> Unit,
    ): Bitmap = ActivityScenario.launch(MapRendererTestActivity::class.java).use { scenario ->
        val snapshot = AtomicReference<Bitmap>()
        val failure = AtomicReference<Throwable>()
        val finished = CountDownLatch(1)
        scenario.onActivity { activity ->
            val fixture = File(activity.cacheDir, "$name.mbtiles").also { it.delete() }
            prepare(activity, fixture)
            activity.mapView.getMapAsync { map ->
                val requested = AtomicBoolean(false)
                activity.mapView.addOnDidFinishRenderingMapListener(
                    object : MapView.OnDidFinishRenderingMapListener {
                        override fun onDidFinishRenderingMap(fully: Boolean) {
                            if (!fully || !requested.compareAndSet(false, true)) return
                            map.snapshot { bitmap -> snapshot.set(bitmap); finished.countDown() }
                        }
                    },
                )
                map.cameraPosition = CameraPosition.Builder()
                    .target(center).zoom(zoom).bearing(bearing).tilt(0.0).build()
                map.setStyle(Style.Builder().fromJson(EMPTY_TEST_STYLE)) { style ->
                    try {
                        style.addSource(RasterSource("fixture", "mbtiles://${fixture.absolutePath}", 256))
                        style.addLayer(RasterLayer("fixture-layer", "fixture"))
                        overlayPoint?.let { point ->
                            val feature = Feature.fromGeometry(
                                Point.fromLngLat(point.longitude, point.latitude),
                                null,
                                "overlay:center",
                            )
                            style.addSource(GeoJsonSource("overlay", FeatureCollection.fromFeature(feature)))
                            style.addLayer(
                                CircleLayer("overlay-layer", "overlay").withProperties(
                                    circleColor(Color.WHITE), circleRadius(9f),
                                ),
                            )
                        }
                        map.triggerRepaint()
                    } catch (error: Throwable) {
                        failure.set(error)
                        finished.countDown()
                    }
                }
            }
        }
        assertTrue("MapLibre did not produce a fully-rendered local snapshot", finished.await(20, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("MapLibre fixture setup failed", it) }
        requireNotNull(snapshot.get())
    }

    private fun Int.near(expected: Int, tolerance: Int = 12): Boolean =
        kotlin.math.abs(Color.red(this) - Color.red(expected)) <= tolerance &&
            kotlin.math.abs(Color.green(this) - Color.green(expected)) <= tolerance &&
            kotlin.math.abs(Color.blue(this) - Color.blue(expected)) <= tolerance

    private fun Bitmap.contains(expected: Int): Boolean = countNear(expected) > 80

    private fun Bitmap.countNear(expected: Int): Int = (0 until width step 3).sumOf { x ->
        (0 until height step 3).count { y -> getPixel(x, y).near(expected, tolerance = 24) }
    }

    private fun createFixture(file: File) {
        file.delete()
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { style = Paint.Style.FILL }
        canvas.drawColor(Color.TRANSPARENT)
        paint.color = Color.rgb(210, 45, 35)
        canvas.drawRect(8f, 8f, 165f, 90f, paint)
        paint.color = Color.rgb(25, 135, 220)
        canvas.drawRect(165f, 8f, 248f, 180f, paint)
        paint.color = Color.rgb(245, 205, 35)
        canvas.drawRect(8f, 90f, 165f, 248f, paint)
        paint.color = Color.rgb(30, 165, 95)
        canvas.drawRect(165f, 180f, 248f, 248f, paint)
        val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            database.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            mapOf(
                "name" to "Directional MapLibre fixture",
                "bounds" to "-180,-85,180,85",
                "minzoom" to "0",
                "maxzoom" to "0",
                "format" to "png",
                "scheme" to "tms",
            ).forEach { (name, value) ->
                database.execSQL("INSERT INTO metadata(name,value) VALUES(?,?)", arrayOf(name, value))
            }
            database.execSQL(
                "INSERT INTO tiles(zoom_level,tile_column,tile_row,tile_data) VALUES(0,0,0,?)",
                arrayOf(bytes),
            )
        }
    }

    private fun createMultiZoomFixture(file: File) {
        file.delete()
        val overview = solidTile(Color.rgb(30, 90, 120))
        val detail = solidTile(Color.rgb(155, 45, 210))
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            database.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
            mapOf(
                "name" to "Multi zoom MapLibre fixture",
                "bounds" to "-180,-85,180,85",
                "minzoom" to "0",
                "maxzoom" to "1",
                "format" to "png",
                "scheme" to "tms",
            ).forEach { (name, value) ->
                database.execSQL("INSERT INTO metadata(name,value) VALUES(?,?)", arrayOf(name, value))
            }
            database.execSQL(
                "INSERT INTO tiles(zoom_level,tile_column,tile_row,tile_data) VALUES(0,0,0,?)",
                arrayOf(overview),
            )
            database.execSQL(
                "INSERT INTO tiles(zoom_level,tile_column,tile_row,tile_data) VALUES(1,0,1,?)",
                arrayOf(detail),
            )
        }
    }

    private fun solidTile(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private companion object {
        const val EMPTY_TEST_STYLE = """{
          "version": 8,
          "sources": {},
          "layers": [{"id":"background","type":"background","paint":{"background-color":"#081b27"}}]
        }"""
    }
}
