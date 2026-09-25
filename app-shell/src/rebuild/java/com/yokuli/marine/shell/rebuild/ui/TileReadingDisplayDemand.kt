package com.yokuli.marine.shell.rebuild.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yokuli.marine.shell.rebuild.OsStore

/** Visible content borrows existing reference-counted display leases, without changing
 * sensor preferences, selected sources, navigation or alarm state.
 */
@Composable internal fun tileReadingDisplayDemand(os:OsStore,id:String,active:Boolean) {
    val display=os.marine?.services?.display
    val needsSensors=active&&id in setOf("PRESSURE","PRESSURE_TREND_1H","PRESSURE_TREND_3H","PRESSURE_TREND_6H",
        "RATE_OF_TURN","HEEL","PITCH","ROLL_RATE","PITCH_RATE","ROLL_PERIOD","MOTION_SCORE","IMPACT_COUNT")
    val needsHeading=active&&id in setOf("HEADING_TRUE","TRUE_WIND_SPEED","TRUE_WIND_DIRECTION","TRUE_WIND_ANGLE",
        "APPARENT_WIND_SPEED","APPARENT_WIND_ANGLE","CURRENT_SET","CURRENT_DRIFT","VMG")
    TileDisplayLease(display,needsSensors) {
        display?.acquireInstruments()?.let {lease -> {lease.close();Unit}}
    }
    TileDisplayLease(display,needsHeading) {
        display?.acquireMapHeading()?.let {lease -> {lease.close();Unit}}
    }
}

/** Release synchronously on PAUSE: do not wait for a background Compose frame. */
@Composable private fun TileDisplayLease(owner:Any?,requested:Boolean,acquire:()->(() -> Unit)?) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val latestAcquire=rememberUpdatedState(acquire)
    DisposableEffect(owner,requested,lifecycle) {
        var release:(() -> Unit)?=null
        fun close() {val previous=release;release=null;previous?.invoke()}
        fun reconcile() {
            if(requested&&lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if(release==null)release=latestAcquire.value.invoke()
            } else close()
        }
        val observer=LifecycleEventObserver {_,_->reconcile()}
        lifecycle.addObserver(observer)
        reconcile()
        onDispose {lifecycle.removeObserver(observer);close()}
    }
}
