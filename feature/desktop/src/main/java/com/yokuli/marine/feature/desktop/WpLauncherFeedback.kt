package com.yokuli.marine.feature.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpText
import com.yokuli.marine.core.design.wpTilt
import com.yokuli.shell.engine.LauncherNotice
import com.yokuli.shell.engine.LauncherTransient
import com.yokuli.shell.engine.layout.LayoutChangeReason

/**
 * Transient feedback remains action-driven. No timeout is claimed because Stage 2.5 did not
 * observe pin/unpin timing; the user dismisses it, invokes Undo, or replaces it with another action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WpLauncherFeedback(
    transient: LauncherTransient?,
    onAction: (LauncherUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWpTheme.current
    when (transient) {
        is LauncherTransient.UndoLayout -> {
            val message = stringResource(
                if (transient.reason == LayoutChangeReason.PIN) R.string.tile_pinned else R.string.tile_unpinned,
            )
            val interactions = remember(transient.transactionId) { MutableInteractionSource() }
            Row(
                modifier.fillMaxWidth().background(colors.accent).padding(start = 18.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("launcher-undo"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WpText(message, 17, color = colors.onAccent)
                WpText(
                    stringResource(R.string.undo),
                    17,
                    color = colors.onAccent,
                    weight = FontWeight.SemiBold,
                    modifier = Modifier.padding(6.dp).testTag("launcher-undo-action")
                        .wpTilt(interactions, maximumDegrees = 3f)
                        .combinedClickable(
                            interactionSource = interactions,
                            indication = null,
                            onClick = { onAction(LauncherUiAction.UndoLayout) },
                        ).padding(12.dp),
                )
            }
        }

        is LauncherTransient.Notice -> {
            val message = stringResource(
                when (transient.notice) {
                    LauncherNotice.ALREADY_PINNED -> R.string.already_pinned
                    LauncherNotice.PIN_UNAVAILABLE -> R.string.pin_unavailable
                    LauncherNotice.LAYOUT_UNAVAILABLE -> R.string.layout_unavailable
                },
            )
            val interactions = remember(transient.notice) { MutableInteractionSource() }
            Row(
                modifier.fillMaxWidth().background(colors.accent).padding(18.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive }
                    .testTag("launcher-notice")
                    .combinedClickable(
                        interactionSource = interactions,
                        indication = null,
                        onClick = { onAction(LauncherUiAction.DismissTransient) },
                    ),
            ) { WpText(message, 17, color = colors.onAccent) }
        }

        else -> Unit
    }
}
