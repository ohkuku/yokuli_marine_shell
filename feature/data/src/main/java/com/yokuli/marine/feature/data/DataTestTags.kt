package com.yokuli.marine.feature.data

object DataTestTags {
    const val ROOT = "data-root"
    const val OVERVIEW = "data-overview"
    const val INPUTS = "data-inputs"
    const val SOURCES = "data-sources"
    const val FLOW = "data-flow"
    const val DIAGNOSTICS = "data-diagnostics"
    const val RAW = "data-raw-console"
    fun section(section: DataSection) = "data-section-${section.name.lowercase()}"
    fun group(group: SourceGroup) = "data-group-${group.name.lowercase()}"
    fun candidate(group: SourceGroup, source: String) = "data-candidate-${group.name.lowercase()}-$source"
}
