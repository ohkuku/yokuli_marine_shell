package com.yokuli.marine.feature.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpText

@Composable
fun LauncherRecoverySurface(
    restoring: Boolean,
    onOpenChart: () -> Unit,
    onOpenSettings: () -> Unit,
    onResetStart: () -> Unit,
) {
    val colors = LocalWpTheme.current
    Column(Modifier.fillMaxSize().testTag(if (restoring) "launcher-restoring" else "launcher-recovery")) {
        WpPageHeader(
            appKey = "recovery",
            appName = stringResource(if (restoring) R.string.restoring_title else R.string.recovery_title),
            contextLine = stringResource(R.string.recovery_context),
        )
        WpText(
            stringResource(if (restoring) R.string.restoring_explanation else R.string.recovery_explanation),
            15,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
        if (!restoring) {
            RecoveryCommand(stringResource(R.string.launcher_chart), "recovery-open-chart", onOpenChart)
            RecoveryCommand(stringResource(R.string.launcher_settings), "recovery-open-settings", onOpenSettings)
            Spacer(Modifier.height(8.dp))
            RecoveryCommand(stringResource(R.string.recovery_reset), "recovery-reset-start", onResetStart)
        }
    }
}

@Composable
private fun RecoveryCommand(label: String, tag: String, onClick: () -> Unit) {
    val colors = LocalWpTheme.current
    val interactions = remember { MutableInteractionSource() }
    WpText(
        label,
        22,
        weight = FontWeight.Light,
        color = colors.accent,
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp, vertical = 12.dp)
            .testTag(tag)
            .semantics { role = Role.Button }
            .clickable(interactionSource = interactions, indication = null, onClick = onClick),
    )
}
