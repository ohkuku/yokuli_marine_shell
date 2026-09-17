package com.yokuli.marine.feature.desktop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpText
import com.yokuli.shell.contract.ShellSafeBands
import com.yokuli.shell.contract.ShellWindowMetrics
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private data class BatteryUiState(val percent: Int = -1, val charging: Boolean = false)

/**
 * A compact display-only status owned by an installed app. Navigation belongs to visible
 * Start/All Apps affordances, never to an invisible hit target in the system strip.
 */
data class WpStatusStripItem(
    val stableId: String,
    val compactText: String,
    val expandedDescription: String,
    val attention: Boolean,
) {
    init {
        require(stableId.isNotBlank())
        require(compactText.isNotBlank())
        require(expandedDescription.isNotBlank())
    }
}

@Composable
fun WpStatusStrip(
    windowMetrics: ShellWindowMetrics,
    statusItems: List<WpStatusStripItem> = emptyList(),
) {
    val colors = LocalWpTheme.current
    val context = LocalContext.current
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var batteryPercent by remember { mutableIntStateOf(-1) }
    var chargingValue by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val waitMillis = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(waitMillis)
            nowTick = System.currentTimeMillis()
        }
    }
    DisposableEffect(context) {
        fun update(intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            chargingValue = if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) 1 else 0
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) = update(intent)
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        update(sticky)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val battery = BatteryUiState(batteryPercent, chargingValue == 1)
    val time = remember(nowTick) { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    val safe = ShellSafeBands.resolve(windowMetrics).status
    val density = windowMetrics.density.takeIf {it.isFinite() && it>0f} ?: 1f
    val safeTop = (safe.top / density).dp
    val segments = ShellSafeBands.statusSegments(windowMetrics)
    val clockSegment = segments.firstOrNull { it.width / density >= 40f } ?: segments.maxByOrNull { it.width }
    val statusSegment = segments.lastOrNull { it.width / density >= 26f } ?: segments.maxByOrNull { it.width }
    Box(Modifier.fillMaxWidth().height(30.dp + safeTop).background(colors.background).testTag("shell-status-strip")) {
        if(clockSegment!=null && clockSegment==statusSegment) {
            Row(Modifier.absoluteOffset(x=(clockSegment.left/density).dp,y=safeTop)
                .width((clockSegment.width/density).dp).height(30.dp).clipToBounds(),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                WpText(time,12,Modifier.width(if(clockSegment.width/density>=80f)44.dp else 32.dp),maxLines=1)
                StatusContents(battery,statusItems,Modifier.weight(1f))
            }
        } else {
            clockSegment?.let {segment ->
                Box(Modifier.absoluteOffset(x=(segment.left/density).dp,y=safeTop)
                    .width((segment.width/density).dp).height(30.dp).clipToBounds(),contentAlignment=Alignment.CenterStart) {
                    WpText(time,12,maxLines=1)
                }
            }
            statusSegment?.let {segment ->
                StatusContents(battery,statusItems,Modifier.absoluteOffset(x=(segment.left/density).dp,y=safeTop)
                    .width((segment.width/density).dp).height(30.dp).clipToBounds())
            }
        }
    }
}

private const val MAX_APP_STATUS_ITEMS = 3

/** 状态内容只能在分配到的区段内测量；充电文字首先让位，长应用状态单行省略。 */
@Composable
private fun StatusContents(battery:BatteryUiState,items:List<WpStatusStripItem>,modifier:Modifier=Modifier) {
    val colors=LocalWpTheme.current
    BoxWithConstraints(modifier.height(30.dp).clipToBounds()) {
        val showPercent=battery.percent>=0 && maxWidth>=if(items.isEmpty())64.dp else 112.dp
        val showCharging=battery.charging && maxWidth>=if(items.isEmpty())150.dp else 260.dp
        val fixedWidth=20f+(if(showPercent)40f else 0f)+(if(showCharging)45f else 0f)+12f
        val appWidth=(maxWidth.value-fixedWidth).coerceAtLeast(0f)
        val appCount=(appWidth/36f).toInt().coerceIn(0,MAX_APP_STATUS_ITEMS)
        // 运行中的系统会话优先于普通未读计数；挖孔旁的窄区不能把 REC 挤掉。
        val visible=items.sortedWith(compareBy<WpStatusStripItem> {
            when(it.stableId) { "voyage" -> 0; "anchor" -> 1; else -> 2 }
        }.thenByDescending {it.attention}).take(appCount)
        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            if(visible.isNotEmpty())Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
                visible.forEach {item ->
                    WpText(item.compactText,10,Modifier.weight(1f).testTag("shell-status-${item.stableId}")
                        .semantics {contentDescription=item.expandedDescription},
                        color=if(item.attention)colors.alarm else colors.muted,maxLines=1)
                }
            } else Spacer(Modifier.weight(1f))
            if(showCharging)WpText(stringResource(R.string.status_charging),10,color=colors.muted,maxLines=1)
            if(showPercent)WpText(stringResource(R.string.status_battery,battery.percent),11,maxLines=1)
            BatteryIcon(battery.percent,Modifier.size(width=20.dp,height=10.dp))
        }
    }
}

@Composable
private fun BatteryIcon(percent: Int, modifier: Modifier = Modifier) {
    val color = LocalWpTheme.current.foreground.copy(alpha = .9f)
    Canvas(modifier) {
        val bodyWidth = size.width * .84f
        val stroke = size.height * .15f
        drawRect(
            color = color,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(bodyWidth - stroke, size.height - stroke),
            style = Stroke(stroke),
        )
        drawRect(
            color = color,
            topLeft = Offset(bodyWidth, size.height * .28f),
            size = Size(size.width - bodyWidth, size.height * .44f),
        )
        if (percent >= 0) {
            val fraction = (percent.coerceIn(0, 100) / 100f)
            drawRect(
                color = color,
                topLeft = Offset(stroke * 1.2f, stroke * 1.2f),
                size = Size((bodyWidth - stroke * 2.4f) * fraction, size.height - stroke * 2.4f),
            )
        }
    }
}
