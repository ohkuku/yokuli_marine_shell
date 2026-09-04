package com.yokuli.marine.shell

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.yokuli.marine.core.model.AppLanguage
import com.yokuli.shell.engine.LauncherPersistedState
import com.yokuli.shell.storage.ProtoDataStoreLauncherPersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ShellApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val launcherPersistence by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProtoDataStoreLauncherPersistence.create(
            context = this,
            scope = applicationScope,
            defaults = LauncherPersistedState(document = defaultStartDocument),
        )
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.BUILD_TYPE in setOf("benchmark", "nonMinifiedRelease")) {
            // Harnesses repeatedly force-stop/reinstall the target. A first-run LocaleManager
            // recreation would measure platform setup instead of the launcher journey.
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = getSystemService(LocaleManager::class.java)
            val frameworkTag = localeManager.applicationLocales.toLanguageTags()
            if (frameworkTag.isNotBlank()) {
                saveLanguageTag(frameworkTag)
            } else {
                // 中文：覆盖安装可能清空 framework locale；从持久化选择恢复，首次默认中文。
                // English: An update may clear the framework locale; restore the saved choice, defaulting to Chinese.
                localeManager.applicationLocales = LocaleList.forLanguageTags(selectedLanguageTag())
            }
        }
    }
}

fun AppCompatActivity.bootstrapLegacyLocale() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        val androidXTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (androidXTag.isNotBlank()) {
            saveLanguageTag(androidXTag)
        } else {
            // 中文：Android 12 及以下通过 AndroidX 恢复选择；首次仍以中文启动。
            // English: AndroidX restores the choice on Android 12 and lower; first launch remains Chinese.
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedLanguageTag()))
        }
    }
}

fun Context.persistAppLanguage(language: AppLanguage) {
    // 中文：先同步落盘再触发 Activity 重建，避免语言选择与 framework 状态分叉。
    // English: Persist synchronously before Activity recreation so the selection cannot drift from framework state.
    saveLanguageTag(language.languageTag)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(language.languageTag)
    } else {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag))
    }
}

fun Context.synchronizePersistedLanguage(languageTag: String) {
    if (selectedLanguageTag() == languageTag) return
    saveLanguageTag(languageTag)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(languageTag)
    } else {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }
}

private fun Context.selectedLanguageTag(): String =
    languagePreferences().getString(LANGUAGE_SELECTION, CHINESE_LANGUAGE_TAG) ?: CHINESE_LANGUAGE_TAG

private fun Context.saveLanguageTag(languageTag: String) {
    languagePreferences().edit().putString(LANGUAGE_SELECTION, languageTag).commit()
}

private fun Context.languagePreferences() =
    getSharedPreferences(LOCALE_PREFERENCES, Context.MODE_PRIVATE)

private const val LOCALE_PREFERENCES = "yokuli_locale"
private const val LANGUAGE_SELECTION = "selected_language_tag"
private const val CHINESE_LANGUAGE_TAG = "zh-CN"
