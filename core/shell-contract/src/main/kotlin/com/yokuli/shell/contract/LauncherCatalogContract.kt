package com.yokuli.shell.contract

data class LauncherCatalogSnapshot(
    val revision: Long,
    val apps: List<LauncherAppDescriptor>,
    val entries: List<LauncherEntryDescriptor>,
)

data class LauncherAppDescriptor(
    val appId: LauncherAppId,
    val rootEntryId: LauncherEntryId,
)

data class LauncherEntryDescriptor(
    val entryId: LauncherEntryId,
    val appId: LauncherAppId,
    val launchToken: LaunchToken,
    val defaultSize: MarineTileSize,
    val supportedSizes: List<MarineTileSize>,
    val pinPolicy: PinPolicy,
    val presentationKind: TilePresentationKind = TilePresentationKind.STATIC,
)

interface LauncherCatalogContribution {
    val app: LauncherAppDescriptor
    val entries: List<LauncherEntryDescriptor>
}
