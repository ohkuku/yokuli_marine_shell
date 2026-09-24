package com.yokuli.anchorwatch.platform

import android.content.Context
import android.content.pm.PackageManager
import com.yokuli.anchorwatch.BuildConfig

/**
 * 本次安装的宿主身份，供关于、备份与诊断共用。
 * 版本与 flavor 从宿主 APK 读取，库不能把 ROM 错报为 standalone；源码身份由根构建统一生成。
 */
data class HostBuildIdentity(
    val appVersionName: String,
    val appVersionCode: Int,
    val applicationId: String,
    val flavor: String,
    val channel: String,
    val gitSha: String,
    val gitBranch: String,
    val gitDirty: Boolean,
    val gitState: String,
    val timestampUtc: String,
    val inCi: Boolean,
) {
    companion object {
        @Suppress("DEPRECATION")
        fun read(context: Context): HostBuildIdentity {
            val app = context.applicationContext
            val manager = app.packageManager
            val info = runCatching { manager.getPackageInfo(app.packageName, 0) }.getOrNull()
            val metadata = runCatching {
                manager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData
            }.getOrNull()
            return HostBuildIdentity(
                appVersionName = info?.versionName?.takeIf(String::isNotBlank) ?: BuildConfig.VERSION_NAME,
                appVersionCode = info?.longVersionCode?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
                    ?: BuildConfig.VERSION_CODE,
                applicationId = app.packageName,
                // 非 Yokuli 宿主不会被猜测成某个产品 flavor。
                flavor = metadata?.getString("com.yokuli.build.FLAVOR") ?: "unknown",
                channel = metadata?.getString("com.yokuli.build.CHANNEL") ?: BuildConfig.BUILD_CHANNEL,
                gitSha = BuildConfig.BUILD_GIT_SHA,
                gitBranch = BuildConfig.BUILD_GIT_BRANCH,
                gitDirty = BuildConfig.BUILD_GIT_DIRTY,
                gitState = BuildConfig.BUILD_GIT_STATE,
                timestampUtc = BuildConfig.BUILD_TIMESTAMP_UTC,
                inCi = BuildConfig.BUILD_IN_CI,
            )
        }
    }
}
