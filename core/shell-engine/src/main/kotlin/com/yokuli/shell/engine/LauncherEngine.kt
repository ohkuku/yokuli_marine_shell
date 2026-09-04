package com.yokuli.shell.engine

import com.yokuli.shell.contract.LauncherHostPort
import com.yokuli.shell.engine.geometry.WpReferenceProfiles
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.engine.layout.StartDocumentRepair
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

interface LauncherEngine {
    val state: StateFlow<LauncherEngineState>
    val effects: Flow<LauncherEffect>

    fun dispatch(action: LauncherAction)
}

class DefaultLauncherEngine(
    private val hostPort: LauncherHostPort,
    private val persistence: LauncherPersistencePort,
    private val defaultDocument: StartDocument,
    private val scope: CoroutineScope,
    private val reducer: LauncherReducer = DefaultLauncherReducer(),
) : LauncherEngine {
    private val startsRestoring = !persistence.loaded.value
    private val actions = Channel<LauncherAction>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(initialState())
    private val mutableEffects = MutableSharedFlow<LauncherEffect>(extraBufferCapacity = 32)

    override val state: StateFlow<LauncherEngineState> = mutableState.asStateFlow()
    override val effects: Flow<LauncherEffect> = mutableEffects.asSharedFlow()

    init {
        scope.launch {
            for (action in actions) {
                process(action)
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
        check(actions.trySend(action).isSuccess) { "LauncherEngine action queue is closed" }
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
        mutableState.value = reduction.state
        reduction.effects.forEach { effect ->
            if (effect is LauncherEffect.PersistDocument) {
                try {
                    persistence.saveDocument(effect.document)
                } catch (error: Throwable) {
                    publishEffect(
                        LauncherEffect.LogIncident(
                            LauncherIncident.PersistenceFailure(error.message ?: error.javaClass.simpleName),
                        ),
                    )
                }
            }
            publishEffect(effect)
        }
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
            surface = LauncherSurface.Start,
            start = StartScreenState(document = repaired),
            allApps = AllAppsState(catalog.revision),
            tasks = InternalTaskState(),
            catalog = catalog,
            recoveryMode = if (startsRestoring) LauncherRecoveryMode.RESTORING else LauncherRecoveryMode.NORMAL,
        )
    }

    private companion object {
        const val MAX_RETAINED_INCIDENTS = 32
    }
}
