package com.yokuli.marine.map.offline

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNotEquals
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
        ActivityScenario.launch(MapRendererTestActivity::class.java).use { scenario ->
            val snapshot = AtomicReference<Bitmap>()
            val failure = AtomicReference<Throwable>()
            val finished = CountDownLatch(1)
            scenario.onActivity { activity ->
                val fixture = File(activity.cacheDir, "maplibre-render-fixture.mbtiles")
                createFixture(fixture)
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
                        .target(LatLng(0.0, 0.0)).zoom(0.0).bearing(0.0).tilt(0.0).build()
                    map.setStyle(Style.Builder().fromJson(EMPTY_TEST_STYLE)) { style ->
                        try {
                            style.addSource(RasterSource("fixture", "mbtiles://${fixture.absolutePath}", 256))
                            style.addLayer(RasterLayer("fixture-layer", "fixture"))
                            val center = Feature.fromGeometry(Point.fromLngLat(0.0, 0.0), null, "overlay:center")
                            style.addSource(GeoJsonSource("overlay", FeatureCollection.fromFeature(center)))
                            style.addLayer(
                                CircleLayer("overlay-layer", "overlay").withProperties(
                                    circleColor(Color.WHITE), circleRadius(9f),
                                ),
                            )
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
            val bitmap = requireNotNull(snapshot.get())
            val topLeft = bitmap.getPixel(bitmap.width / 4, bitmap.height / 4)
            val topRight = bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height / 4)
            val bottomLeft = bitmap.getPixel(bitmap.width / 4, bitmap.height * 3 / 4)
            val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            assertNotEquals("directional tile must not collapse horizontally", topLeft, topRight)
            assertNotEquals("directional tile must not collapse vertically", topLeft, bottomLeft)
            assertTrue("stable overlay must be visibly bright", Color.red(center) > 220 && Color.green(center) > 220)
        }
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

    private companion object {
        const val EMPTY_TEST_STYLE = """{
          "version": 8,
          "sources": {},
          "layers": [{"id":"background","type":"background","paint":{"background-color":"#081b27"}}]
        }"""
    }
}
