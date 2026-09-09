package com.yokuli.anchorwatch.di

import com.yokuli.anchorwatch.data.diagnostics.IncidentLogger
import com.yokuli.anchorwatch.data.NavigationRepository
import com.yokuli.anchorwatch.data.vessel.VesselDataHub
import com.yokuli.anchorwatch.location.SystemLocationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MarineIntegrationEntryPoint {
    fun incidentLogger(): IncidentLogger
    fun navigation(): NavigationRepository
    fun vesselData(): VesselDataHub
    fun systemLocation(): SystemLocationRepository
}
