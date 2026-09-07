package com.yokuli.marine.chart.library.android

import android.content.Context
import com.yokuli.marine.map.domain.ChartPackageImportException
import com.yokuli.marine.map.domain.ChartPackageImportFailure
import com.yokuli.marine.map.domain.ChartPackageInspectProgress
import com.yokuli.marine.map.domain.ChartPackageOperationId
import com.yokuli.marine.map.domain.ChartPackageRepository
import com.yokuli.marine.map.domain.MapTileScheme
import com.yokuli.marine.map.domain.chartlibrary.*
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.coroutines.sync.withPermit

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
    private val managedBridge: ManagedChartCatalogBridge? = null,
    private val catalogFile: File? = null,
) : ChartLibraryRuntimePort, AutoCloseable {
    private val runtimeJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + runtimeJob)
    private val readBudget = Semaphore(MAX_OPEN_READ_SESSIONS)
    private val waitingReads = AtomicInteger(0)
    private val sessions = ConcurrentHashMap<ChartAssetId, MutableSet<BudgetedReadSession>>()
    private val basicQueue = Channel<ChartAssetId>(BASIC_QUEUE_CAPACITY)
    private val copyBudget = Semaphore(MAX_ACTIVE_COPY_JOBS)
    private val copyJobs = ConcurrentHashMap<ChartAssetId, Job>()
    private val mutableMetrics = MutableStateFlow(ChartLibraryRuntimeMetrics())
    private val mutableStorage = MutableStateFlow(
        ChartLibraryStorageSnapshot.EMPTY.copy(
            copyCapability = if (managedBridge == null) ChartManagedCopyCapability.UNAVAILABLE else ChartManagedCopyCapability.AVAILABLE,
        ),
    )
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
        if (managedBridge != null) scope.launch { refreshManagedStore() }
        repeat(BASIC_WORKERS) {
            scope.launch {
                for (assetId in basicQueue) {
                    mutableMetrics.update { value -> value.copy(queuedBasicChecks = (value.queuedBasicChecks - 1).coerceAtLeast(0)) }
                    if (catalog.asset(assetId)?.needsBasicInspection() == true) {
                        validationController.inspectBasic(assetId)
                    }
                }
            }
        }
        scope.launch { recoverHistoricalAccessFailures() }
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
    override suspend fun managedCopyFor(originalAssetId: ChartAssetId) = catalog.managedCopyFor(originalAssetId)
    override suspend fun originalForManagedCopy(managedAssetId: ChartAssetId) = catalog.originalForManagedCopy(managedAssetId)
    override suspend fun layers(offset: Int, limit: Int) = catalog.layers(offset, limit)
    override suspend fun layer(id: ChartLayerId) = catalog.layer(id)
    override suspend fun views(offset: Int, limit: Int) = catalog.views(offset, limit)
    override suspend fun view(id: ChartViewId) = catalog.view(id)
    override suspend fun activeView() = catalog.activeView()
    override suspend fun transact(transaction: ChartCatalogTransaction) = catalog.transact(transaction)

    override suspend fun acceptPicker(selection: ChartPickerSelection) = sourceController.acceptPicker(selection)
    override suspend fun repair(sourceId: ChartSourceId, selection: ChartPickerSelection) =
        sourceController.repair(sourceId, selection)

    override suspend fun refresh(sourceId: ChartSourceId): ChartSourceCommandResult {
        if (sourceId == ManagedChartCatalogBridge.MANAGED_SOURCE_ID) {
            refreshManagedStore()
            return ChartSourceCommandResult.Accepted(sourceId)
        }
        val result = sourceController.refresh(sourceId)
        if (result is ChartSourceCommandResult.ScanPublished && result.status in setOf(ChartScanStatus.COMPLETE, ChartScanStatus.PARTIAL)) {
            enqueueDiscovered(sourceId)
        }
        return result
    }

    override suspend fun cancel(sourceId: ChartSourceId) = if (sourceId == ManagedChartCatalogBridge.MANAGED_SOURCE_ID) {
        ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.STALE_OPERATION)
    } else sourceController.cancel(sourceId)
    override suspend fun remove(sourceId: ChartSourceId) = if (sourceId == ManagedChartCatalogBridge.MANAGED_SOURCE_ID) {
        ChartSourceCommandResult.Rejected(ChartSourceCommandFailure.PERSISTENCE)
    } else sourceController.remove(sourceId)
    override suspend fun inspectBasic(assetId: ChartAssetId) = validationController.inspectBasic(assetId)
    override suspend fun verifyFull(assetId: ChartAssetId) = validationController.verifyFull(assetId)
    override fun cancel(assetId: ChartAssetId) = validationController.cancel(assetId)
    override suspend fun saveManagedCopy(assetId: ChartAssetId): ChartManagedCopyCommandResult {
        val bridge = managedBridge ?: return ChartManagedCopyCommandResult.Rejected(ChartManagedCopyFailure.NOT_AVAILABLE)
        val asset = catalog.asset(assetId)
            ?: return ChartManagedCopyCommandResult.Rejected(ChartManagedCopyFailure.ASSET_NOT_FOUND)
        if (ManagedChartCatalogBridge.MANAGED_SOURCE_ID in asset.memberships) {
            return ChartManagedCopyCommandResult.Rejected(ChartManagedCopyFailure.NOT_AVAILABLE)
        }
        if (!bridge.hasSpaceFor(asset.facts.sizeBytes)) {
            return ChartManagedCopyCommandResult.Rejected(ChartManagedCopyFailure.INSUFFICIENT_SPACE)
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            copyBudget.withPermit {
                try {
                    updateCopy(assetId, ChartManagedCopyStatus.COPYING, totalBytes = asset.facts.sizeBytes)
                    val managedId = bridge.copy(
                        original = asset,
                        operationId = ChartPackageOperationId(UUID.randomUUID().toString()),
                        onProgress = { progress ->
                            when (progress) {
                                is ChartPackageInspectProgress.Copying -> updateCopy(
                                    assetId,
                                    ChartManagedCopyStatus.COPYING,
                                    copiedBytes = progress.completedBytes,
                                    totalBytes = progress.totalBytes,
                                )
                                is ChartPackageInspectProgress.Inspecting -> updateCopy(
                                    assetId,
                                    ChartManagedCopyStatus.VERIFYING,
                                    totalBytes = asset.facts.sizeBytes,
                                )
                            }
                        },
                        onPublishing = {
                            updateCopy(assetId, ChartManagedCopyStatus.PUBLISHING, totalBytes = asset.facts.sizeBytes)
                        },
                    )
                    updateCopy(
                        assetId,
                        ChartManagedCopyStatus.COMPLETED,
                        copiedBytes = asset.facts.sizeBytes ?: mutableStorage.value.copyJobs[assetId]?.copiedBytes ?: 0L,
                        totalBytes = asset.facts.sizeBytes,
                        managedAssetId = managedId,
                    )
                } catch (cancelled: CancellationException) {
                    updateCopy(assetId, ChartManagedCopyStatus.CANCELLED)
                    throw cancelled
                } catch (error: Throwable) {
                    val failure = if (
                        error is ChartPackageImportException && error.reason == ChartPackageImportFailure.INSUFFICIENT_SPACE
                    ) ChartManagedCopyFailure.INSUFFICIENT_SPACE else ChartManagedCopyFailure.PERSISTENCE
                    updateCopy(assetId, ChartManagedCopyStatus.FAILED, failure = failure)
                } finally {
                    copyJobs.remove(assetId)
                    refreshManagedStore()
                }
            }
        }
        val admitted = synchronized(copyJobs) {
            if (copyJobs.size >= MAX_QUEUED_COPY_JOBS || copyJobs.containsKey(assetId)) false
            else {
                copyJobs[assetId] = job
                true
            }
        }
        if (!admitted) {
            job.cancel()
            return ChartManagedCopyCommandResult.Rejected(ChartManagedCopyFailure.NOT_AVAILABLE)
        }
        updateCopy(assetId, ChartManagedCopyStatus.QUEUED, totalBytes = asset.facts.sizeBytes)
        job.start()
        return ChartManagedCopyCommandResult.Accepted(assetId)
    }

    override fun cancelManagedCopy(assetId: ChartAssetId) {
        copyJobs[assetId]?.cancel()
    }

    override suspend fun deleteManagedCopy(assetId: ChartAssetId, confirmed: Boolean): ChartManagedDeleteResult {
        val bridge = managedBridge ?: return ChartManagedDeleteResult.Rejected(ChartManagedCopyFailure.NOT_AVAILABLE)
        val asset = catalog.asset(assetId)
            ?: return ChartManagedDeleteResult.Rejected(ChartManagedCopyFailure.ASSET_NOT_FOUND)
        if (ManagedChartCatalogBridge.MANAGED_SOURCE_ID !in asset.memberships) {
            return ChartManagedDeleteResult.Rejected(ChartManagedCopyFailure.NOT_AVAILABLE)
        }
        if (!confirmed) return ChartManagedDeleteResult.ConfirmationRequired(
            ChartManagedDeleteImpact(assetId, mayAffectDisplay = true),
        )
        sessions.remove(assetId)?.toList()?.forEach(ChartReadSession::close)
        return try {
            bridge.delete(asset)
            refreshManagedStore()
            ChartManagedDeleteResult.Deleted
        } catch (error: ChartPackageImportException) {
            ChartManagedDeleteResult.Rejected(
                if (error.reason == ChartPackageImportFailure.PACKAGE_IN_USE) {
                    ChartManagedCopyFailure.ACTIVE_LEASE
                } else {
                    ChartManagedCopyFailure.PERSISTENCE
                },
            )
        } catch (_: Throwable) {
            ChartManagedDeleteResult.Rejected(ChartManagedCopyFailure.PERSISTENCE)
        }
    }

    override suspend fun open(request: ChartReadRequest): ChartOpenResult {
        if (closed.get()) return ChartOpenResult.Rejected(ChartReadFailure.SESSION_CLOSED, "Chart library runtime is closed")
        if (!requestIsCurrent(request)) return ChartOpenResult.Rejected(
            ChartReadFailure.REVISION_CHANGED, "Catalog revision or source generation changed",
        )
        val queued = waitingReads.incrementAndGet()
        if (queued > MAX_PENDING_READ_REQUESTS) {
            waitingReads.decrementAndGet()
            mutableMetrics.update { value -> value.copy(rejectedReadRequests = value.rejectedReadRequests + 1L) }
            return ChartOpenResult.Rejected(ChartReadFailure.RESOURCE_LIMIT, "Chart read queue is full")
        }
        mutableMetrics.update { value ->
            value.copy(
                queuedReadRequests = queued,
                queuedReadHighWater = maxOf(value.queuedReadHighWater, queued),
            )
        }
        try {
            readBudget.acquire()
        } finally {
            val remaining = waitingReads.decrementAndGet().coerceAtLeast(0)
            mutableMetrics.update { value -> value.copy(queuedReadRequests = remaining) }
        }
        var openedSession: ChartReadSession? = null
        var permitTransferred = false
        try {
            if (closed.get()) {
                return ChartOpenResult.Rejected(ChartReadFailure.SESSION_CLOSED, "Chart library runtime is closed")
            }
            val opened = delegateAccess.open(request)
            if (opened !is ChartOpenResult.Opened) return opened
            openedSession = opened.session
            if (!requestIsCurrent(request)) {
                return ChartOpenResult.Rejected(ChartReadFailure.REVISION_CHANGED, "Catalog changed while opening")
            }
            val tracked = BudgetedReadSession(opened.session) { session, statistics ->
                sessions[request.assetId]?.let { set ->
                    set.remove(session)
                    if (set.isEmpty()) sessions.remove(request.assetId, set)
                }
                readBudget.release()
                mutableMetrics.update { value -> value.copy(
                    activeReadSessions = (value.activeReadSessions - 1).coerceAtLeast(0),
                    closedReadSessions = value.closedReadSessions + 1L,
                    sourceBytesRead = value.sourceBytesRead.saturatedPlus(statistics.sourceBytesRead),
                    tileQueries = value.tileQueries.saturatedPlus(statistics.tileQueries),
                ) }
            }
            val revisionBeforeRegistration = catalog.snapshot.value.revision
            sessions.computeIfAbsent(request.assetId) { ConcurrentHashMap.newKeySet() }.add(tracked)
            mutableMetrics.update { value ->
                val active = value.activeReadSessions + 1
                value.copy(activeReadSessions = active, readSessionHighWater = maxOf(value.readSessionHighWater, active))
            }
            permitTransferred = true
            openedSession = null
            try {
                if (catalog.snapshot.value.revision != revisionBeforeRegistration && !requestIsCurrent(request)) {
                    tracked.close()
                    return ChartOpenResult.Rejected(
                        ChartReadFailure.REVISION_CHANGED,
                        "Catalog changed while registering session",
                    )
                }
            } catch (error: Throwable) {
                runCatching(tracked::close)
                throw error
            }
            return ChartOpenResult.Opened(tracked)
        } finally {
            if (!permitTransferred) {
                runCatching { openedSession?.close() }
                readBudget.release()
            }
        }
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
            page.items.filter(ChartAsset::needsBasicInspection).forEach(::enqueueBasic)
            offset += page.items.size
        } while (offset < page.total && page.items.isNotEmpty())
    }

    /**
     * `DIRECT_READ_UNSUPPORTED` is a historical provider-capability observation, not a content
     * verdict. Older catalog rows are re-probed on process start so the existing stream/local
     * fallback can recover them without a rescan, data clear, or source re-import.
     */
    private suspend fun recoverHistoricalAccessFailures() {
        val query = ChartAssetQuery(access = setOf(ChartAssetAccessState.DIRECT_READ_UNSUPPORTED))
        val initialTotal = catalog.assets(query, limit = 1).total
        var offset = if (initialTotal == 0) -1 else {
            ((initialTotal - 1) / DEFAULT_CATALOG_PAGE_SIZE) * DEFAULT_CATALOG_PAGE_SIZE
        }
        while (offset >= 0) {
            val page = catalog.assets(query, offset = offset)
            page.items.forEach { historical ->
                if (catalog.asset(historical.id)?.access == ChartAssetAccessState.DIRECT_READ_UNSUPPORTED) {
                    validationController.inspectBasic(historical.id)
                }
            }
            offset -= DEFAULT_CATALOG_PAGE_SIZE
        }
    }

    private fun enqueueBasic(asset: ChartAsset) {
        if (basicQueue.trySend(asset.id).isSuccess) {
            mutableMetrics.update { value -> value.copy(queuedBasicChecks = value.queuedBasicChecks + 1) }
        } else {
            mutableMetrics.update { value -> value.copy(rejectedBasicChecks = value.rejectedBasicChecks + 1) }
        }
    }

    private suspend fun refreshManagedStore() {
        val bridge = managedBridge ?: return
        val store = try {
            bridge.synchronize()
        } catch (_: Throwable) {
            // Keep the last catalog truth, but never claim that managed writes are usable while
            // the store cannot be reconciled. A later explicit refresh retries convergence.
            mutableStorage.update {
                it.copy(
                    availableCopyBytes = null,
                    copyCapability = ChartManagedCopyCapability.UNAVAILABLE,
                )
            }
            return
        }
        val catalogBytes = catalogFile?.let { file ->
            sequenceOf(file, File("${file.path}-wal"), File("${file.path}-shm"))
                .filter(File::isFile).sumOf(File::length)
        }
        mutableStorage.update {
            it.copy(
                managedCopyBytes = store.storageBytes,
                catalogBytes = catalogBytes,
                cacheBytes = 0L,
                availableCopyBytes = bridge.availableSpaceBytes(),
                copyCapability = ChartManagedCopyCapability.AVAILABLE,
            )
        }
    }

    private fun updateCopy(
        assetId: ChartAssetId,
        status: ChartManagedCopyStatus,
        copiedBytes: Long? = null,
        totalBytes: Long? = null,
        managedAssetId: ChartAssetId? = null,
        failure: ChartManagedCopyFailure? = null,
    ) {
        mutableStorage.update { storage ->
            val previous = storage.copyJobs[assetId]
            val completed = copiedBytes ?: previous?.copiedBytes ?: 0L
            val total = (totalBytes ?: previous?.totalBytes)?.coerceAtLeast(completed)
            val updated = ChartManagedCopyProgress(assetId, status, completed, total, managedAssetId, failure)
            val jobs = (storage.copyJobs + (assetId to updated)).entries
                .sortedWith(
                    compareBy<Map.Entry<ChartAssetId, ChartManagedCopyProgress>> {
                        it.value.status in ACTIVE_COPY_STATES
                    }.thenBy { it.key.value },
                )
                .takeLast(MAX_COPY_JOB_HISTORY)
                .associate { it.toPair() }
            storage.copy(copyJobs = jobs)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runtimeJob.cancel()
        basicQueue.close()
        copyJobs.values.forEach(Job::cancel)
        sessions.values.flatMap { it.toList() }.forEach(BudgetedReadSession::close)
        catalog.close()
    }

    companion object {
        const val MAX_OPEN_READ_SESSIONS = 12
        const val MAX_PENDING_READ_REQUESTS = 12
        const val BASIC_WORKERS = 2
        const val BASIC_QUEUE_CAPACITY = 256
        const val MAX_ACTIVE_COPY_JOBS = 1
        const val MAX_QUEUED_COPY_JOBS = 8

        fun create(
            context: Context,
            parentScope: CoroutineScope,
            managedRepository: ChartPackageRepository? = null,
        ): AndroidChartLibraryRuntime {
            val catalogFile = File(context.filesDir, "chart-library/catalog.db")
            val catalog = RoomChartCatalogRepository.create(context, catalogFile)
            val resolver = context.contentResolver
            val managedRoot = File(context.filesDir, "map_packages")
            val bridge = managedRepository?.let { ManagedChartCatalogBridge(catalog, it, managedRoot) }
            return AndroidChartLibraryRuntime(
                catalog = catalog,
                delegateAccess = AndroidChartResourceAccess(
                    resolver,
                    managedRoot,
                    managedRepository?.let { repository -> repository::acquireLease },
                    File(context.cacheDir, "chart-library-access"),
                ),
                sourceController = AndroidChartSourceController(
                    catalog,
                    AndroidChartDocumentEnumerator(resolver),
                    AndroidPersistedChartGrantPort(resolver),
                ),
                revisionProbe = AndroidChartRevisionProbe(resolver),
                parentScope = parentScope,
                jobStore = SharedPreferencesChartValidationJobStore(context),
                managedBridge = bridge,
                catalogFile = catalogFile,
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
        )
    }
}

private fun ChartAssetAccessState.invalidFor(purpose: ChartReadPurpose): Boolean =
    this in AndroidChartLibraryRuntime.ALWAYS_INVALID_ACCESS ||
        this in setOf(ChartAssetAccessState.CHANGED, ChartAssetAccessState.DIRECT_READ_UNSUPPORTED) &&
        purpose != ChartReadPurpose.VALIDATION

private fun ChartAsset.needsBasicInspection(): Boolean =
    access == ChartAssetAccessState.DIRECT_READ_UNSUPPORTED ||
        validation == ChartAssetValidationState.DISCOVERED &&
        access in setOf(ChartAssetAccessState.UNCHECKED, ChartAssetAccessState.CHANGED)

private val ACTIVE_COPY_STATES = setOf(
    ChartManagedCopyStatus.QUEUED,
    ChartManagedCopyStatus.COPYING,
    ChartManagedCopyStatus.VERIFYING,
    ChartManagedCopyStatus.PUBLISHING,
)

private class BudgetedReadSession(
    private val delegate: ChartReadSession,
    private val onClose: (BudgetedReadSession, ChartReadStatistics) -> Unit,
) : ChartReadSession {
    private val closed = AtomicBoolean(false)
    override val request: ChartReadRequest get() = delegate.request
    override val sourceSizeBytes: Long get() = delegate.sourceSizeBytes
    override val accessMode: ChartReadAccessMode get() = delegate.accessMode
    override val metadataPresent: Boolean get() = delegate.metadataPresent
    override fun readMetadata(limit: Int) = delegate.readMetadata(limit)
    override fun readZoomRange() = delegate.readZoomRange()
    override fun readTileExtents(limit: Int) = delegate.readTileExtents(limit)
    override fun readTile(key: ChartTileKey, scheme: MapTileScheme) = delegate.readTile(key, scheme)
    override fun hasTile(key: ChartTileKey, scheme: MapTileScheme) = delegate.hasTile(key, scheme)
    override fun readSampleTiles(limit: Int) = delegate.readSampleTiles(limit)
    override fun readStoredTiles(offset: Long, limit: Int) = delegate.readStoredTiles(offset, limit)
    override fun readSourceRange(offset: Long, maxByteCount: Int) = delegate.readSourceRange(offset, maxByteCount)
    override fun statistics() = delegate.statistics()
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            val statistics = runCatching(delegate::statistics).getOrDefault(ChartReadStatistics())
            try {
                delegate.close()
            } finally {
                onClose(this, statistics)
            }
        }
    }
}

private fun Long.saturatedPlus(other: Long): Long =
    if (other > Long.MAX_VALUE - this) Long.MAX_VALUE else this + other
