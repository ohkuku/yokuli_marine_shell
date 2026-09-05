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

    /**
     * App-owned destinations that are routable but are not separate Start/All Apps entries.
     * Entry launch tokens are registered automatically; do not repeat them here.
     * The installation binding validates uniqueness and assigns every token to this app's host.
     */
    val internalLaunchTokens: List<LaunchToken> get() = emptyList()
}
