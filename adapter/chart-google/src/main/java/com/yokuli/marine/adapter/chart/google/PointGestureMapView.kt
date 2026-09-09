package com.yokuli.marine.adapter.chart.google

import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView

/** 图钉先命中，整段手势归编辑器。 / Hit handles before SDK children receive a gesture. */
internal class PointGestureMapView(context: Context, options: GoogleMapOptions) : MapView(context, options) {
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
