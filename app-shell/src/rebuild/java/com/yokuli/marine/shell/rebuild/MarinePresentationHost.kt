package com.yokuli.marine.shell.rebuild

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.yokuli.anchorwatch.api.MarinePresentationAction
import com.yokuli.anchorwatch.api.MarinePresentationRequest

/** 仅前台 Activity 调用。白名单动作和私有 FileProvider URI 不允许后台任意发起外部 Intent。 */
internal fun Activity.presentMarineRequest(value: MarinePresentationRequest) {
    val intent = when (value.action) {
        MarinePresentationAction.SHARE_FILE -> {
            val uri = Uri.parse(requireNotNull(value.uri))
            require(uri.scheme == "content" && uri.authority == "$packageName.files") { "INVALID_EXPORT_URI" }
            val send = Intent(Intent.ACTION_SEND).setType(value.mime ?: "application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, value.text)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newUri(contentResolver, value.title ?: "Yokuli", uri)
            Intent.createChooser(send, value.title ?: "Yokuli")
        }
        MarinePresentationAction.OPEN_MAP -> {
            val uri = Uri.parse(requireNotNull(value.uri))
            require(uri.scheme == "https" && uri.host in setOf("www.google.com", "maps.google.com"))
            Intent(Intent.ACTION_VIEW, uri)
        }
        MarinePresentationAction.SOUND_SETTINGS -> Intent(Settings.ACTION_SOUND_SETTINGS)
        MarinePresentationAction.DO_NOT_DISTURB_SETTINGS -> Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
    }
    startActivity(intent)
}
