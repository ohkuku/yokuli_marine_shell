package com.yokuli.marine.map.domain.chartlibrary

import kotlinx.coroutines.flow.StateFlow

data class ChartLibraryRuntimeMetrics(
    val activeReadSessions: Int = 0,
    val readSessionHighWater: Int = 0,
    val queuedBasicChecks: Int = 0,
    val rejectedBasicChecks: Long = 0L,
) {
    init {
        require(activeReadSessions >= 0 && readSessionHighWater >= activeReadSessions)
        require(queuedBasicChecks >= 0 && rejectedBasicChecks >= 0L)
    }
}

interface ChartLibraryRuntimePort :
    ChartCatalogReadPort,
    ChartSourceCommandPort,
    ChartValidationCommandPort,
    ChartResourceAccessPort {
    val metrics: StateFlow<ChartLibraryRuntimeMetrics>
}
