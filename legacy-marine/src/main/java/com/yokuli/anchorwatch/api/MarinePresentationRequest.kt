package com.yokuli.anchorwatch.api

import com.yokuli.runtime.contract.time.MarineTime

/** 只允许这些前台交互，不让后台传入任意 Intent 或 Android 类名。 */
enum class MarinePresentationAction { SHARE_FILE, OPEN_MAP, SOUND_SETTINGS, DO_NOT_DISTURB_SETTINGS }
data class MarinePresentationRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val action: MarinePresentationAction,
    val uri: String? = null,
    val mime: String? = null,
    val title: String? = null,
    val text: String? = null,
    val createdAtUtc: Long = MarineTime.nowUtcMillis(),
)
