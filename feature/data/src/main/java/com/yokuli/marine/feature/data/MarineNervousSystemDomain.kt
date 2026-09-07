package com.yokuli.marine.feature.data

import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.nmea.ChecksumPolicy
import com.yokuli.marine.data.source.ResolvedDatum
import com.yokuli.marine.data.source.SourceCandidateAvailability

/** The only three first-level places in the recovered Data product. */
enum class PrimaryDataArea { BOAT, FLOW, CONNECTIONS }

/** Drill-down destinations are deliberately not promoted to first-level tabs. */
sealed interface DataSurface {
    data class Primary(val area: PrimaryDataArea) : DataSurface
    data class Sensor(val sensor: BoatSensor) : DataSurface
    data class Trust(val group: SourceGroup) : DataSurface
    data class Connection(val connectionId: com.yokuli.marine.data.model.ConnectionId) : DataSurface
    data class Diagnostics(val connectionId: com.yokuli.marine.data.model.ConnectionId? = null) : DataSurface
    data object AddSource : DataSurface
    data class ConnectionWizard(val type: DataConnectionType) : DataSurface
}

enum class BoatSensor {
    POSITION,
    HEADING,
    DEPTH,
    WIND,
}

enum class DataConnectionType { BOAT_GATEWAY, UDP_BROADCAST, ADVANCED_TCP, ADVANCED_UDP }

data class DataConnectionDraft(
    val id: ConnectionId,
    val type: DataConnectionType,
    val host: String,
    val portText: String,
    val customName: String = "",
    val checksumPolicy: ChecksumPolicy = ChecksumPolicy.STRICT,
    val expectedRevision: Long? = null,
) {
    val port: Int?
        get() = portText.takeIf { it.isNotEmpty() && it.all { character -> character in '0'..'9' } }
            ?.toIntOrNull()?.takeIf { it in 1..65_535 }

    val displayName: String
        get() = customName.trim().ifBlank {
            when (type) {
                DataConnectionType.BOAT_GATEWAY, DataConnectionType.ADVANCED_TCP -> "Boat Gateway"
                DataConnectionType.UDP_BROADCAST, DataConnectionType.ADVANCED_UDP -> "UDP Broadcast"
            }
        }

    fun configOrNull(): NmeaConnectionConfig? {
        val parsedPort = port ?: return null
        val endpoint = when (type) {
            DataConnectionType.BOAT_GATEWAY, DataConnectionType.ADVANCED_TCP ->
                host.trim().takeIf { it.isNotEmpty() }?.let { NmeaEndpoint.TcpClient(it, parsedPort) }
                    ?: return null
            DataConnectionType.UDP_BROADCAST, DataConnectionType.ADVANCED_UDP ->
                NmeaEndpoint.UdpListener(parsedPort)
        }
        return NmeaConnectionConfig(id, displayName, endpoint, checksumPolicy)
    }

    companion object {
        fun create(id: ConnectionId, type: DataConnectionType): DataConnectionDraft = DataConnectionDraft(
            id = id,
            type = type,
            host = "",
            portText = when (type) {
                DataConnectionType.BOAT_GATEWAY, DataConnectionType.ADVANCED_TCP -> "10110"
                DataConnectionType.UDP_BROADCAST, DataConnectionType.ADVANCED_UDP -> "10110"
            },
        )
    }
}

enum class ConnectionTestPhase {
    NOT_STARTED,
    SUBMITTING,
    WAITING_FOR_MARINE_DATA,
    DETECTED,
    FAILED,
    INVALID_CONFIGURATION,
}

data class DataConnectionTestState(
    val phase: ConnectionTestPhase = ConnectionTestPhase.NOT_STARTED,
    val detectedSensors: Set<BoatSensor> = emptySet(),
    val failure: com.yokuli.marine.data.runtime.NmeaRuntimeFailure? = null,
) {
    companion object {
        val IDLE = DataConnectionTestState()
    }
}

enum class SensorHealth {
    LIVE,
    HELD,
    STALE,
    UNAVAILABLE,
    NEEDS_ATTENTION,
}

data class BoatSensorState(
    val sensor: BoatSensor,
    val groups: Set<SourceGroup>,
    val health: SensorHealth,
    val resolvedValues: List<ResolvedDatum>,
    val selectedSource: SourceIdentity?,
    val sourceDisplayName: String?,
    val youngestAgeMillis: Long?,
    val groupStatuses: Map<SourceGroup, SourceGroupStatus>,
)

data class DataBoatOverview(
    /** Same immutable map instance exposed by [DataUiState]; no parallel UI value cache. */
    val resolvedTruth: Map<DataKey, ResolvedDatum>,
    val sensors: List<BoatSensorState>,
    val health: SensorHealth,
)

enum class MarineConsumerId {
    CHART,
    NAVIGATION,
    START_TILE,
}

data class MarineConsumerRegistration(
    val id: MarineConsumerId,
    val requiredSensors: Set<BoatSensor>,
    val optionalSensors: Set<BoatSensor>,
) {
    init {
        require(requiredSensors.intersect(optionalSensors).isEmpty())
    }
}

/** Composition-root truth: registration never implies that a consumer is currently active. */
data class MarineConsumerActivitySnapshot(
    val activeConsumers: Set<MarineConsumerId> = emptySet(),
) {
    companion object {
        val EMPTY = MarineConsumerActivitySnapshot()
    }
}

enum class ConsumerImpactSeverity { NONE, DEGRADED, BLOCKED }

data class MarineConsumerImpact(
    val consumerId: MarineConsumerId,
    val active: Boolean,
    val affectedSensors: Set<BoatSensor>,
    val severity: ConsumerImpactSeverity,
)

/** Stable product registry. New consumers register dependencies instead of Data guessing UI links. */
object MarineConsumerRegistry {
    val registrations: List<MarineConsumerRegistration> = listOf(
        MarineConsumerRegistration(
            id = MarineConsumerId.CHART,
            requiredSensors = emptySet(),
            optionalSensors = setOf(BoatSensor.POSITION, BoatSensor.HEADING),
        ),
        MarineConsumerRegistration(
            id = MarineConsumerId.NAVIGATION,
            requiredSensors = setOf(BoatSensor.POSITION),
            optionalSensors = setOf(BoatSensor.HEADING),
        ),
        MarineConsumerRegistration(
            id = MarineConsumerId.START_TILE,
            requiredSensors = emptySet(),
            optionalSensors = BoatSensor.entries.toSet(),
        ),
    ).also { registrations ->
        require(registrations.map { it.id }.distinct().size == registrations.size)
    }
}

internal object MarineNervousSystemProjector {
    fun boat(
        resolvedTruth: Map<DataKey, ResolvedDatum>,
        groups: List<SourceGroupState>,
    ): DataBoatOverview {
        val sensors = BoatSensor.entries.map { sensor ->
            val sensorGroups = sensor.sourceGroups
            val groupStates = groups.filter { it.group in sensorGroups }
            val values = resolvedTruth.values.filter { it.key in sensorGroups.flatMapTo(linkedSetOf()) { group -> group.keys } }
            val selectedSources = groupStates.mapNotNull { it.selectedSource }.distinct()
            val selectedSource = selectedSources.singleOrNull()
            val selectedCandidate = selectedSource?.let { source ->
                groupStates.asSequence().flatMap { it.candidates.asSequence() }
                    .firstOrNull { it.source == source }
            }
            BoatSensorState(
                sensor = sensor,
                groups = sensorGroups,
                health = sensorHealth(groupStates, values),
                resolvedValues = values,
                selectedSource = selectedSource,
                sourceDisplayName = selectedCandidate?.displayName,
                youngestAgeMillis = values.mapNotNull { it.candidate?.ageMillis }.minOrNull(),
                groupStatuses = groupStates.associate { it.group to it.status },
            )
        }
        return DataBoatOverview(
            resolvedTruth = resolvedTruth,
            sensors = sensors,
            health = overallHealth(sensors),
        )
    }

    fun consumerImpact(
        boat: DataBoatOverview,
        activity: MarineConsumerActivitySnapshot,
    ): List<MarineConsumerImpact> = MarineConsumerRegistry.registrations.map { registration ->
        val active = registration.id in activity.activeConsumers
        val affected = if (active) {
            (registration.requiredSensors + registration.optionalSensors).filterTo(linkedSetOf()) { sensor ->
                boat.sensors.single { it.sensor == sensor }.health in IMPACTING_HEALTH
            }
        } else {
            emptySet()
        }
        val blocked = affected.any { it in registration.requiredSensors }
        MarineConsumerImpact(
            consumerId = registration.id,
            active = active,
            affectedSensors = affected,
            severity = when {
                !active || affected.isEmpty() -> ConsumerImpactSeverity.NONE
                blocked -> ConsumerImpactSeverity.BLOCKED
                else -> ConsumerImpactSeverity.DEGRADED
            },
        )
    }

    private fun sensorHealth(
        groups: List<SourceGroupState>,
        values: List<ResolvedDatum>,
    ): SensorHealth = when {
        groups.any { it.status in ATTENTION_STATUSES } -> SensorHealth.NEEDS_ATTENTION
        values.any { it.availability == SourceCandidateAvailability.LIVE } -> SensorHealth.LIVE
        values.any { it.availability == SourceCandidateAvailability.HELD } -> SensorHealth.HELD
        values.any { it.availability == SourceCandidateAvailability.STALE } -> SensorHealth.STALE
        else -> SensorHealth.UNAVAILABLE
    }

    private fun overallHealth(sensors: List<BoatSensorState>): SensorHealth = when {
        sensors.any { it.health == SensorHealth.NEEDS_ATTENTION } -> SensorHealth.NEEDS_ATTENTION
        sensors.any { it.health == SensorHealth.LIVE } -> SensorHealth.LIVE
        sensors.any { it.health == SensorHealth.HELD } -> SensorHealth.HELD
        sensors.any { it.health == SensorHealth.STALE } -> SensorHealth.STALE
        else -> SensorHealth.UNAVAILABLE
    }

    private val ATTENTION_STATUSES = setOf(
        SourceGroupStatus.NEEDS_SELECTION,
        SourceGroupStatus.SELECTED_UNAVAILABLE,
        SourceGroupStatus.MIXED_LEGACY_SELECTION,
    )
    private val IMPACTING_HEALTH = setOf(
        SensorHealth.STALE,
        SensorHealth.UNAVAILABLE,
        SensorHealth.NEEDS_ATTENTION,
    )
}

internal val BoatSensor.sourceGroups: Set<SourceGroup>
    get() = when (this) {
        BoatSensor.POSITION -> setOf(SourceGroup.POSITION_AND_MOTION)
        BoatSensor.HEADING -> setOf(SourceGroup.HEADING)
        BoatSensor.DEPTH -> setOf(SourceGroup.DEPTH)
        BoatSensor.WIND -> setOf(SourceGroup.APPARENT_WIND, SourceGroup.TRUE_WIND)
    }

internal fun sensorFor(key: DataKey): BoatSensor? = BoatSensor.entries.firstOrNull { sensor ->
    sensor.sourceGroups.any { key in it.keys }
}
