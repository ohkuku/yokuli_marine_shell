package com.yokuli.marine.map.offline

import android.content.Context
import android.view.MotionEvent
import android.view.View
import org.maplibre.android.maps.MapView

/** 图钉先命中，整段手势归编辑器。 / Hit handles before SDK children receive a gesture. */
internal class PointGestureMapView(context: Context) : MapView(context) {
    var pointTouchListener: View.OnTouchListener? = null
    private var ownsGesture = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) ownsGesture = false
        val handled = pointTouchListener?.onTouch(this, event) == true
        ownsGesture = ownsGesture || handled
        val consumed = ownsGesture
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            ownsGesture = false
        }
        return consumed || super.dispatchTouchEvent(event)
    }
}
