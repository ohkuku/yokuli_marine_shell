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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.yokuli.marine.core.design.LocalWpTheme
import com.yokuli.marine.core.design.WpPageHeader
import com.yokuli.marine.core.design.WpThemeSpec
import com.yokuli.marine.core.design.YokuliTheme
import com.yokuli.marine.feature.desktop.LauncherEntryUiState
import com.yokuli.marine.feature.desktop.LauncherUiAction
import com.yokuli.marine.feature.desktop.LauncherUiState
import com.yokuli.marine.feature.desktop.MarineIconKind
import com.yokuli.marine.feature.desktop.YokuliStartScreen
import com.yokuli.shell.contract.LaunchToken
import com.yokuli.shell.contract.LauncherAppId
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.GridCell
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement

class ShellLabActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YokuliTheme(WpThemeSpec()) { ShellLab() } }
    }
}

/** 中文：60 个压测项只存在于 Debug/Benchmark。 English: 60 stress entries are debug/benchmark-only. */
private enum class ShellLabDataset(val tileCount: Int) {
    DEMO(30),
    PERFORMANCE(60),
}

@Composable
private fun ShellLab() {
    val descriptors = remember { demoDescriptors(ShellLabDataset.PERFORMANCE.tileCount) }
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
    Column(
        Modifier.fillMaxSize().background(colors.background)
            .semantics { testTagsAsResourceId = true },
    ) {
        WpPageHeader("shell-lab", stringResource(R.string.lab_title), stringResource(R.string.lab_context))
        Box(Modifier.weight(1f)) {
            YokuliStartScreen(
                state = LauncherUiState(document, entries),
                onAction = { action -> if (action is LauncherUiAction.ProposeLayout) document = action.proposal.after },
            )
        }
    }
}

internal fun demoDescriptors(count: Int) = List(count) { index ->
    val id = LauncherEntryId("demo-${index + 1}")
    val appId = LauncherAppId("demo-${index + 1}")
    LauncherEntryDescriptor(
        entryId = id,
        appId = appId,
        launchToken = LaunchToken("demo.${index + 1}"),
        defaultSize = if (index % 7 == 0) MarineTileSize.WIDE_4X2 else MarineTileSize.ICON_1X1,
        supportedSizes = MarineTileSize.entries,
        pinPolicy = PinPolicy.PINNABLE,
    )
}

internal fun demoDocument(entries: List<LauncherEntryDescriptor>): StartDocument {
    var row = 0
    val placements = entries.mapIndexed { index, entry ->
        val size = entry.defaultSize
        val column = if (size == MarineTileSize.WIDE_4X2) 0 else index % 4
        if (size == MarineTileSize.WIDE_4X2 && index > 0) row += 1
        val placement = TilePlacement(
            TileInstanceId("tile-${entry.entryId.value}"),
            entry.entryId,
            size,
            GridCell(column, row),
        )
        if (size == MarineTileSize.WIDE_4X2) row += 2 else if (column == 3) row += 1
        placement
    }
    return StartDocument(
        schemaVersion = 1,
        profileId = WpReferenceProfiles.PHONE_PORTRAIT_4COL.id,
        defaultLayoutVersion = 1,
        placements = placements,
    )
}
