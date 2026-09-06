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
import com.yokuli.marine.data.android.persistence.ProtoDataStoreConnectionRepository
import com.yokuli.marine.data.android.persistence.ProtoDataStoreSourceSelectionRepository
import com.yokuli.marine.data.android.location.AndroidLocationManagerPlatform
import com.yokuli.marine.data.android.location.AndroidPhoneLocationRuntime
import com.yokuli.marine.data.android.location.SharedPreferencesPhoneLocationIntentStore
import com.yokuli.marine.data.android.runtime.AndroidElapsedRealtimeClock
import com.yokuli.marine.data.android.runtime.AndroidMarineSourceRuntime
import com.yokuli.marine.data.android.runtime.AndroidNetworkAvailability
import com.yokuli.marine.data.android.runtime.AndroidNmeaInputRuntime
import com.yokuli.marine.data.android.runtime.MarineDataRuntimeOwner
import com.yokuli.marine.data.android.runtime.ReconnectDelayPort
import com.yokuli.marine.data.android.runtime.SocketNmeaTransportFactory
import com.yokuli.marine.data.android.service.NmeaForegroundServiceController
import com.yokuli.marine.data.android.service.PhoneLocationForegroundServiceController
import com.yokuli.marine.data.phone.PhoneLocationRuntimePort
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.source.MarineSourceRuntimePort
import com.yokuli.marine.map.storage.RoomMapPersistence
import com.yokuli.marine.map.offline.AndroidMbTilesRepository
import com.yokuli.marine.map.offline.AndroidChartCoverageIndex
import com.yokuli.marine.map.domain.NoSourcePositionPort
import com.yokuli.marine.map.domain.ObservationMonotonicClock
import com.yokuli.marine.map.domain.MonotonicTime
import com.yokuli.shell.engine.LauncherPersistedState
import com.yokuli.shell.storage.ProtoDataStoreLauncherPersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ShellApplication : Application(), MarineDataRuntimeOwner {
    private val processObservationClockId = java.util.UUID.randomUUID().toString()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val launcherPersistence by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProtoDataStoreLauncherPersistence.create(
            context = this,
            scope = applicationScope,
            defaults = LauncherPersistedState(document = defaultStartDocument),
        )
    }
    val mapPersistence by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        RoomMapPersistence.create(this, applicationScope)
    }
    val chartPackageRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidMbTilesRepository(this)
    }
    val chartCoverageIndex by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidChartCoverageIndex(chartPackageRepository::acquireLease)
    }
    val positionPort = NoSourcePositionPort
    val observationClock = ObservationMonotonicClock {
        MonotonicTime(processObservationClockId, android.os.SystemClock.elapsedRealtime())
    }
    private val nmeaConnectionRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProtoDataStoreConnectionRepository.create(
            storageFile = java.io.File(filesDir, "marine-data/nmea-connections.pb"),
            scope = applicationScope,
        )
    }
    private val nmeaNetworkAvailability by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidNetworkAvailability(this)
    }
    override val nmeaInputRuntime: NmeaInputRuntimePort by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidNmeaInputRuntime(
            repository = nmeaConnectionRepository,
            transportFactory = SocketNmeaTransportFactory(),
            networkAvailability = nmeaNetworkAvailability,
            clock = AndroidElapsedRealtimeClock,
            reconnectDelay = ReconnectDelayPort.SYSTEM,
            applicationScope = applicationScope,
            foregroundController = NmeaForegroundServiceController(this),
        )
    }
    override val phoneLocationRuntime: PhoneLocationRuntimePort by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidPhoneLocationRuntime(
            platform = AndroidLocationManagerPlatform(this),
            clock = AndroidElapsedRealtimeClock,
            applicationScope = applicationScope,
            intentStore = SharedPreferencesPhoneLocationIntentStore(this),
            foregroundController = PhoneLocationForegroundServiceController(this),
        )
    }
    private val sourceSelectionRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProtoDataStoreSourceSelectionRepository.create(
            storageFile = java.io.File(filesDir, "marine-data/source-selection.pb"),
            scope = applicationScope,
        )
    }
    override val marineSourceRuntime: MarineSourceRuntimePort by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidMarineSourceRuntime(
            nmeaRuntime = nmeaInputRuntime,
            phoneRuntime = phoneLocationRuntime,
            repository = sourceSelectionRepository,
            clock = AndroidElapsedRealtimeClock,
            applicationScope = applicationScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Process ownership is independent of any Feature/ViewModel subscription. This restores
        // durable user intent without resurrecting live observations from disk.
        nmeaInputRuntime
        phoneLocationRuntime
        marineSourceRuntime
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
