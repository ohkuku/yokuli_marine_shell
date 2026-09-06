package com.yokuli.marine.chart.library.android

import android.content.Context
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

interface ChartLibraryRuntimeOwner {
    val chartLibraryRuntime: ChartLibraryRuntimePort
}

class AndroidChartLibraryRuntime private constructor(
    private val catalog: RoomChartCatalogRepository,
    private val delegateAccess: ChartResourceAccessPort,
    private val sourceController: AndroidChartSourceController,
    private val revisionProbe: ChartRevisionProbe,
    parentScope: CoroutineScope,
    private val jobStore: ChartValidationJobStore?,
) : ChartLibraryRuntimePort, AutoCloseable {
    private val runtimeJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + runtimeJob)
    private val readBudget = Semaphore(MAX_OPEN_READ_SESSIONS)
    private val sessions = ConcurrentHashMap<ChartAssetId, MutableSet<BudgetedReadSession>>()
    private val basicQueue = Channel<ChartAssetId>(BASIC_QUEUE_CAPACITY)
    private val mutableMetrics = MutableStateFlow(ChartLibraryRuntimeMetrics())
    private val mutableStorage = MutableStateFlow(ChartLibraryStorageSnapshot.EMPTY)
    private val closed = AtomicBoolean(false)
    override val metrics: StateFlow<ChartLibraryRuntimeMetrics> = mutableMetrics.asStateFlow()
    override val storage: StateFlow<ChartLibraryStorageSnapshot> = mutableStorage.asStateFlow()
    private val validationController: AndroidChartValidationController by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidChartValidationController(catalog, this, revisionProbe, jobStore)
    }
    override val state: StateFlow<ChartValidationSnapshot> get() = validationController.state
    override val snapshot: StateFlow<ChartCatalogSnapshot> get() = catalog.snapshot

    init {
        validationController.state // restore interrupted process-owned jobs without a UI subscriber
        repeat(BASIC_WORKERS) {
            scope.launch {
                for (assetId in basicQueue) {
                    mutableMetrics.update { value -> value.copy(queuedBasicChecks = (value.queuedBasicChecks - 1).coerceAtLeast(0)) }
                    if (catalog.asset(assetId)?.let { asset ->
                            asset.validation == ChartAssetValidationState.DISCOVERED &&
                                asset.access in setOf(ChartAssetAccessState.UNCHECKED, ChartAssetAccessState.CHANGED)
                        } == true
                    ) validationController.inspectBasic(assetId)
                }
            }
        }
        scope.launch {
            catalog.snapshot.drop(1).collect { invalidateChangedSessions() }
        }
    }

    override suspend fun sources(offset: Int, limit: Int) = catalog.sources(offset, limit)
    override suspend fun source(id: ChartSourceId) = catalog.source(id)
    override suspend fun assets(query: ChartAssetQuery, offset: Int, limit: Int) = catalog.assets(query, offset, limit)
    override suspend fun asset(id: ChartAssetId) = catalog.asset(id)
    override suspend fun resolveLegacyAsset(legacyLogicalId: String, legacyVersionId: String?) =
        catalog.resolveLegacyAsset(legacyLogicalId, legacyVersionId)
    override suspend fun transact(transaction: ChartCatalogTransaction) = catalog.transact(transaction)

    override suspend fun acceptPicker(selection: ChartPickerSelection) = sourceController.acceptPicker(selection)
    override suspend fun repair(sourceId: ChartSourceId, selection: ChartPickerSelection) =
        sourceController.repair(sourceId, selection)

    override suspend fun refresh(sourceId: ChartSourceId): ChartSourceCommandResult {
        val result = sourceController.refresh(sourceId)
        if (result is ChartSourceCommandResult.ScanPublished && result.status in setOf(ChartScanStatus.COMPLETE, ChartScanStatus.PARTIAL)) {
            enqueueDiscovered(sourceId)
        }
        return result
    }

    override suspend fun cancel(sourceId: ChartSourceId) = sourceController.cancel(sourceId)
    override suspend fun remove(sourceId: ChartSourceId) = sourceController.remove(sourceId)
    override suspend fun inspectBasic(assetId: ChartAssetId) = validationController.inspectBasic(assetId)
    override suspend fun verifyFull(assetId: ChartAssetId) = validationController.verifyFull(assetId)
    override fun cancel(assetId: ChartAssetId) = validationController.cancel(assetId)
    override suspend fun saveManagedCopy(assetId: ChartAssetId): ChartManagedCopyCommandResult =
        ChartManagedCopyCommandResult.Rejected(ChartManagedCopyFailure.NOT_AVAILABLE)
    override fun cancelManagedCopy(assetId: ChartAssetId) = Unit

    override suspend fun open(request: ChartReadRequest): ChartOpenResult {
        if (closed.get()) return ChartOpenResult.Rejected(ChartReadFailure.SESSION_CLOSED, "Chart library runtime is closed")
        if (!requestIsCurrent(request)) return ChartOpenResult.Rejected(
            ChartReadFailure.REVISION_CHANGED, "Catalog revision or source generation changed",
        )
        readBudget.acquire()
        if (closed.get()) {
            readBudget.release()
            return ChartOpenResult.Rejected(ChartReadFailure.SESSION_CLOSED, "Chart library runtime is closed")
        }
        val opened = try {
            delegateAccess.open(request)
        } catch (error: Throwable) {
            readBudget.release()
            throw error
        }
        if (opened !is ChartOpenResult.Opened) {
            readBudget.release()
            return opened
        }
        if (!requestIsCurrent(request)) {
            opened.session.close()
            readBudget.release()
            return ChartOpenResult.Rejected(ChartReadFailure.REVISION_CHANGED, "Catalog changed while opening")
        }
        val tracked = BudgetedReadSession(opened.session) { session ->
            sessions[request.assetId]?.let { set ->
                set.remove(session)
                if (set.isEmpty()) sessions.remove(request.assetId, set)
            }
            readBudget.release()
            mutableMetrics.update { value -> value.copy(activeReadSessions = (value.activeReadSessions - 1).coerceAtLeast(0)) }
        }
        val revisionBeforeRegistration = catalog.snapshot.value.revision
        sessions.computeIfAbsent(request.assetId) { ConcurrentHashMap.newKeySet() }.add(tracked)
        mutableMetrics.update { value ->
            val active = value.activeReadSessions + 1
            value.copy(activeReadSessions = active, readSessionHighWater = maxOf(value.readSessionHighWater, active))
        }
        if (catalog.snapshot.value.revision != revisionBeforeRegistration && !requestIsCurrent(request)) {
            tracked.close()
            return ChartOpenResult.Rejected(ChartReadFailure.REVISION_CHANGED, "Catalog changed while registering session")
        }
        return ChartOpenResult.Opened(tracked)
    }

    private suspend fun requestIsCurrent(request: ChartReadRequest): Boolean {
        val current = catalog.asset(request.assetId) ?: return false
        if (current.revision.cacheKey != request.revision.cacheKey || current.access.invalidFor(request.purpose)) return false
        return current.memberships.any { sourceId -> catalog.source(sourceId)?.scan?.generation == request.sourceGeneration }
    }

    private suspend fun invalidateChangedSessions() {
        sessions.entries.toList().forEach { (assetId, active) ->
            val current = catalog.asset(assetId)
            active.toList().forEach { session ->
                val request = session.request
                val generationCurrent = current?.memberships?.any { id -> catalog.source(id)?.scan?.generation == request.sourceGeneration } == true
                if (current == null || current.revision.cacheKey != request.revision.cacheKey || !generationCurrent || current.access.invalidFor(request.purpose)) {
                    session.close()
                }
            }
        }
    }

    private suspend fun enqueueDiscovered(sourceId: ChartSourceId) {
        var offset = 0
        do {
            val page = catalog.assets(ChartAssetQuery(sourceId = sourceId), offset)
            page.items.filter {
                it.validation == ChartAssetValidationState.DISCOVERED &&
                    it.access in setOf(ChartAssetAccessState.UNCHECKED, ChartAssetAccessState.CHANGED)
            }
                .forEach { asset ->
                    if (basicQueue.trySend(asset.id).isSuccess) {
                        mutableMetrics.update { value -> value.copy(queuedBasicChecks = value.queuedBasicChecks + 1) }
                    } else {
                        mutableMetrics.update { value -> value.copy(rejectedBasicChecks = value.rejectedBasicChecks + 1) }
                    }
                }
            offset += page.items.size
        } while (offset < page.total && page.items.isNotEmpty())
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runtimeJob.cancel()
        basicQueue.close()
        sessions.values.flatMap { it.toList() }.forEach(BudgetedReadSession::close)
        catalog.close()
    }

    companion object {
        const val MAX_OPEN_READ_SESSIONS = 12
        const val BASIC_WORKERS = 2
        const val BASIC_QUEUE_CAPACITY = 256

        fun create(context: Context, parentScope: CoroutineScope): AndroidChartLibraryRuntime {
            val catalog = RoomChartCatalogRepository.create(context, File(context.filesDir, "chart-library/catalog.db"))
            val resolver = context.contentResolver
            return AndroidChartLibraryRuntime(
                catalog = catalog,
                delegateAccess = AndroidChartResourceAccess(resolver),
                sourceController = AndroidChartSourceController(
                    catalog,
                    AndroidChartDocumentEnumerator(resolver),
                    AndroidPersistedChartGrantPort(resolver),
                ),
                revisionProbe = AndroidChartRevisionProbe(resolver),
                parentScope = parentScope,
                jobStore = SharedPreferencesChartValidationJobStore(context),
            )
        }

        internal fun createForTest(
            catalog: RoomChartCatalogRepository,
            delegateAccess: ChartResourceAccessPort,
            sourceController: AndroidChartSourceController,
            revisionProbe: ChartRevisionProbe,
            parentScope: CoroutineScope,
            jobStore: ChartValidationJobStore? = null,
        ) = AndroidChartLibraryRuntime(catalog, delegateAccess, sourceController, revisionProbe, parentScope, jobStore)

        internal val ALWAYS_INVALID_ACCESS = setOf(
            ChartAssetAccessState.PERMISSION_LOST,
            ChartAssetAccessState.SOURCE_OFFLINE,
            ChartAssetAccessState.MISSING,
            ChartAssetAccessState.PENDING,
            ChartAssetAccessState.DIRECT_READ_UNSUPPORTED,
        )
    }
}

private fun ChartAssetAccessState.invalidFor(purpose: ChartReadPurpose): Boolean =
    this in AndroidChartLibraryRuntime.ALWAYS_INVALID_ACCESS ||
        this == ChartAssetAccessState.CHANGED && purpose != ChartReadPurpose.VALIDATION

private class BudgetedReadSession(
    private val delegate: ChartReadSession,
    private val onClose: (BudgetedReadSession) -> Unit,
) : ChartReadSession {
    private val closed = AtomicBoolean(false)
    override val request: ChartReadRequest get() = delegate.request
    override val sourceSizeBytes: Long get() = delegate.sourceSizeBytes
    override fun readMetadata(limit: Int) = delegate.readMetadata(limit)
    override fun readTile(key: ChartTileKey, scheme: MapTileScheme) = delegate.readTile(key, scheme)
    override fun hasTile(key: ChartTileKey, scheme: MapTileScheme) = delegate.hasTile(key, scheme)
    override fun readStoredTiles(offset: Long, limit: Int) = delegate.readStoredTiles(offset, limit)
    override fun readSourceRange(offset: Long, maxByteCount: Int) = delegate.readSourceRange(offset, maxByteCount)
    override fun statistics() = delegate.statistics()
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            delegate.close()
            onClose(this)
        }
    }
}
