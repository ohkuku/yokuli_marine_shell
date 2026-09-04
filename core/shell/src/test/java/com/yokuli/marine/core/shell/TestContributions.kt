package com.yokuli.marine.core.shell

import com.yokuli.marine.core.model.DestinationId
import com.yokuli.marine.core.model.LaunchTarget
import com.yokuli.marine.core.model.LauncherEntryDescriptor
import com.yokuli.marine.core.model.LauncherEntryId
import com.yokuli.marine.core.model.MarineAppDescriptor
import com.yokuli.marine.core.model.MarineAppId
import com.yokuli.marine.core.model.ShellFeatureContribution
import com.yokuli.marine.core.model.TileSize

internal fun contribution(id: String, entryId: String = id): ShellFeatureContribution {
    val appId = MarineAppId(id)
    val destination = DestinationId("$id.root")
    return object : ShellFeatureContribution {
        override val app = MarineAppDescriptor(appId, destination)
        override val launcherEntries = listOf(
            LauncherEntryDescriptor(
                LauncherEntryId(entryId),
                appId,
                LaunchTarget(appId, destination),
                TileSize.SMALL_1X1,
                listOf(TileSize.SMALL_1X1),
            ),
        )
    }
}
