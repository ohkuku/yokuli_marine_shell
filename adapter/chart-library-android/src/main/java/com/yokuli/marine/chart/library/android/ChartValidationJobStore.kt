package com.yokuli.marine.chart.library.android

import android.content.Context
import com.yokuli.marine.map.domain.chartlibrary.*

interface ChartValidationJobStore {
    fun running(assetId: ChartAssetId, kind: ChartValidationJobKind)
    fun finished(assetId: ChartAssetId)
    fun restoreInterrupted(): Map<ChartAssetId, ChartValidationJob>
}

class SharedPreferencesChartValidationJobStore(context: Context) : ChartValidationJobStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun running(assetId: ChartAssetId, kind: ChartValidationJobKind) {
        preferences.edit().putString(assetId.value, kind.name).commit()
    }

    override fun finished(assetId: ChartAssetId) {
        preferences.edit().remove(assetId.value).commit()
    }

    override fun restoreInterrupted(): Map<ChartAssetId, ChartValidationJob> = buildMap {
        preferences.all.entries.take(MAX_RESTORED_JOBS).forEach { (id, encoded) ->
            val assetId = runCatching { ChartAssetId(id) }.getOrNull() ?: return@forEach
            val kind = runCatching { ChartValidationJobKind.valueOf(encoded as? String ?: return@forEach) }.getOrNull()
                ?: return@forEach
            put(assetId, ChartValidationJob(assetId, kind, ChartValidationJobStatus.INTERRUPTED))
        }
        preferences.edit().clear().commit()
    }

    companion object {
        private const val PREFERENCES = "chart_library_running_jobs"
        private const val MAX_RESTORED_JOBS = 256
    }
}
