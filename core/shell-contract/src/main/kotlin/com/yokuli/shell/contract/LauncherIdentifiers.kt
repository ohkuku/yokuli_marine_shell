package com.yokuli.shell.contract

@JvmInline
value class LauncherAppId(val value: String) {
    init {
        require(value.isNotBlank()) { "LauncherAppId must not be blank" }
    }
}

@JvmInline
value class LauncherEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "LauncherEntryId must not be blank" }
    }
}

@JvmInline
value class LaunchToken(val value: String) {
    init {
        require(value.isNotBlank()) { "LaunchToken must not be blank" }
    }
}

@JvmInline
value class TileInstanceId(val value: String) {
    init {
        require(value.isNotBlank()) { "TileInstanceId must not be blank" }
    }
}

enum class WpTileSize(val columns: Int, val rows: Int) {
    SMALL_1X1(1, 1),
    MEDIUM_2X2(2, 2),
    WIDE_4X2(4, 2),
}

enum class PinPolicy {
    PINNABLE,
    FIXED,
}
