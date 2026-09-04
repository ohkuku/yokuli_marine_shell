package com.yokuli.marine.feature.shell.lab

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.core.model.DestinationId
import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.LauncherEntryDescriptor
import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.model.MarineAppId
import com.yokuli.marine.core.model.TileId
import com.yokuli.marine.core.model.TileSize
import com.yokuli.marine.core.shell.engine.layout.DesktopDocument
import com.yokuli.marine.core.shell.engine.layout.GridCell
import com.yokuli.marine.core.shell.engine.layout.TilePlacement
import com.yokuli.marine.feature.desktop.LauncherEntryUiState
import com.yokuli.marine.feature.desktop.LauncherUiAction
import com.yokuli.marine.feature.desktop.LauncherUiState
import com.yokuli.marine.feature.desktop.MarineIconKind
import com.yokuli.marine.feature.desktop.YokuliStartScreen

class ShellLabActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YokuliTheme(WpThemeSpec()) { ShellLab() } }
    }
}

/** 中文：30 个 DEMO 项只存在于调试构建。 English: All 30 DEMO entries exist only in debug builds. */
@Composable
private fun ShellLab() {
    val descriptors = remember { demoDescriptors(30) }
    var document by remember { mutableStateOf(demoDocument(descriptors)) }
    val colors = LocalWpTheme.current
    val titlePattern = stringResource(R.string.lab_entry_title)
    val entries = descriptors.mapIndexed { index, descriptor ->
        LauncherEntryUiState(
            descriptor = descriptor,
            title = titlePattern.format(index + 1),
            chineseIndex = 'D',
            icon = MarineIconKind.GENERIC,
            headline = stringResource(R.string.lab_demo_label),
            detail = stringResource(if (index % 2 == 0) R.string.lab_short_detail else R.string.lab_long_detail),
        )
    }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        WpPageHeader("shell-lab", stringResource(R.string.lab_title), stringResource(R.string.lab_context))
        Box(Modifier.weight(1f)) {
            YokuliStartScreen(
                state = LauncherUiState(document, entries),
                onAction = { action -> if (action is LauncherUiAction.ChangeDocument) document = action.document },
            )
        }
    }
}

private fun demoDescriptors(count: Int) = List(count) { index ->
    val id = LauncherEntryId("demo-${index + 1}")
    val appId = MarineAppId("demo-${index + 1}")
    LauncherEntryDescriptor(
        id = id,
        appId = appId,
        launchTarget = LaunchTarget(appId, DestinationId("demo.${index + 1}")),
        defaultSize = if (index % 7 == 0) TileSize.WIDE_4X2 else TileSize.SMALL_1X1,
        supportedSizesInCycleOrder = TileSize.entries,
    )
}

private fun demoDocument(entries: List<LauncherEntryDescriptor>): DesktopDocument {
    var row = 0
    val placements = entries.mapIndexed { index, entry ->
        val size = entry.defaultSize
        val column = if (size == TileSize.WIDE_4X2) 0 else index % 4
        if (size == TileSize.WIDE_4X2 && index > 0) row += 1
        val placement = TilePlacement(TileId("tile-${entry.id.value}"), entry.id, size, GridCell(column, row))
        if (size == TileSize.WIDE_4X2) row += 2 else if (column == 3) row += 1
        placement
    }
    return DesktopDocument(version = 1, columns = 4, placements = placements)
}
