package com.yokuli.marine.feature.data

object DataTestTags {
    const val ROOT = "data-root"
    const val OVERVIEW = "data-overview"
    const val INPUTS = "data-inputs"
    const val SOURCES = "data-sources"
    const val FLOW = "data-flow"
    const val DIAGNOSTICS = "data-diagnostics"
    const val RAW = "data-raw-console"
    const val BOAT = "data-boat"
    const val VESSEL_SCENE = "data-vessel-scene"
    const val CONNECTIONS = "data-connections"
    const val ADD_SOURCE = "data-add-source"
    const val CONNECTION_WIZARD = "data-connection-wizard"
    const val FLOW_SCENE = "data-flow-scene"
    fun primary(area: PrimaryDataArea) = "data-primary-${area.name.lowercase()}"
    fun sensor(sensor: BoatSensor) = "data-sensor-${sensor.name.lowercase()}"
    fun connection(id: String) = "data-connection-$id"
    fun section(section: DataSection) = "data-section-${section.name.lowercase()}"
    fun group(group: SourceGroup) = "data-group-${group.name.lowercase()}"
    fun candidate(group: SourceGroup, source: String) = "data-candidate-${group.name.lowercase()}-$source"
}
