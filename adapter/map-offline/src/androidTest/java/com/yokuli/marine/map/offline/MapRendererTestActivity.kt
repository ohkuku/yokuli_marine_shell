package com.yokuli.marine.map.offline

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

class MapRendererTestActivity : Activity() {
    lateinit var mapView: MapView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(applicationContext)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        setContentView(
            FrameLayout(this).apply {
                addView(mapView, FrameLayout.LayoutParams(512, 512))
            },
        )
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { mapView.onLowMemory(); super.onLowMemory() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
}
