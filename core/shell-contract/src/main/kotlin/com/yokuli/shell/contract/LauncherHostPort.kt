package com.yokuli.shell.contract

import kotlinx.coroutines.flow.StateFlow

@JvmInline
value class UiText(val value: String)

@JvmInline
value class TileBadge(val value: String)

enum class TileSemanticState {
    DEFAULT,
    ATTENTION,
}

enum class TileAnimationPolicy {
    STATIC,
    ALLOW_TRANSITIONS,
}

data class TileContentSnapshot(
    val entryId: LauncherEntryId,
    val primary: UiText?,
    val secondary: UiText?,
    val badge: TileBadge?,
    val semanticState: TileSemanticState,
    val updatedAtElapsedMillis: Long?,
    val animationPolicy: TileAnimationPolicy,
)

data class LauncherSystemStatus(val revision: Long = 0)

sealed interface LaunchResolution {
    data class Internal(
        val appId: LauncherAppId,
        val token: LaunchToken,
    ) : LaunchResolution

    data class Unresolved(val token: LaunchToken) : LaunchResolution
}

interface LauncherHostPort {
    val catalog: StateFlow<LauncherCatalogSnapshot>
    val tileContents: StateFlow<Map<LauncherEntryId, TileContentSnapshot>>
    val systemStatus: StateFlow<LauncherSystemStatus>

    suspend fun resolveLaunch(token: LaunchToken): LaunchResolution
}
