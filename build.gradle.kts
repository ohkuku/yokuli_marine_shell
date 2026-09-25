plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// 所有宿主和运行时库共享一次构建身份。版本覆盖仍沿用现有 CI 环境变量。
fun buildSetting(name: String) = providers.environmentVariable(name)
    .orElse(providers.gradleProperty(name)).orNull?.trim().orEmpty()
fun gitBuildValue(vararg arguments: String): String? = runCatching {
    val result = providers.exec {
        workingDir(rootDir)
        commandLine(listOf("git") + arguments)
        isIgnoreExitValue = true
    }
    if (result.result.get().exitValue == 0) result.standardOutput.asText.get().trim() else null
}.getOrNull()

val identityVersionCode = buildSetting("YOKULI_VERSION_CODE").ifBlank { "24" }.toInt()
require(identityVersionCode in 1..2_100_000_000) { "YOKULI_VERSION_CODE must be a valid Android version code" }
val identityGitStatus = gitBuildValue("status", "--porcelain", "--untracked-files=normal")
val identityTimestamp = buildSetting("BUILD_TIMESTAMP_UTC").takeIf(String::isNotBlank)
    ?.let(java.time.Instant::parse)
    ?: buildSetting("SOURCE_DATE_EPOCH").takeIf(String::isNotBlank)?.toLong()?.let(java.time.Instant::ofEpochSecond)
    ?: java.time.Instant.now()
val identityChannel = buildSetting("YOKULI_BUILD_CHANNEL").ifBlank { buildSetting("BUILD_CHANNEL") }
extra["yokuliBuildIdentity"] = mapOf(
    "versionName" to buildSetting("YOKULI_VERSION_NAME").ifBlank { "0.5.0-experience.20" },
    "versionCode" to identityVersionCode.toString(),
    "gitSha" to buildSetting("YOKULI_GIT_SHA").ifBlank {
        gitBuildValue("rev-parse", "HEAD") ?: buildSetting("GITHUB_SHA").ifBlank { "unknown" }
    },
    "gitBranch" to buildSetting("YOKULI_GIT_BRANCH").ifBlank {
        gitBuildValue("symbolic-ref", "--quiet", "--short", "HEAD")
            ?: buildSetting("GITHUB_REF_NAME").ifBlank { "detached" }
    },
    // Git 不可读时不能声称源码干净；gitState 同时保留 unknown 原因。
    "gitDirty" to (identityGitStatus?.isNotEmpty() ?: true).toString(),
    "gitState" to when (identityGitStatus) { null -> "unknown"; "" -> "clean"; else -> "dirty" },
    "timestampUtc" to identityTimestamp.toString(),
    "inCi" to (buildSetting("GITHUB_ACTIONS") == "true").toString(),
    "debugChannel" to identityChannel.ifBlank { "debug" },
    "releaseChannel" to identityChannel.ifBlank { "release" },
)
