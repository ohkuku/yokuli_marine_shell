package com.yokuli.shell.engine

import com.yokuli.shell.contract.LauncherEntryId
import com.yokuli.shell.engine.layout.StartDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

const val CURRENT_LAUNCHER_PERSISTENCE_SCHEMA = 3

enum class PersistedLauncherPage { START, ALL_APPS }

data class LauncherStartupHealth(
    val startupAttemptCount: Int = 0,
    val launchPending: Boolean = false,
    val lastLaunchEpochMillis: Long = 0,
    val safeMode: Boolean = false,
)

data class LauncherPersistedState(
    val schemaVersion: Int = CURRENT_LAUNCHER_PERSISTENCE_SCHEMA,
    val document: StartDocument? = null,
    val themeModeName: String = "DARK",
    val accentName: String = "CYAN",
    val languageTag: String = "zh-CN",
    val layoutLocked: Boolean = false,
    val lastLauncherPage: PersistedLauncherPage = PersistedLauncherPage.START,
    val lastForegroundToken: String? = null,
    val productModelVersion: Int = 0,
    val recovery: LauncherStartupHealth = LauncherStartupHealth(),
)

enum class LauncherPersistenceIncident {
    CORRUPT_DATA_REPLACED,
    LEGACY_SCHEMA_MIGRATED,
    FUTURE_SCHEMA_REJECTED,
    INVALID_PREFERENCE_REPLACED,
    INVALID_PRODUCT_MODEL_VERSION_REPLACED,
    PRODUCT_MODEL_MIGRATED,
}

data class LauncherPersistenceMigrationResult(
    val state: LauncherPersistedState,
    val incidents: List<LauncherPersistenceIncident> = emptyList(),
)

object LauncherPersistedStateMigration {
    private val themes = setOf("DARK", "LIGHT")
    private val accents = setOf("COBALT", "CYAN", "EMERALD", "MAGENTA", "VIOLET", "CRIMSON", "AMBER")
    private val languages = setOf("zh-CN", "en")

    fun migrate(
        source: LauncherPersistedState?,
        defaults: LauncherPersistedState,
        productMigration: LauncherProductMigrationPlan = LauncherProductMigrationPlan.NONE,
        installedEntryIds: Set<LauncherEntryId> = emptySet(),
    ): LauncherPersistenceMigrationResult {
        if (source == null) {
            val product = productMigration.migrate(defaults, installedEntryIds)
            return LauncherPersistenceMigrationResult(
                product.state,
                if (product.appliedVersions.isEmpty()) emptyList()
                else listOf(LauncherPersistenceIncident.PRODUCT_MODEL_MIGRATED),
            )
        }
        if (source.schemaVersion > CURRENT_LAUNCHER_PERSISTENCE_SCHEMA) {
            return LauncherPersistenceMigrationResult(
                defaults,
                listOf(LauncherPersistenceIncident.FUTURE_SCHEMA_REJECTED),
            )
        }
        val incidents = mutableListOf<LauncherPersistenceIncident>()
        if (source.schemaVersion < CURRENT_LAUNCHER_PERSISTENCE_SCHEMA) {
            incidents += LauncherPersistenceIncident.LEGACY_SCHEMA_MIGRATED
        }
        fun normalized(value: String, accepted: Set<String>, fallback: String): String {
            if (value in accepted) return value
            incidents += LauncherPersistenceIncident.INVALID_PREFERENCE_REPLACED
            return fallback
        }
        val productModelVersion = source.productModelVersion.takeIf { it >= 0 } ?: run {
            incidents += LauncherPersistenceIncident.INVALID_PRODUCT_MODEL_VERSION_REPLACED
            0
        }
        val normalizedState = source.copy(
                schemaVersion = CURRENT_LAUNCHER_PERSISTENCE_SCHEMA,
                document = source.document ?: defaults.document,
                themeModeName = normalized(source.themeModeName, themes, defaults.themeModeName),
                accentName = normalized(source.accentName, accents, defaults.accentName),
                languageTag = normalized(source.languageTag, languages, defaults.languageTag),
                productModelVersion = productModelVersion,
            )
        val product = productMigration.migrate(normalizedState, installedEntryIds)
        if (product.appliedVersions.isNotEmpty()) {
            incidents += LauncherPersistenceIncident.PRODUCT_MODEL_MIGRATED
        }
        return LauncherPersistenceMigrationResult(
            product.state,
            incidents.distinct(),
        )
    }
}

/** A typed, product-neutral rule for translating a persisted launch token. */
data class LauncherTokenAlias(
    val legacy: String,
    val replacement: String,
    val prefix: Boolean = false,
) {
    init {
        require(legacy.isNotBlank())
        require(replacement.isNotBlank())
    }

    fun migrate(value: String): String? = when {
        prefix && value.startsWith(legacy) -> replacement + value.removePrefix(legacy)
        !prefix && value == legacy -> replacement
        else -> null
    }
}

data class LauncherProductMigrationStep(
    val version: Int,
    val targetEntryId: LauncherEntryId,
    val legacyEntryIds: Set<LauncherEntryId>,
    val tokenAliases: List<LauncherTokenAlias> = emptyList(),
) {
    init {
        require(version > 0)
        require(legacyEntryIds.isNotEmpty())
        require(targetEntryId !in legacyEntryIds)
    }
}

data class LauncherProductMigrationResult(
    val state: LauncherPersistedState,
    val appliedVersions: List<Int>,
)

/**
 * Applies only consecutive steps whose target app is actually installed. This keeps old tiles
 * usable until their replacement has a real host instead of exposing an empty future app.
 */
class LauncherProductMigrationPlan(
    steps: List<LauncherProductMigrationStep>,
) {
    private val orderedSteps = steps.sortedBy(LauncherProductMigrationStep::version)

    init {
        require(orderedSteps.map { it.version } == (1..orderedSteps.size).toList()) {
            "Product migration versions must be unique and consecutive from 1"
        }
    }

    fun migrate(
        source: LauncherPersistedState,
        installedEntryIds: Set<LauncherEntryId>,
    ): LauncherProductMigrationResult {
        var state = source
        val applied = mutableListOf<Int>()
        for (step in orderedSteps) {
            if (step.version <= state.productModelVersion) continue
            if (step.version != state.productModelVersion + 1 || step.targetEntryId !in installedEntryIds) break
            state = state.apply(step)
            applied += step.version
        }
        return LauncherProductMigrationResult(state, applied)
    }

    private fun LauncherPersistedState.apply(step: LauncherProductMigrationStep): LauncherPersistedState {
        val aliases = step.legacyEntryIds + step.targetEntryId
        val migratedDocument = document?.let { current ->
            val candidates = current.placements.filter { it.entryId in aliases }
            if (candidates.isEmpty()) current else {
                val survivor = candidates.minWith(
                    compareBy<com.yokuli.shell.engine.layout.TilePlacement> { it.rank }
                        .thenBy { it.tileId.value },
                )
                current.copy(
                    placements = current.placements.mapNotNull { placement ->
                        when {
                            placement.tileId == survivor.tileId -> placement.copy(entryId = step.targetEntryId)
                            placement.entryId in aliases -> null
                            else -> placement
                        }
                    },
                )
            }
        }
        val migratedToken = lastForegroundToken?.let { token ->
            step.tokenAliases.firstNotNullOfOrNull { alias -> alias.migrate(token) } ?: token
        }
        return copy(
            document = migratedDocument,
            lastForegroundToken = migratedToken,
            productModelVersion = step.version,
        )
    }

    companion object {
        val NONE = LauncherProductMigrationPlan(emptyList())
    }
}

data class LauncherRecoveryDecision(
    val health: LauncherStartupHealth,
    val enterSafeMode: Boolean,
)

object LauncherRecoveryPolicy {
    const val FAILURE_WINDOW_MILLIS = 120_000L
    const val SAFE_MODE_ATTEMPT_THRESHOLD = 3

    fun beginLaunch(previous: LauncherStartupHealth, nowEpochMillis: Long): LauncherRecoveryDecision {
        val previousAttemptIsRecent = previous.launchPending &&
            nowEpochMillis >= previous.lastLaunchEpochMillis &&
            nowEpochMillis - previous.lastLaunchEpochMillis <= FAILURE_WINDOW_MILLIS
        val attempts = if (previousAttemptIsRecent) previous.startupAttemptCount + 1 else 1
        val safeMode = previous.safeMode || attempts >= SAFE_MODE_ATTEMPT_THRESHOLD
        val health = LauncherStartupHealth(
            startupAttemptCount = attempts,
            launchPending = true,
            lastLaunchEpochMillis = nowEpochMillis,
            safeMode = safeMode,
        )
        return LauncherRecoveryDecision(health, safeMode)
    }

    fun markHealthy(previous: LauncherStartupHealth) = LauncherStartupHealth(
        startupAttemptCount = 0,
        launchPending = false,
        lastLaunchEpochMillis = previous.lastLaunchEpochMillis,
        safeMode = false,
    )
}

interface LauncherPersistencePort {
    val state: StateFlow<LauncherPersistedState?>
    val document: StateFlow<StartDocument?>
    val loaded: StateFlow<Boolean>
    val incidents: Flow<LauncherPersistenceIncident>
        get() = emptyFlow()

    suspend fun load(): LauncherPersistedState?
    suspend fun save(state: LauncherPersistedState)
    suspend fun reset()

    suspend fun saveDocument(document: StartDocument) {
        save((load() ?: LauncherPersistedState()).copy(document = document))
    }

    suspend fun savePreferences(themeModeName: String, accentName: String, languageTag: String) {
        save(
            (load() ?: LauncherPersistedState()).copy(
                themeModeName = themeModeName,
                accentName = accentName,
                languageTag = languageTag,
            ),
        )
    }

    suspend fun beginLaunch(nowEpochMillis: Long): LauncherRecoveryDecision {
        val current = load() ?: LauncherPersistedState()
        val decision = LauncherRecoveryPolicy.beginLaunch(current.recovery, nowEpochMillis)
        save(current.copy(recovery = decision.health))
        return decision
    }

    suspend fun markLaunchHealthy() {
        val current = load() ?: return
        save(current.copy(recovery = LauncherRecoveryPolicy.markHealthy(current.recovery)))
    }
}

class InMemoryLauncherPersistence(initialDocument: StartDocument? = null) : LauncherPersistencePort {
    private val mutableState = MutableStateFlow(
        initialDocument?.let { LauncherPersistedState(document = it) },
    )
    private val mutableDocument = MutableStateFlow(initialDocument)
    private val mutableLoaded = MutableStateFlow(true)

    override val state: StateFlow<LauncherPersistedState?> = mutableState
    override val document: StateFlow<StartDocument?> = mutableDocument
    override val loaded: StateFlow<Boolean> = mutableLoaded
    override val incidents: Flow<LauncherPersistenceIncident> = emptyFlow()

    override suspend fun load(): LauncherPersistedState? = mutableState.value

    override suspend fun save(state: LauncherPersistedState) {
        mutableState.value = state
        mutableDocument.value = state.document
    }

    override suspend fun reset() {
        mutableState.value = null
        mutableDocument.value = null
    }
}
