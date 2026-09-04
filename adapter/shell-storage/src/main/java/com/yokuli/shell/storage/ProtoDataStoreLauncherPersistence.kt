package com.yokuli.shell.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.yokuli.shell.engine.LauncherPersistedState
import com.yokuli.shell.engine.LauncherPersistedStateMigration
import com.yokuli.shell.engine.LauncherPersistenceIncident
import com.yokuli.shell.engine.LauncherPersistencePort
import com.yokuli.shell.engine.LauncherRecoveryDecision
import com.yokuli.shell.engine.LauncherRecoveryPolicy
import com.yokuli.shell.engine.layout.StartDocument
import com.yokuli.shell.storage.proto.LauncherStateProto
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProtoDataStoreLauncherPersistence private constructor(
    private val dataStore: DataStore<LauncherStateProto>,
    private val defaults: LauncherPersistedState,
    private val mutableIncidents: MutableSharedFlow<LauncherPersistenceIncident>,
    scope: CoroutineScope,
) : LauncherPersistencePort {
    private val mutableState = MutableStateFlow<LauncherPersistedState?>(null)
    private val mutableDocument = MutableStateFlow<StartDocument?>(null)
    private val mutableLoaded = MutableStateFlow(false)

    override val state: StateFlow<LauncherPersistedState?> = mutableState
    override val document: StateFlow<StartDocument?> = mutableDocument
    override val loaded: StateFlow<Boolean> = mutableLoaded
    override val incidents: Flow<LauncherPersistenceIncident> = mutableIncidents.asSharedFlow()

    init {
        scope.launch {
            dataStore.data.collect { proto ->
                val result = LauncherPersistedStateMigration.migrate(LauncherProtoMapper.decode(proto), defaults)
                publish(result.state)
                result.incidents.forEach { mutableIncidents.emit(it) }
                if (LauncherProtoMapper.encode(result.state) != proto) {
                    dataStore.updateData { LauncherProtoMapper.encode(result.state) }
                }
            }
        }
    }

    override suspend fun load(): LauncherPersistedState? {
        loaded.first { it }
        return state.value
    }

    override suspend fun save(state: LauncherPersistedState) {
        val migrated = LauncherPersistedStateMigration.migrate(state, defaults).state
        val written = dataStore.updateData { LauncherProtoMapper.encode(migrated) }
        publish(LauncherProtoMapper.decode(written))
    }

    override suspend fun saveDocument(document: StartDocument) {
        updateCurrent { current -> current.copy(document = document) }
    }

    override suspend fun savePreferences(themeModeName: String, accentName: String, languageTag: String) {
        updateCurrent { current ->
            current.copy(
                themeModeName = themeModeName,
                accentName = accentName,
                languageTag = languageTag,
            )
        }
    }

    override suspend fun beginLaunch(nowEpochMillis: Long): LauncherRecoveryDecision {
        var decision: LauncherRecoveryDecision? = null
        updateCurrent { current ->
            LauncherRecoveryPolicy.beginLaunch(current.recovery, nowEpochMillis).let {
                decision = it
                current.copy(recovery = it.health)
            }
        }
        return requireNotNull(decision)
    }

    override suspend fun markLaunchHealthy() {
        updateCurrent { current -> current.copy(recovery = LauncherRecoveryPolicy.markHealthy(current.recovery)) }
    }

    override suspend fun reset() {
        val written = dataStore.updateData { LauncherProtoMapper.encode(defaults) }
        publish(LauncherProtoMapper.decode(written))
    }

    private fun publish(state: LauncherPersistedState) {
        mutableState.value = state
        mutableDocument.value = state.document
        mutableLoaded.value = true
    }

    private suspend fun updateCurrent(transform: (LauncherPersistedState) -> LauncherPersistedState) {
        val written = dataStore.updateData { currentProto ->
            val current = LauncherPersistedStateMigration.migrate(LauncherProtoMapper.decode(currentProto), defaults).state
            LauncherProtoMapper.encode(LauncherPersistedStateMigration.migrate(transform(current), defaults).state)
        }
        publish(LauncherProtoMapper.decode(written))
    }

    companion object {
        private const val FILE_NAME = "launcher_state.pb"

        fun create(
            context: Context,
            scope: CoroutineScope,
            defaults: LauncherPersistedState,
        ): ProtoDataStoreLauncherPersistence = create(context.dataStoreFile(FILE_NAME), scope, defaults)

        fun create(
            file: File,
            scope: CoroutineScope,
            defaults: LauncherPersistedState,
        ): ProtoDataStoreLauncherPersistence {
            val defaultProto = LauncherProtoMapper.encode(defaults)
            val serializer = LauncherStateSerializer(defaultProto)
            val incidents = MutableSharedFlow<LauncherPersistenceIncident>(replay = 16)
            return ProtoDataStoreLauncherPersistence(
                dataStore = DataStoreFactory.create(
                    serializer = serializer,
                    corruptionHandler = ReplaceFileCorruptionHandler {
                        incidents.tryEmit(LauncherPersistenceIncident.CORRUPT_DATA_REPLACED)
                        defaultProto
                    },
                    scope = scope,
                    produceFile = { file },
                ),
                defaults = defaults,
                mutableIncidents = incidents,
                scope = scope,
            )
        }
    }
}
