package com.yokuli.shell.engine

import com.yokuli.shell.contract.LauncherHostPort
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.StartDocumentRepair
import com.yokuli.shell.contract.TileInstanceId
import com.yokuli.shell.engine.layout.TileCommitRequest
import com.yokuli.shell.engine.layout.TileCommitResult
import com.yokuli.shell.engine.layout.TileCommitPolicy
import com.yokuli.shell.engine.layout.TileRemovalRecord
import com.yokuli.shell.engine.interaction.StartInteractionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.selects.select

interface LauncherEngine {
    val state: StateFlow<LauncherEngineState>
    val effects: Flow<LauncherEffect>

    fun dispatch(action: LauncherAction)
    suspend fun commitTile(request: TileCommitRequest): TileCommitResult
    suspend fun removeTile(requestId: String, tileId: TileInstanceId, expectedRevision: Long): TileCommitResult
    suspend fun undoTile(requestId: String, removalRequestId: String): TileCommitResult
}

class DefaultLauncherEngine(
    private val hostPort: LauncherHostPort,
    private val persistence: LauncherPersistencePort,
    private val defaultDocument: StartDocument,
    private val scope: CoroutineScope,
    private val reducer: LauncherReducer = DefaultLauncherReducer(),
) : LauncherEngine {
    private val startsRestoring = !persistence.loaded.value
    private val actionSignal = Channel<Unit>(Channel.CONFLATED)
    private sealed interface Work {
        data class Action(val action: LauncherAction) : Work
        data class Tile(val transform: (StartDocument) -> TileCommitPolicy.Decision, val reply: CompletableDeferred<TileCommitResult>) : Work
    }
    private val actionQueue = ArrayDeque<Work>()
    private val actionQueueLock = Any()
    private val mutableState = MutableStateFlow(initialState())
    private val mutableEffects = MutableSharedFlow<LauncherEffect>(extraBufferCapacity = 32)

    override val state: StateFlow<LauncherEngineState> = mutableState.asStateFlow()
    override val effects: Flow<LauncherEffect> = mutableEffects.asSharedFlow()

    init {
        scope.launch {
            try {
                for (ignored in actionSignal) {
                    while (true) {
                        val work = synchronized(actionQueueLock) { actionQueue.removeFirstOrNull() } ?: break
                        try {
                            when (work) {
                                is Work.Action -> process(work.action)
                                is Work.Tile -> processTile(work)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            if (work is Work.Tile) work.reply.complete(TileCommitResult.Failed(error.message ?: "Tile operation failed"))
                            publishEffect(LauncherEffect.LogIncident(LauncherIncident.PersistenceFailure(error.message ?: error.javaClass.simpleName)))
                        }
                    }
                }
            } finally {
                actionSignal.close()
                val pending = synchronized(actionQueueLock) { actionQueue.toList().also { actionQueue.clear() } }
                pending.filterIsInstance<Work.Tile>().forEach { it.reply.complete(TileCommitResult.Failed("Save interrupted; restore the same request before retrying")) }
            }
        }
        scope.launch {
            hostPort.catalog.collect { catalog ->
                dispatch(LauncherAction.CatalogChanged(catalog))
            }
        }
        scope.launch {
            persistence.incidents.collect { incident ->
                dispatch(LauncherAction.PersistenceIncidentObserved(incident))
            }
        }
        if (startsRestoring) {
            scope.launch {
                persistence.loaded.first { it }
                dispatch(LauncherAction.RestorePersistedDocument(persistence.document.value))
            }
        }
    }

    override fun dispatch(action: LauncherAction) {
        synchronized(actionQueueLock) {
            check(actionQueue.size < MAX_PENDING_ACTIONS) { "LauncherEngine action queue capacity exceeded" }
            actionQueue.addLast(Work.Action(action))
        }
        check(actionSignal.trySend(Unit).isSuccess) { "LauncherEngine action queue is closed" }
    }

    override suspend fun commitTile(request: TileCommitRequest): TileCommitResult = enqueueTile { document ->
        TileCommitPolicy.save(document, request, mutableState.value.catalog.entries)
    }

    override suspend fun removeTile(requestId: String, tileId: TileInstanceId, expectedRevision: Long): TileCommitResult = enqueueTile {
        TileCommitPolicy.remove(it, requestId, tileId, expectedRevision)
    }

    override suspend fun undoTile(requestId: String, removalRequestId: String): TileCommitResult = enqueueTile {
        TileCommitPolicy.undo(it, requestId, removalRequestId)
    }

    private suspend fun enqueueTile(transform: (StartDocument) -> TileCommitPolicy.Decision): TileCommitResult {
        // reply 不绑定页面 Job：接受的写入即使页面 Home/关闭，仍在引擎作用域完成。
        val reply = CompletableDeferred<TileCommitResult>()
        synchronized(actionQueueLock) {
            if (actionQueue.size >= MAX_PENDING_ACTIONS) return TileCommitResult.Failed("Tile save queue is full")
            actionQueue.addLast(Work.Tile(transform, reply))
        }
        if (!actionSignal.trySend(Unit).isSuccess) return TileCommitResult.Failed("Tile save queue is closed")
        return reply.await()
    }

    private suspend fun processTile(work: Work.Tile) {
        if (mutableState.value.recoveryMode != LauncherRecoveryMode.NORMAL) {
            work.reply.complete(TileCommitResult.Failed("Start is still restoring"))
            return
        }
        try {
            var decision: TileCommitPolicy.Decision? = null
            val written = writeDocument { latest ->
                work.transform(latest ?: mutableState.value.start.document).also { decision = it }.document
            }
            mutableState.value = mutableState.value.copy(start = mutableState.value.start.copy(document = written))
            work.reply.complete(requireNotNull(decision).result)
        } catch (cancelled: CancellationException) {
            work.reply.complete(TileCommitResult.Failed("Save interrupted; restore the same request before retrying"))
            throw cancelled
        } catch (error: Exception) {
            work.reply.complete(TileCommitResult.Failed(error.message ?: error.javaClass.simpleName))
            publishEffect(LauncherEffect.LogIncident(LauncherIncident.PersistenceFailure(error.message ?: error.javaClass.simpleName)))
        }
    }

    private suspend fun process(action: LauncherAction) {
        val resolution = if (action is LauncherAction.Open) hostPort.resolveLaunch(action.token) else null
        val profile = runCatching { WpReferenceProfiles.require(mutableState.value.start.document.profileId) }
            .getOrElse { WpReferenceProfiles.require(defaultDocument.profileId) }
        val reduction = reducer.reduce(
            state = mutableState.value,
            action = action,
            context = LauncherReducerContext(
                defaultDocument = defaultDocument.copy(profileId = profile.id),
                profile = profile,
                launchResolution = resolution,
            ),
        )
        val persist = reduction.effects.filterIsInstance<LauncherEffect.PersistDocument>().lastOrNull()
        if (persist == null) {
            mutableState.value = reduction.state
            reduction.effects.forEach { publishEffect(it) }
            return
        }
        val previousState = mutableState.value
        try {
            val written = writeDocument { stored ->
                val previous = stored ?: previousState.start.activeTransaction?.before ?: previousState.start.document
                val candidate = persist.document
                val oldById = previous.placements.associateBy { it.tileId }
                val columns = WpReferenceProfiles.require(candidate.profileId).columnCount
                val oldCells = com.yokuli.shell.engine.layout.AdaptiveTilePacker.pack(previous, columns).tiles.associate { it.entry.tileId to it.cell }
                val newCells = com.yokuli.shell.engine.layout.AdaptiveTilePacker.pack(candidate, columns).tiles.associate { it.entry.tileId to it.cell }
                val changed = candidate.placements.map { entry ->
                    val old = oldById[entry.tileId]
                    val unchanged = old != null && entry.copy(revision = old.revision, preservedProto = old.preservedProto,
                        preferredCell = old.preferredCell, rank = old.rank) == old && oldCells[entry.tileId] == newCells[entry.tileId]
                    // 将隐式格位显式冻结不是用户改变该实例，不让无关编辑误报版本冲突。
                    if (unchanged) entry.copy(revision = old!!.revision) else entry.copy(revision = previous.revision + 1)
                }
                val newRemoved = previous.placements.filter { old -> candidate.placements.none { it.tileId == old.tileId } }
                val transactionId = reduction.state.start.undoStack.lastOrNull()?.id ?: "layout-${previous.revision + 1}"
                candidate.copy(
                    revision = previous.revision + 1, placements = changed,
                    receipts = previous.receipts,
                    removedTiles = (previous.removedTiles + newRemoved.map { TileRemovalRecord(transactionId, it, previous.revision + 1) }).takeLast(32),
                    preservedProto = previous.preservedProto,
                )
            }
            // 等待落盘期间 Home/切应用仍可用；成功只合并文档，不倒退访问栈。
            val afterNavigation = mutableState.value
            val committedUndo = reduction.state.start.undoStack.takeLast(32).map { transaction ->
                if (transaction.after == persist.document) transaction.copy(after = written) else transaction
            }
            mutableState.value = if (afterNavigation != previousState) afterNavigation.copy(
                start = afterNavigation.start.copy(document = written, undoStack = committedUndo),
            ) else reduction.state.copy(start = reduction.state.start.copy(document = written, undoStack = committedUndo))
            reduction.effects.forEach { effect ->
                publishEffect(if (effect is LauncherEffect.PersistDocument) effect.copy(document = written) else effect)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val latest = mutableState.value
            mutableState.value = latest.copy(start = latest.start.copy(
                document = previousState.start.activeTransaction?.before ?: previousState.start.document,
                activeTransaction = null, interaction = StartInteractionState.Idle,
            ), transient = if (latest.surface == previousState.surface) LauncherTransient.Notice(LauncherNotice.LAYOUT_UNAVAILABLE) else latest.transient)
            publishEffect(LauncherEffect.LogIncident(LauncherIncident.PersistenceFailure(error.message ?: error.javaClass.simpleName)))
        }
    }

    /** 中文：写入仍只有本队列；等待存储时只放行不更改布局的系统导航。 */
    private suspend fun writeDocument(transform: (StartDocument?) -> StartDocument): StartDocument = supervisorScope {
        val write = async { persistence.updateDocument(transform) }
        var result: StartDocument? = null
        while (result == null) {
            val navigation = synchronized(actionQueueLock) {
                val index = actionQueue.indexOfFirst { it is Work.Action && it.action.canRunDuringTileWrite() }
                if (index >= 0) actionQueue.removeAt(index) as Work.Action else null
            }
            if (navigation != null) {
                try {
                    process(navigation.action)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    publishEffect(LauncherEffect.LogIncident(LauncherIncident.PersistenceFailure(error.message ?: error.javaClass.simpleName)))
                }
                continue
            }
            result = select<StartDocument?> {
                write.onAwait { it }
                actionSignal.onReceiveCatching { signal ->
                    if (signal.isClosed) throw CancellationException("Launcher queue closed")
                    null
                }
            }
        }
        requireNotNull(result)
    }

    private fun LauncherAction.canRunDuringTileWrite(): Boolean = when (this) {
        LauncherAction.ShowStart, LauncherAction.ShowAllApps, LauncherAction.Back, LauncherAction.ShowDesktop,
        LauncherAction.OpenSearch, LauncherAction.ShowRecents, LauncherAction.DismissTransient,
        LauncherAction.OpenAlphabetJump, LauncherAction.ExitStartEdit, LauncherAction.CancelTileOperation -> true
        is LauncherAction.Open, is LauncherAction.UpdateSearchQuery, is LauncherAction.ActivateTask,
        is LauncherAction.CloseTask, is LauncherAction.OpenEntryContextMenu, is LauncherAction.RevealTile,
        is LauncherAction.AcknowledgeStartReveal -> true
        else -> false
    }

    private suspend fun publishEffect(effect: LauncherEffect) {
        if (effect is LauncherEffect.LogIncident) {
            mutableState.value = mutableState.value.copy(
                incidentLog = (mutableState.value.incidentLog + effect.incident).takeLast(MAX_RETAINED_INCIDENTS),
            )
        }
        mutableEffects.emit(effect)
    }

    private fun initialState(): LauncherEngineState {
        val catalog = hostPort.catalog.value
        val source = persistence.document.value ?: defaultDocument
        val profile = runCatching { WpReferenceProfiles.require(source.profileId) }
            .getOrElse { WpReferenceProfiles.require(defaultDocument.profileId) }
        val fallback = defaultDocument.copy(profileId = profile.id)
        val repaired = StartDocumentRepair.repair(source, catalog.entries, fallback, profile).document
        return LauncherEngineState(
            surface = ShellVisualSurface.Desktop,
            start = StartScreenState(document = repaired),
            allApps = AllAppsState(catalog.revision),
            tasks = InternalTaskState(),
            catalog = catalog,
            recoveryMode = if (startsRestoring) LauncherRecoveryMode.RESTORING else LauncherRecoveryMode.NORMAL,
        )
    }

    private companion object {
        const val MAX_RETAINED_INCIDENTS = 32
        const val MAX_PENDING_ACTIONS = 256
    }
}
