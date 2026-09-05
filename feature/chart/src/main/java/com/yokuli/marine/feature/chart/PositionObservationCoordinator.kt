package com.yokuli.marine.feature.chart

import com.yokuli.marine.map.domain.MapAction
import com.yokuli.marine.map.domain.MapStore
import com.yokuli.marine.map.domain.NoSourcePositionPort
import com.yokuli.marine.map.domain.ObservationMonotonicClock
import com.yokuli.marine.map.domain.PositionPortEvent
import com.yokuli.marine.map.domain.ReadOnlyPositionPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serializes a future provider's read-only observations into the existing MapStore actor.
 * Production supplies [NoSourcePositionPort], which deliberately starts neither collector nor timer.
 */
class PositionObservationCoordinator(
    port: ReadOnlyPositionPort,
    private val mapStore: MapStore,
    private val scope: CoroutineScope,
    private val clock: ObservationMonotonicClock,
    private val freshnessTickMillis: Long = DEFAULT_FRESHNESS_TICK_MILLIS,
) : AutoCloseable {
    private var ticker: Job? = null
    private val collector: Job? = if (port === NoSourcePositionPort) {
        null
    } else {
        require(freshnessTickMillis > 0L)
        scope.launch {
            port.events.collect { event ->
                val now = clock.now()
                val action = when (event) {
                    is PositionPortEvent.SourceConnected -> MapAction.PositionSourceConnected(event.source)
                    is PositionPortEvent.SourceDisconnected -> MapAction.PositionSourceDisconnected(event.source, now)
                    is PositionPortEvent.Position -> MapAction.ObservePosition(event.value, now)
                    is PositionPortEvent.Heading -> MapAction.ObserveHeading(event.value, now)
                    is PositionPortEvent.CourseSpeed -> MapAction.ObserveCourseSpeed(event.value, now)
                }
                mapStore.dispatch(action)
                ensureFreshnessTicker()
            }
        }
    }

    val isIdleNoSource: Boolean get() = collector == null && ticker == null

    private fun ensureFreshnessTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(freshnessTickMillis)
                mapStore.dispatch(MapAction.PositionClockTick(clock.now()))
            }
        }
    }

    override fun close() {
        collector?.cancel()
        ticker?.cancel()
    }

    private companion object {
        const val DEFAULT_FRESHNESS_TICK_MILLIS = 1_000L
    }
}
