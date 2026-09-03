package com.yokuli.marine.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WpText(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    color: Color = YokuliColors.White,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFamily = FontFamily.SansSerif,
            lineHeight = (size * 1.08).sp,
            textMotion = TextMotion.Animated,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun WpPageHeader(
    appName: String,
    contextLine: String? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f).wpEntrance(motionKey = "header-$appName", order = 0)) {
            WpText(
                text = appName.lowercase(),
                size = 44,
                weight = FontWeight.Light,
                maxLines = 1,
                modifier = Modifier.testTag("wp-page-title-${appName.lowercase()}"),
            )
            if (contextLine != null) {
                WpText(
                    text = contextLine.uppercase(),
                    size = 11,
                    color = YokuliColors.Cyan,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            WpText(trailing, 11, color = YokuliColors.Muted, maxLines = 1)
        }
    }
}

data class WpAppBarAction(
    val symbol: String,
    val label: String,
    val description: String = label,
    val selected: Boolean = false,
    val testTag: String? = null,
    val onClick: () -> Unit,
)

@Composable
fun WpApplicationBar(
    actions: List<WpAppBarAction>,
    modifier: Modifier = Modifier,
    onOverflow: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().height(YokuliMetrics.AppBarHeight)
            .background(YokuliColors.Black).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        actions.forEach { action ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WpCircleButton(
                    symbol = action.symbol,
                    description = action.description,
                    selected = action.selected,
                    onClick = action.onClick,
                    modifier = action.testTag?.let { Modifier.testTag(it) } ?: Modifier,
                )
                WpText(
                    text = action.label.lowercase(),
                    size = 9,
                    color = if (action.selected) YokuliColors.White else YokuliColors.Muted,
                    maxLines = 1,
                )
            }
        }
        if (onOverflow != null) {
            Spacer(Modifier.weight(1f))
            WpCircleButton("…", "More actions", onClick = onOverflow)
        }
    }
}

@Composable
fun WpCircleButton(
    symbol: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(YokuliMetrics.MinTouch)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .wpTilt(interactions, maximumDegrees = 4f)
            .clickable(
                interactionSource = interactions,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp)
                .background(if (selected) YokuliColors.Cyan else Color.Transparent, CircleShape)
                .border(2.dp, YokuliColors.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            WpText(symbol, 20, color = YokuliColors.White, maxLines = 1)
        }
    }
}
