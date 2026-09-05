package com.yokuli.marine.feature.shell.lab

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
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
import com.yokuli.shell.contract.LauncherAppDescriptor
import com.yokuli.shell.contract.LauncherCatalogSnapshot
import com.yokuli.shell.contract.LauncherEntryDescriptor
import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.contract.PinPolicy
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.contract.MarineTileSize
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.AllAppsState
import com.yokuli.shell.engine.DefaultLauncherReducer
import com.yokuli.shell.engine.InternalTaskState
import com.yokuli.shell.engine.LauncherAction
import com.yokuli.shell.engine.LauncherEngineState
import com.yokuli.shell.engine.LauncherReducerContext
import com.yokuli.shell.engine.ShellVisualSurface
import com.yokuli.shell.engine.StartScreenState
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.TilePlacement

class ShellLabActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tileCount = intent.getIntExtra(EXTRA_TILE_COUNT, ShellLabDataset.PERFORMANCE.tileCount)
            .coerceIn(1, ShellLabDataset.PERFORMANCE.tileCount)
        val viewportDp = intent.getIntExtra(EXTRA_VIEWPORT_DP, 0).takeIf { it > 0 }
        setContent { YokuliTheme(WpThemeSpec()) { ShellLab(tileCount, viewportDp) } }
    }

    companion object {
        const val EXTRA_TILE_COUNT = "com.yokuli.marine.shell.lab.TILE_COUNT"
        const val EXTRA_VIEWPORT_DP = "com.yokuli.marine.shell.lab.VIEWPORT_DP"
    }
}

/** 中文：60 个压测项只存在于 Debug/Benchmark。 English: 60 stress entries are debug/benchmark-only. */
private enum class ShellLabDataset(val tileCount: Int) {
    DEMO(30),
    PERFORMANCE(60),
}

@Composable
private fun ShellLab(tileCount: Int, viewportDp: Int?) {
    val descriptors = remember(tileCount) { demoDescriptors(tileCount) }
    val defaultDocument = remember(descriptors) { demoDocument(descriptors) }
    val catalog = remember(descriptors) {
        LauncherCatalogSnapshot(
            revision = 1,
            apps = descriptors.map { LauncherAppDescriptor(it.appId, it.entryId) },
            entries = descriptors,
        )
    }
    val profile = WpReferenceProfiles.PHONE_PORTRAIT_4COL
    val reducer = remember { DefaultLauncherReducer() }
    val context = remember(defaultDocument, profile) { LauncherReducerContext(defaultDocument, profile) }
    var engineState by remember(defaultDocument, catalog) {
        mutableStateOf(
            LauncherEngineState(
                surface = ShellVisualSurface.Desktop,
                start = StartScreenState(defaultDocument),
                allApps = AllAppsState(catalog.revision),
                tasks = InternalTaskState(),
                catalog = catalog,
            ),
        )
    }
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
    val shellModifier = if (viewportDp == null) {
        Modifier.fillMaxSize()
    } else {
        Modifier.requiredSize(viewportDp.dp).clip(RoundedCornerShape(28.dp))
            .testTag("shell-lab-rounded-viewport-$viewportDp")
    }
    Box(
        Modifier.fillMaxSize().background(colors.background)
            .semantics { testTagsAsResourceId = true },
        contentAlignment = Alignment.Center,
    ) {
        Column(shellModifier.background(colors.background)) {
            WpPageHeader("shell-lab", stringResource(R.string.lab_title), stringResource(R.string.lab_context))
            Box(Modifier.weight(1f)) {
                YokuliStartScreen(
                    state = LauncherUiState(
                        document = engineState.start.document,
                        entries = entries,
                        interaction = engineState.start.interaction,
                        transient = engineState.transient,
                        reveal = engineState.start.reveal,
                    ),
                    onAction = { action ->
                        action.toLabEngineAction()?.let { engineAction ->
                            engineState = reducer.reduce(engineState, engineAction, context).state
                        }
                    },
                )
                val firstSize = engineState.start.document.placements
                    .firstOrNull { it.entryId.value == "demo-1" }?.size
                if (firstSize != null) {
                    Box(
                        Modifier.size(1.dp)
                            .testTag("shell-lab-demo-1-size-${firstSize.name.lowercase()}"),
                    )
                }
            }
        }
    }
}

private fun LauncherUiAction.toLabEngineAction(): LauncherAction? = when (this) {
    is LauncherUiAction.ProposeLayout -> LauncherAction.ApplyLayoutProposal(proposal)
    is LauncherUiAction.EnterStartEdit -> LauncherAction.EnterStartEdit(tileId)
    is LauncherUiAction.SelectStartTile -> LauncherAction.SelectStartTile(tileId)
    LauncherUiAction.ExitStartEdit -> LauncherAction.ExitStartEdit
    is LauncherUiAction.BeginTileDrag -> LauncherAction.BeginTileDrag(tileId, pointerId, grabOffset)
    is LauncherUiAction.InsertionTargetChanged -> LauncherAction.InsertionTargetChanged(tileId, insertionIndex)
    is LauncherUiAction.DropTile -> LauncherAction.DropTile(tileId)
    LauncherUiAction.CancelTileOperation -> LauncherAction.CancelTileOperation
    is LauncherUiAction.ResizeTile -> LauncherAction.ResizeTile(tileId)
    LauncherUiAction.CommitTileResize -> LauncherAction.CommitTileResize
    is LauncherUiAction.MoveTileBy -> LauncherAction.MoveTileBy(tileId, columns, rows)
    is LauncherUiAction.UnpinTile -> LauncherAction.UnpinTile(tileId)
    is LauncherUiAction.AcknowledgeStartReveal -> LauncherAction.AcknowledgeStartReveal(tileId)
    LauncherUiAction.UndoLayout -> LauncherAction.UndoLayout
    else -> null
}

internal fun demoDescriptors(count: Int) = List(count) { index ->
    val id = LauncherEntryId("demo-${index + 1}")
    val appId = LauncherAppId("demo-${index + 1}")
    LauncherEntryDescriptor(
        entryId = id,
        appId = appId,
        launchToken = LaunchToken("demo.${index + 1}"),
        defaultSize = benchmarkTileSizes[index % benchmarkTileSizes.size],
        supportedSizes = MarineTileSize.entries,
        pinPolicy = PinPolicy.PINNABLE,
    )
}

private val benchmarkTileSizes = listOf(
    MarineTileSize.STANDARD_2X2,
    MarineTileSize.WIDE_4X2,
    MarineTileSize.ICON_1X1,
    MarineTileSize.COMPACT_2X1,
    MarineTileSize.TALL_2X4,
    MarineTileSize.LARGE_4X4,
)

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
            rank = (row * 4L + column) * 1024L,
        )
        if (size == MarineTileSize.WIDE_4X2) row += 2 else if (column == 3) row += 1
        placement
    }
    return StartDocument(
        schemaVersion = 2,
        profileId = WpReferenceProfiles.PHONE_PORTRAIT_4COL.id,
        defaultLayoutVersion = 2,
        placements = placements,
    )
}
