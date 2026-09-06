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
    val retainedStreamCount: Int,
)

data class ObservationCatalogSnapshot(
    val candidates: List<ObservationCandidate>,
    val evaluatedAtMillis: Long,
    val capacityEvictionCount: Long,
    val capacityRejectionCount: Long,
    val staleSessionDropCount: Long,
    val outOfOrderDropCount: Long,
    val streamCapacityEvictionCount: Long,
    val streamCapacityRejectionCount: Long,
    val streamHighWaterMark: Int,
)

/**
 * Retains bounded candidates before global source selection. A candidate is one normalized key and
 * stable source. Complementary sentence streams remain separate evidence inside that candidate.
 */
class ObservationCatalog(
    clock: MonotonicClock,
    private val sessionRegistry: ActiveSessionRegistry,
    private val maxCandidates: Int = DEFAULT_MAX_CANDIDATES,
    private val maxStreamsPerCandidate: Int = MAX_STREAMS_PER_CANDIDATE,
    private val priorityRules: SentencePriorityRules = DefaultSentencePriorityRules,
    thresholds: FreshnessThresholds = FreshnessThresholds(),
) {
    init {
        require(maxCandidates > 0) { "Observation candidate capacity must be positive" }
        require(maxStreamsPerCandidate > 0) { "Candidate stream capacity must be positive" }
    }

    private val clock = clock
    private val freshnessPolicy = FreshnessPolicy(clock, thresholds)
    private val candidates = linkedMapOf<CandidateId, CandidateState>()
    private var touchSequence = 0L
    private var capacityEvictionCount = 0L
    private var capacityRejectionCount = 0L
    private var staleSessionDropCount = 0L
    private var outOfOrderDropCount = 0L
    private var streamCapacityEvictionCount = 0L
    private var streamCapacityRejectionCount = 0L
    private var streamHighWaterMark = 0

    @Synchronized
    fun record(
        observation: MarineObservation,
        protectedCandidates: Set<CandidateId> = emptySet(),
    ): ObservationCatalogSnapshot {
        val generation = observation.origin.sessionGeneration
        val accepted = sessionRegistry.commitInbound(observation.origin.source.connectionId, generation) {
            recordAuthorized(observation, protectedCandidates)
        }
        if (!accepted) {
            staleSessionDropCount++
        }
        return snapshotLocked()
    }

    private fun recordAuthorized(
        observation: MarineObservation,
        protectedCandidates: Set<CandidateId>,
    ) {
        val generation = observation.origin.sessionGeneration
        val id = CandidateId(observation.key, observation.origin.source)
        val current = candidates[id]
        if (current == null && candidates.size >= maxCandidates && !makeRoom(protectedCandidates)) {
            capacityRejectionCount++
            return
        }
        val base = if (current == null || current.sessionGeneration != generation) {
            CandidateState(
                sessionGeneration = generation,
                streams = emptyMap(),
                latestValidObservation = null,
                latestInvalidObservation = null,
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
            return
        }

        var retainedStreams = base.streams
        if (currentStream == null && retainedStreams.size >= maxStreamsPerCandidate) {
            val eviction = retainedStreams.entries.minWithOrNull(streamEvictionComparator)
                ?: error("A full candidate must contain an evictable stream")
            if (compareObservationOrder(observation, eviction.value.current) <= 0) {
                streamCapacityRejectionCount++
                return
            }
            retainedStreams = retainedStreams - eviction.key
            streamCapacityEvictionCount++
        }

        retainedStreams = retainedStreams + (streamKey to ObservationStream(observation))
        streamHighWaterMark = maxOf(streamHighWaterMark, retainedStreams.size)
        candidates[id] = base.copy(
            streams = retainedStreams,
            latestValidObservation = if (observation.validity == ObservationValidity.VALID) {
                preferredLatest(base.latestValidObservation, observation, id.key)
            } else {
                base.latestValidObservation
            },
            latestInvalidObservation = if (observation.validity == ObservationValidity.EXPLICIT_INVALID) {
                preferredLatest(base.latestInvalidObservation, observation, id.key)
            } else {
                base.latestInvalidObservation
            },
            lastTouched = nextTouch(),
        )
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
            streamCapacityEvictionCount = streamCapacityEvictionCount,
            streamCapacityRejectionCount = streamCapacityRejectionCount,
            streamHighWaterMark = streamHighWaterMark,
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
        val eligibleValid = evaluated.filter {
            it.stream.current.validity == ObservationValidity.VALID &&
                (
                    latestInvalidObservation == null ||
                        compareObservationOrder(it.stream.current, latestInvalidObservation) > 0
                    )
        }
        val selectedObservation = if (eligibleValid.isNotEmpty()) {
            val live = eligibleValid.filter { it.freshness.state == Freshness.LIVE }
            val held = eligibleValid.filter { it.freshness.state == Freshness.HELD }
            val stale = eligibleValid.filter { it.freshness.state == Freshness.STALE }
            val unavailable = eligibleValid.filter { it.freshness.state == Freshness.UNAVAILABLE }
            (live.ifEmpty { held }.ifEmpty { stale }.ifEmpty { unavailable })
                .minWithOrNull(streamComparator(id.key))
                ?.stream
                ?.current
                ?: error("Eligible valid evidence must be selectable")
        } else {
            latestInvalidObservation
                ?: error("A candidate must contain valid or explicitly invalid evidence")
        }

        return ObservationCandidate(
            id = id,
            sessionGeneration = sessionGeneration,
            selectedObservation = selectedObservation,
            lastValidObservation = latestValidObservation,
            freshness = freshnessPolicy.evaluateAt(
                selectedObservation,
                sourceAvailable = sourceAvailable,
                nowMillis = now,
            ),
            contributingFormatters = streams.values.map { it.current.origin.formatter }.toSet(),
            contributingSentenceIds = streams.values.map { it.current.origin.sentenceId }.toSet(),
            sourceAvailable = sourceAvailable,
            lastSeenMillis = streams.values.maxOf { it.current.measuredAtMillis },
            retainedStreamCount = streams.size,
        )
    }

    private fun streamComparator(key: DataKey) =
        compareBy<EvaluatedStream> { priorityRules.rank(key, it.stream.current.origin.formatter) }
            .thenBy { if (it.stream.current.checksumTrust == ChecksumTrust.VERIFIED) 0 else 1 }
            .thenBy { it.stream.current.origin.formatter }
            .thenBy { it.stream.current.origin.talker }
            .thenByDescending { it.stream.current.measuredAtMillis }
            .thenByDescending { it.stream.current.groupId.frameSequence }

    private fun preferredLatest(
        current: MarineObservation?,
        incoming: MarineObservation,
        key: DataKey,
    ): MarineObservation {
        if (current == null) return incoming
        val order = compareObservationOrder(incoming, current)
        if (order != 0) return if (order > 0) incoming else current
        return if (observationTieComparator(key).compare(incoming, current) < 0) incoming else current
    }

    private fun observationTieComparator(key: DataKey) =
        compareBy<MarineObservation> { priorityRules.rank(key, it.origin.formatter) }
            .thenBy { if (it.checksumTrust == ChecksumTrust.VERIFIED) 0 else 1 }
            .thenBy { it.origin.formatter }
            .thenBy { it.origin.talker }

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
        const val MAX_STREAMS_PER_CANDIDATE = 32
    }
}

private data class CandidateState(
    val sessionGeneration: SessionGeneration,
    val streams: Map<ObservationStreamKey, ObservationStream>,
    val latestValidObservation: MarineObservation?,
    val latestInvalidObservation: MarineObservation?,
    val lastTouched: Long,
)

private data class ObservationStreamKey(
    val talker: String,
    val formatter: String,
)

private data class ObservationStream(
    val current: MarineObservation,
)

private data class EvaluatedStream(
    val stream: ObservationStream,
    val freshness: FreshnessEvaluation,
)

private val streamEvictionComparator =
    compareBy<Map.Entry<ObservationStreamKey, ObservationStream>> { it.value.current.measuredAtMillis }
        .thenBy { it.value.current.groupId.frameSequence }
        .thenBy { it.key.stableOrder() }

private fun compareObservationOrder(first: MarineObservation, second: MarineObservation): Int {
    val timestamp = first.measuredAtMillis.compareTo(second.measuredAtMillis)
    return if (timestamp != 0) timestamp else first.groupId.frameSequence.compareTo(second.groupId.frameSequence)
}

private fun MarineObservation.isOlderThan(other: MarineObservation): Boolean =
    compareObservationOrder(this, other) < 0

private fun ObservationStreamKey.stableOrder(): String = "$talker|$formatter"

private fun CandidateId.stableOrder(): String = buildString {
    append(key.toString())
    append('|')
    append(source.stableOrder())
}
