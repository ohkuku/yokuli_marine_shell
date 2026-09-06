package com.yokuli.marine.data.catalog

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ChecksumTrust
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.DepthReference
import com.yokuli.marine.data.model.HeadingReference
import com.yokuli.marine.data.model.MarineObservation
import com.yokuli.marine.data.model.ObservationValidity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.model.WindReference
import com.yokuli.marine.data.session.ActiveSessionRegistry
import com.yokuli.marine.data.time.MonotonicClock

fun interface SentencePriorityRules {
    /** Lower values win. Equal ranks are resolved deterministically, never by map iteration order. */
    fun rank(key: DataKey, formatter: String): Int
}

/**
 * An explicit operational merge rule, not a claim that one formatter is inherently more accurate.
 * Freshness tier wins first; these ranks are used only inside one tier.
 */
object DefaultSentencePriorityRules : SentencePriorityRules {
    override fun rank(key: DataKey, formatter: String): Int {
        val order = when (key) {
            DataKey.Position -> listOf("GGA", "RMC", "GLL")
            DataKey.SpeedOverGround,
            DataKey.CourseOverGround,
            -> listOf("VTG", "RMC")
            DataKey.SourceTime -> listOf("ZDA", "RMC")
            DataKey.FixQuality,
            DataKey.Satellites,
            DataKey.HorizontalDilution,
            DataKey.Altitude,
            -> listOf("GGA")
            DataKey.MagneticVariation -> listOf("HDG", "RMC")
            is DataKey.Heading -> when (key.reference) {
                HeadingReference.TRUE -> listOf("HDT", "HDG")
                HeadingReference.MAGNETIC -> listOf("HDM", "HDG")
            }
            is DataKey.Depth -> when (key.reference) {
                DepthReference.BELOW_TRANSDUCER -> listOf("DPT", "DBT")
                DepthReference.BELOW_SURFACE -> listOf("DBT", "DPT")
                DepthReference.BELOW_KEEL -> listOf("DPT", "DBT")
            }
            is DataKey.WindAngle -> when (key.reference) {
                WindReference.APPARENT,
                WindReference.TRUE_RELATIVE,
                -> listOf("MWV", "MWD")
                WindReference.TRUE_NORTH,
                WindReference.MAGNETIC_NORTH,
                -> listOf("MWD", "MWV")
            }
            is DataKey.WindSpeed -> listOf("MWV", "MWD")
        }
        val index = order.indexOf(formatter)
        return if (index >= 0) index else UNKNOWN_FORMATTER_RANK
    }

    private const val UNKNOWN_FORMATTER_RANK = 10_000
}

data class ObservationCandidate(
    val id: CandidateId,
    val sessionGeneration: SessionGeneration,
    val selectedObservation: MarineObservation,
    val lastValidObservation: MarineObservation?,
    val freshness: FreshnessEvaluation,
    val contributingFormatters: Set<String>,
    val contributingSentenceIds: Set<String>,
    val sourceAvailable: Boolean,
    val lastSeenMillis: Long,
)

data class ObservationCatalogSnapshot(
    val candidates: List<ObservationCandidate>,
    val evaluatedAtMillis: Long,
    val capacityEvictionCount: Long,
    val capacityRejectionCount: Long,
    val staleSessionDropCount: Long,
    val outOfOrderDropCount: Long,
)

/**
 * Retains bounded candidates before global source selection. A candidate is one normalized key and
 * stable source. Complementary sentence streams remain separate evidence inside that candidate.
 */
class ObservationCatalog(
    clock: MonotonicClock,
    private val sessionRegistry: ActiveSessionRegistry,
    private val maxCandidates: Int = DEFAULT_MAX_CANDIDATES,
    private val priorityRules: SentencePriorityRules = DefaultSentencePriorityRules,
    thresholds: FreshnessThresholds = FreshnessThresholds(),
) {
    init {
        require(maxCandidates > 0) { "Observation candidate capacity must be positive" }
    }

    private val clock = clock
    private val freshnessPolicy = FreshnessPolicy(clock, thresholds)
    private val candidates = linkedMapOf<CandidateId, CandidateState>()
    private var touchSequence = 0L
    private var capacityEvictionCount = 0L
    private var capacityRejectionCount = 0L
    private var staleSessionDropCount = 0L
    private var outOfOrderDropCount = 0L

    @Synchronized
    fun record(
        observation: MarineObservation,
        protectedCandidates: Set<CandidateId> = emptySet(),
    ): ObservationCatalogSnapshot {
        val generation = observation.origin.sessionGeneration
        if (!sessionRegistry.acceptInbound(observation.origin.source.connectionId, generation)) {
            staleSessionDropCount++
            return snapshotLocked()
        }

        val id = CandidateId(observation.key, observation.origin.source)
        val current = candidates[id]
        if (current == null && candidates.size >= maxCandidates && !makeRoom(protectedCandidates)) {
            capacityRejectionCount++
            return snapshotLocked()
        }
        val base = if (current == null || current.sessionGeneration != generation) {
            CandidateState(
                sessionGeneration = generation,
                streams = emptyMap(),
                lastTouched = nextTouch(),
            )
        } else {
            current
        }

        val streamKey = ObservationStreamKey(
            talker = observation.origin.talker,
            formatter = observation.origin.formatter,
        )
        val currentStream = base.streams[streamKey]
        if (currentStream != null && observation.isOlderThan(currentStream.current)) {
            outOfOrderDropCount++
            return snapshotLocked()
        }

        val stream = ObservationStream(
            current = observation,
            lastValid = if (observation.validity == ObservationValidity.VALID) {
                observation
            } else {
                currentStream?.lastValid
            },
        )
        candidates[id] = base.copy(
            streams = base.streams + (streamKey to stream),
            lastTouched = nextTouch(),
        )
        return snapshotLocked()
    }

    /** Compatibility helper; the shared registry remains the only source-availability authority. */
    @Synchronized
    fun markSourceUnavailable(
        source: SourceIdentity,
        sessionGeneration: SessionGeneration,
    ): ObservationCatalogSnapshot {
        sessionRegistry.endSession(source.connectionId, sessionGeneration)
        return snapshotLocked()
    }

    /** Re-evaluates age and current-session truth even when no packet has arrived. */
    @Synchronized
    fun snapshot(): ObservationCatalogSnapshot = snapshotLocked()

    private fun snapshotLocked(): ObservationCatalogSnapshot {
        val now = clock.nowMillis()
        require(now >= 0L) { "Monotonic time must be non-negative" }
        return ObservationCatalogSnapshot(
            candidates = candidates.map { (id, state) -> state.toSnapshot(id, now) }
                .sortedBy { it.id.stableOrder() },
            evaluatedAtMillis = now,
            capacityEvictionCount = capacityEvictionCount,
            capacityRejectionCount = capacityRejectionCount,
            staleSessionDropCount = staleSessionDropCount,
            outOfOrderDropCount = outOfOrderDropCount,
        )
    }

    private fun CandidateState.toSnapshot(id: CandidateId, now: Long): ObservationCandidate {
        val sourceAvailable = sessionRegistry.isCurrent(id.source.connectionId, sessionGeneration)
        val evaluated = streams.values.map { stream ->
            EvaluatedStream(
                stream = stream,
                freshness = freshnessPolicy.evaluateAt(stream.current, sourceAvailable = true, nowMillis = now),
            )
        }
        val latestInvalid = evaluated
            .asSequence()
            .filter { it.stream.current.validity == ObservationValidity.EXPLICIT_INVALID }
            .maxWithOrNull(evaluatedObservationOrderComparator)
        val latestValid = evaluated
            .asSequence()
            .filter { it.stream.current.validity == ObservationValidity.VALID }
            .maxWithOrNull(evaluatedObservationOrderComparator)

        val selected = if (
            latestInvalid != null &&
            (latestValid == null || evaluatedObservationOrderComparator.compare(latestInvalid, latestValid) >= 0)
        ) {
            evaluated
                .asSequence()
                .filter {
                    it.stream.current.validity == ObservationValidity.EXPLICIT_INVALID &&
                        evaluatedObservationOrderComparator.compare(it, latestInvalid) == 0
                }
                .minWithOrNull(invalidComparator(id.key))
                ?: error("A newest explicit invalid observation must exist")
        } else {
            val eligibleValid = evaluated.filter {
                it.stream.current.validity == ObservationValidity.VALID &&
                    (latestInvalid == null || evaluatedObservationOrderComparator.compare(it, latestInvalid) > 0)
            }
            val live = eligibleValid.filter { it.freshness.state == Freshness.LIVE }
            val held = eligibleValid.filter { it.freshness.state == Freshness.HELD }
            val stale = eligibleValid.filter { it.freshness.state == Freshness.STALE }
            val unavailable = eligibleValid.filter { it.freshness.state == Freshness.UNAVAILABLE }
            (live.ifEmpty { held }.ifEmpty { stale }.ifEmpty { unavailable })
                .minWithOrNull(streamComparator(id.key))
                ?: error("A candidate must contain valid or explicitly invalid evidence")
        }
        val lastValid = streams.values
            .mapNotNull { it.lastValid }
            .maxWithOrNull(lastValidComparator(id.key))

        return ObservationCandidate(
            id = id,
            sessionGeneration = sessionGeneration,
            selectedObservation = selected.stream.current,
            lastValidObservation = lastValid,
            freshness = freshnessPolicy.evaluateAt(
                selected.stream.current,
                sourceAvailable = sourceAvailable,
                nowMillis = now,
            ),
            contributingFormatters = streams.values.map { it.current.origin.formatter }.toSet(),
            contributingSentenceIds = streams.values.map { it.current.origin.sentenceId }.toSet(),
            sourceAvailable = sourceAvailable,
            lastSeenMillis = streams.values.maxOf { it.current.measuredAtMillis },
        )
    }

    private fun streamComparator(key: DataKey) =
        compareBy<EvaluatedStream> { priorityRules.rank(key, it.stream.current.origin.formatter) }
            .thenBy { if (it.stream.current.checksumTrust == ChecksumTrust.VERIFIED) 0 else 1 }
            .thenBy { it.stream.current.origin.formatter }
            .thenBy { it.stream.current.origin.talker }
            .thenByDescending { it.stream.current.measuredAtMillis }
            .thenByDescending { it.stream.current.groupId.frameSequence }

    private fun invalidComparator(key: DataKey) =
        compareByDescending<EvaluatedStream> { it.stream.current.groupId.frameSequence }
            .thenBy { priorityRules.rank(key, it.stream.current.origin.formatter) }
            .thenBy { if (it.stream.current.checksumTrust == ChecksumTrust.VERIFIED) 0 else 1 }
            .thenBy { it.stream.current.origin.formatter }
            .thenBy { it.stream.current.origin.talker }

    private fun lastValidComparator(key: DataKey) =
        compareBy<MarineObservation> { it.measuredAtMillis }
            .thenBy { it.groupId.frameSequence }
            .thenByDescending { priorityRules.rank(key, it.origin.formatter) }
            .thenByDescending { if (it.checksumTrust == ChecksumTrust.VERIFIED) 0 else 1 }
            .thenByDescending { it.origin.formatter }
            .thenByDescending { it.origin.talker }

    private fun makeRoom(protectedCandidates: Set<CandidateId>): Boolean {
        val eviction = candidates.entries
            .asSequence()
            .filterNot { it.key in protectedCandidates }
            .minWithOrNull(compareBy<Map.Entry<CandidateId, CandidateState>> { it.value.lastTouched }.thenBy { it.key.stableOrder() })
            ?: return false
        candidates.remove(eviction.key)
        capacityEvictionCount++
        return true
    }

    private fun nextTouch(): Long {
        touchSequence = Math.addExact(touchSequence, 1L)
        return touchSequence
    }

    companion object {
        const val DEFAULT_MAX_CANDIDATES = 256
    }
}

private data class CandidateState(
    val sessionGeneration: SessionGeneration,
    val streams: Map<ObservationStreamKey, ObservationStream>,
    val lastTouched: Long,
)

private data class ObservationStreamKey(
    val talker: String,
    val formatter: String,
)

private data class ObservationStream(
    val current: MarineObservation,
    val lastValid: MarineObservation?,
)

private data class EvaluatedStream(
    val stream: ObservationStream,
    val freshness: FreshnessEvaluation,
)

private val evaluatedObservationOrderComparator =
    compareBy<EvaluatedStream> { it.stream.current.measuredAtMillis }
        .thenBy { it.stream.current.groupId.frameSequence }

private fun MarineObservation.isOlderThan(other: MarineObservation): Boolean =
    measuredAtMillis < other.measuredAtMillis ||
        (measuredAtMillis == other.measuredAtMillis && groupId.frameSequence < other.groupId.frameSequence)

private fun CandidateId.stableOrder(): String = buildString {
    append(key.toString())
    append('|')
    append(source.stableOrder())
}
