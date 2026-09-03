package com.yokuli.marine.feature.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.*

@Composable
fun WpStatusStrip(onOpenSystem: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(YokuliMetrics.StatusHeight).background(YokuliColors.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpenSystem)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        WpText("23:21", 12)
        Spacer(Modifier.weight(1f))
        WpText("GPS", 10, color = YokuliColors.White)
        Box(Modifier.size(5.dp).background(YokuliColors.Safe, androidx.compose.foundation.shape.CircleShape))
        WpText("NMEA", 10, color = YokuliColors.Muted)
        Box(Modifier.size(5.dp).background(YokuliColors.Stale, androidx.compose.foundation.shape.CircleShape))
        WpText("72%", 11)
    }
}
