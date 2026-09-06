package com.yokuli.marine.feature.datasources

object DataSourcesTestTags {
    const val ROOT = "data-sources-root"
    const val EMPTY = "data-sources-empty"
    const val DATA_VIEW = "data-sources-view-data"
    const val SENTENCE_VIEW = "data-sources-view-sentences"
    const val SEARCH = "data-sources-search"
    const val ENABLE_PHONE = "data-sources-enable-phone"
    const val DISABLE_PHONE = "data-sources-disable-phone"
    const val DETAIL = "data-sources-detail"
    fun data(key: String) = "data-sources-data-$key"
    fun sentence(id: String) = "data-sources-sentence-$id"
    fun candidate(id: String) = "data-sources-candidate-$id"
}
