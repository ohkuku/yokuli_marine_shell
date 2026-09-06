package com.yokuli.marine.feature.datasources

import com.yokuli.marine.data.catalog.SentenceCatalogKey
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.source.MarineFeatureLinkToken
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.data.source.SourceDecision
import com.yokuli.marine.data.source.SourceEvidence
import com.yokuli.marine.data.source.SourceKind

enum class DataSourcesViewMode { DATA, SENTENCES }

sealed interface DataSourcesFilter {
    data object All : DataSourcesFilter
    data object NeedsAttention : DataSourcesFilter
    data object MultipleSources : DataSourcesFilter
    data object Phone : DataSourcesFilter
    data class Connection(val id: ConnectionId) : DataSourcesFilter
}

sealed interface DataSourcesLocalPage {
    data object Overview : DataSourcesLocalPage
    data class DataDetail(val key: DataKey) : DataSourcesLocalPage
    data class SentenceDetail(val key: SentenceCatalogKey) : DataSourcesLocalPage
}

data class DataSourcesLocalState(
    val viewMode: DataSourcesViewMode = DataSourcesViewMode.DATA,
    val filter: DataSourcesFilter = DataSourcesFilter.All,
    val query: String = "",
    val page: DataSourcesLocalPage = DataSourcesLocalPage.Overview,
)

data class DataSourceCandidateUi(
    val source: SourceIdentity,
    val sourceName: String,
    val kind: SourceKind,
    val detail: String,
    val value: MarineValue?,
    val availability: SourceCandidateAvailability,
    val ageMillis: Long?,
    val evidence: SourceEvidence,
    val selected: Boolean,
)

data class DataSourceRowUi(
    val key: DataKey,
    val decision: SourceDecision,
    val candidates: List<DataSourceCandidateUi>,
    val resolvedValue: MarineValue?,
    val needsAttention: Boolean,
)

enum class SentenceMeaningUi { PARSED, EXPLICIT_INVALID, UNSUPPORTED }

data class RawEvidenceUi(
    val raw: String,
    val receivedAtMillis: Long,
    val current: Boolean,
)

data class SentenceRowUi(
    val key: SentenceCatalogKey,
    val sentenceId: String,
    val sourceName: String,
    val meaning: SentenceMeaningUi,
    val current: Boolean,
    val ageMillis: Long,
    val receivedCount: Long,
    val rawEvidence: List<RawEvidenceUi>,
)

enum class PhoneSourceUi {
    DISABLED,
    PERMISSION_REQUIRED,
    SYSTEM_LOCATION_DISABLED,
    STARTING,
    RECEIVING,
    INTERRUPTED,
    PLATFORM_RESTRICTED,
}

data class PhoneSourceSummaryUi(
    val state: PhoneSourceUi,
    val permission: PhoneLocationPermission,
    val enabledByUser: Boolean,
)

sealed interface DataSourcesPageUi {
    data class Overview(
        val dataRows: List<DataSourceRowUi>,
        val sentenceRows: List<SentenceRowUi>,
    ) : DataSourcesPageUi

    data class DataDetail(val row: DataSourceRowUi) : DataSourcesPageUi
    data class SentenceDetail(val row: SentenceRowUi) : DataSourcesPageUi
}

enum class DataSourcesNotice {
    SELECTION_SAVED,
    SELECTION_DISABLED,
    SELECTION_SAVE_FAILED,
    CANDIDATE_UNAVAILABLE,
    PHONE_LOCATION_ENABLED,
    PHONE_LOCATION_DISABLED,
    PHONE_LOCATION_UNAVAILABLE,
    ACTION_QUEUE_FULL,
}

data class DataSourcesUiState(
    val page: DataSourcesPageUi,
    val viewMode: DataSourcesViewMode,
    val filter: DataSourcesFilter,
    val query: String,
    val allDataRows: List<DataSourceRowUi>,
    val allSentenceRows: List<SentenceRowUi>,
    val phone: PhoneSourceSummaryUi,
    val attentionCount: Int,
    val notice: DataSourcesNotice? = null,
) {
    val dataRows: List<DataSourceRowUi> get() = allDataRows
    val sentenceRows: List<SentenceRowUi> get() = allSentenceRows
}

sealed interface DataSourcesUiAction {
    data class ChangeView(val mode: DataSourcesViewMode) : DataSourcesUiAction
    data class ChangeFilter(val filter: DataSourcesFilter) : DataSourcesUiAction
    data class ChangeQuery(val query: String) : DataSourcesUiAction
    data class OpenData(val key: DataKey) : DataSourcesUiAction
    data class OpenSentence(val key: SentenceCatalogKey) : DataSourcesUiAction
    data object BackToOverview : DataSourcesUiAction
    data class UseSource(val key: DataKey, val source: SourceIdentity) : DataSourcesUiAction
    data class DisableData(val key: DataKey) : DataSourcesUiAction
    data object EnablePhoneLocation : DataSourcesUiAction
    data object DisablePhoneLocation : DataSourcesUiAction
    data object ResolvePhoneLocation : DataSourcesUiAction
    data class PhonePermissionResult(val permanentlyDenied: Boolean) : DataSourcesUiAction
    data class OpenNmeaInput(val connectionId: ConnectionId?) : DataSourcesUiAction
    data object DismissNotice : DataSourcesUiAction
}

sealed interface DataSourcesEffect {
    data object RequestPhoneLocationPermission : DataSourcesEffect
    data object OpenSystemLocationSettings : DataSourcesEffect
    data object OpenAppPermissionSettings : DataSourcesEffect
    data class OpenNmeaInput(val token: MarineFeatureLinkToken) : DataSourcesEffect
}

object DataSourcesBackPolicy {
    fun actionFor(page: DataSourcesPageUi): DataSourcesUiAction? = when (page) {
        is DataSourcesPageUi.Overview -> null
        else -> DataSourcesUiAction.BackToOverview
    }
}
