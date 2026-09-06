package com.yokuli.marine.core.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 中文：实时数据显示节奏。它只控制“多久把最新值呈现一次”，不改变数据源真相。
 * English: Presentation cadence controls when the latest value becomes visible, never source truth.
 */
data class PresentationCadence(val intervalMillis: Long) {
    init {
        require(intervalMillis > 0L)
    }

    companion object {
        val ChartPosition = PresentationCadence(200L)
        val DataOverview = PresentationCadence(250L)
        val RawPreview = PresentationCadence(250L)
        val NmeaStatus = PresentationCadence(1_000L)
        val StartTile = PresentationCadence(1_000L)
    }
}

fun interface PresentationClock {
    fun nowMillis(): Long
}

enum class PresentationChange {
    /** A routine high-frequency value; latest wins until the next cadence boundary. */
    LIVE_FRAME,

    /** Row/page identity, selection, availability or another user-understood structural fact. */
    STRUCTURAL,

    /** Disconnect, invalidity, overload or another fact which must never wait for throttling. */
    SAFETY,
}

sealed interface PresentationDecision<out T> {
    data class Presented<T>(val value: T) : PresentationDecision<T>
    data class Deferred(val millisUntilPresentation: Long) : PresentationDecision<Nothing>
    data object Unchanged : PresentationDecision<Nothing>
}

/**
 * Pure, clock-injected latest-wins state machine used to test cadence without wall-clock sleeps.
 * It deliberately knows nothing about Compose, NMEA, maps or application lifecycles.
 */
class CadencedPresentation<T>(
    val cadence: PresentationCadence,
    private val clock: PresentationClock,
    initialValue: T,
) {
    var visibleValue: T = initialValue
        private set
    var presentationCount: Long = 1L
        private set

    private var pendingValue: T? = null
    private var hasPendingValue = false
    private var lastPresentedAtMillis = clock.nowMillis().also { require(it >= 0L) }

    fun submit(value: T, change: PresentationChange = PresentationChange.LIVE_FRAME): PresentationDecision<T> {
        val now = monotonicNow()
        if (change != PresentationChange.LIVE_FRAME || now - lastPresentedAtMillis >= cadence.intervalMillis) {
            return present(value, now)
        }
        pendingValue = value
        hasPendingValue = true
        return PresentationDecision.Deferred(
            (cadence.intervalMillis - (now - lastPresentedAtMillis)).coerceAtLeast(0L),
        )
    }

    fun flush(): PresentationDecision<T> {
        if (!hasPendingValue) return PresentationDecision.Unchanged
        val now = monotonicNow()
        val remaining = cadence.intervalMillis - (now - lastPresentedAtMillis)
        if (remaining > 0L) return PresentationDecision.Deferred(remaining)
        @Suppress("UNCHECKED_CAST")
        return present(pendingValue as T, now)
    }

    private fun present(value: T, now: Long): PresentationDecision.Presented<T> {
        visibleValue = value
        pendingValue = null
        hasPendingValue = false
        lastPresentedAtMillis = now
        presentationCount += 1L
        return PresentationDecision.Presented(value)
    }

    private fun monotonicNow(): Long = clock.nowMillis().also {
        require(it >= lastPresentedAtMillis) { "presentation clock must be monotonic" }
    }
}

/** A bounded whole-snapshot buffer: incoming raw frames never create an unbounded UI queue. */
class LatestWinsBatchBuffer<T>(
    maxItems: Int,
    cadence: PresentationCadence,
    clock: PresentationClock,
    initialItems: List<T> = emptyList(),
) {
    val capacity: Int = maxItems.also { require(it > 0) }
    private val presentation = CadencedPresentation(cadence, clock, initialItems.take(capacity))

    val visibleItems: List<T> get() = presentation.visibleValue
    val presentationCount: Long get() = presentation.presentationCount

    fun submit(
        newestFirstSnapshot: List<T>,
        change: PresentationChange = PresentationChange.LIVE_FRAME,
    ): PresentationDecision<List<T>> = presentation.submit(newestFirstSnapshot.take(capacity), change)

    fun flush(): PresentationDecision<List<T>> = presentation.flush()
}

/**
 * Keeps structural/safety changes immediate while routine live frames are latest-wins and bounded.
 * This helper never starts an entrance animation.
 */
@Composable
fun <T> rememberCadencedLiveValue(
    value: T,
    structuralKey: Any?,
    cadence: PresentationCadence,
): T {
    val latest by rememberUpdatedState(value)
    var visible by remember(structuralKey) { mutableStateOf(value) }

    LaunchedEffect(structuralKey, cadence.intervalMillis) {
        while (isActive) {
            delay(cadence.intervalMillis)
            visible = latest
        }
    }
    return visible
}

/** Stable-width, tabular-feeling numeric field for live values. No entrance animation is applied. */
@Composable
fun WpLiveField(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    structuralKey: Any? = Unit,
    cadence: PresentationCadence = PresentationCadence.DataOverview,
    size: Int = 16,
    color: Color? = null,
    minValueWidth: Dp = 112.dp,
    maxLines: Int = 1,
) {
    val visible = rememberCadencedLiveValue(value, structuralKey, cadence)
    val colors = LocalWpTheme.current
    Column(modifier) {
        if (label != null) WpText(label, 10, color = colors.muted, maxLines = 1)
        BasicText(
            text = visible,
            modifier = Modifier.widthIn(min = minValueWidth),
            style = TextStyle(
                color = color ?: colors.foreground,
                fontSize = size.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                lineHeight = (size * 1.12).sp,
            ),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Bounded, latest-wins raw console using stable monospaced geometry and no per-frame motion. */
@Composable
fun WpLiveConsole(
    newestFirstLines: List<String>,
    modifier: Modifier = Modifier,
    structuralKey: Any? = Unit,
    cadence: PresentationCadence = PresentationCadence.RawPreview,
    maxEntries: Int = 20,
    lineSize: Int = 12,
    emptyContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    require(maxEntries > 0)
    val bounded = newestFirstLines.take(maxEntries)
    val visible = rememberCadencedLiveValue(bounded, structuralKey, cadence)
    val colors = LocalWpTheme.current
    Column(modifier) {
        if (visible.isEmpty()) {
            emptyContent?.invoke(this)
        } else {
            visible.forEach { line ->
                BasicText(
                    text = line,
                    style = TextStyle(
                        color = colors.foreground,
                        fontSize = lineSize.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = (lineSize * 1.25).sp,
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
