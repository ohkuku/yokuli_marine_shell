package com.yokuli.marine.feature.data

import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.SourceIdentity
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
}

enum class BoatSensor {
    POSITION,
    HEADING,
    DEPTH,
    WIND,
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

    private val BoatSensor.sourceGroups: Set<SourceGroup>
        get() = when (this) {
            BoatSensor.POSITION -> setOf(SourceGroup.POSITION_AND_MOTION)
            BoatSensor.HEADING -> setOf(SourceGroup.HEADING)
            BoatSensor.DEPTH -> setOf(SourceGroup.DEPTH)
            BoatSensor.WIND -> setOf(SourceGroup.APPARENT_WIND, SourceGroup.TRUE_WIND)
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
