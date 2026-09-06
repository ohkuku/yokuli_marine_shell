package com.yokuli.marine.data.runtime

import com.yokuli.marine.data.catalog.ObservationCatalog
import com.yokuli.marine.data.catalog.RawPreviewBuffer
import com.yokuli.marine.data.catalog.RawPreviewEntry
import com.yokuli.marine.data.catalog.SentenceCatalog
import com.yokuli.marine.data.catalog.SentenceCatalogEvent
import com.yokuli.marine.data.catalog.SentenceParseStatus
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SenderIdentity
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.nmea.Nmea0183Parser
import com.yokuli.marine.data.nmea.NmeaParseContext
import com.yokuli.marine.data.nmea.NmeaParseResult
import com.yokuli.marine.data.session.ActiveSessionRegistry
import com.yokuli.marine.data.time.MonotonicClock
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

data class NmeaInboundFrame(
    val source: SourceIdentity,
    val sessionToken: SessionToken,
    val receivedAtMillis: Long,
    val groupId: ObservationGroupId,
    val raw: String,
    val sender: SenderIdentity? = null,
    /** Byte-counter contribution; zero is valid when the transport already counted its chunk. */
    val receivedByteCount: Int = raw.toByteArray(StandardCharsets.UTF_8).size,
) {
    init {
        require(source.connectionId == sessionToken.connectionId) {
            "Inbound source and session token must identify the same connection"
        }
        require(receivedAtMillis >= 0L) { "Inbound time must be monotonic and non-negative" }
        require(receivedByteCount >= 0) { "Inbound byte count must be non-negative" }
        source.udpOrigin?.let { identity ->
            val observed = requireNotNull(sender) { "A UDP source requires observed sender provenance" }
            require(observed.hostAddress == identity.hostAddress) {
                "UDP source host and observed sender host must match"
            }
            require(identity.port == null || observed.port == identity.port) {
                "UDP source port and observed sender port must match"
            }
        }
        require(source.udpOrigin != null || sender == null) {
            "Observed sender provenance requires a UDP source identity"
        }
    }
}

/**
 * Synchronous, bounded ingress transaction. Transport actors may enqueue frames, but all admitted
 * parser, counter and catalog changes cross the active-session boundary together here.
 */
class NmeaInboundPipeline(
    private val clock: MonotonicClock,
    private val sessionRegistry: ActiveSessionRegistry,
    initialConnections: List<ConnectionRuntimeSnapshot> = emptyList(),
    private val inputHealthPolicy: InputHealthPolicy = InputHealthPolicy(),
    private val maxIncidents: Int = MAX_STRUCTURED_INCIDENTS,
) {
    init {
        require(maxIncidents > 0 && maxIncidents <= MAX_STRUCTURED_INCIDENTS) {
            "Pipeline incident capacity is outside the runtime bound"
        }
        require(initialConnections.size <= com.yokuli.marine.data.connection.MAX_NMEA_CONNECTIONS) {
            "Initial connections exceed the runtime bound"
        }
        require(initialConnections.map { it.stored.config.id }.distinct().size == initialConnections.size) {
            "Initial connection identities must be unique"
        }
    }

    private val connections = initialConnections.associateByTo(linkedMapOf()) { it.stored.config.id }
    private val rateWindows = mutableMapOf<com.yokuli.marine.data.model.ConnectionId, FiveSecondFrameRateWindow>()
    private val sentenceCatalog = SentenceCatalog(sessionRegistry)
    private val observationCatalog = ObservationCatalog(clock, sessionRegistry)
    private val rawPreview = RawPreviewBuffer(sessionRegistry)
    private val incidents = ArrayDeque<NmeaRuntimeIncident>()
    private var revision = 0L

    /** Adds or replaces runtime facts. The process actor must call this before admitting a session. */
    @Synchronized
    fun upsertConnection(connection: ConnectionRuntimeSnapshot): Boolean {
        val id = connection.stored.config.id
        if (id !in connections && connections.size >= com.yokuli.marine.data.connection.MAX_NMEA_CONNECTIONS) {
            return false
        }
        val previous = connections[id]
        val accepted = if (connection.token != null && previous?.token != connection.token) {
            connection.copy(
                input = ConnectionInputState.NO_BYTES,
                metrics = connection.metrics.withSessionEvidenceCleared(),
            )
        } else {
            connection
        }
        connections[id] = accepted
        if (previous?.token != connection.token) {
            rateWindows.remove(id)
        }
        revision = saturatingAdd(revision, 1L)
        return true
    }

    /** Removes runtime facts only; the owner must end/forget the session in the registry explicitly. */
    @Synchronized
    fun removeConnection(connectionId: com.yokuli.marine.data.model.ConnectionId): Boolean {
        val removed = connections.remove(connectionId) ?: return false
        rateWindows.remove(connectionId)
        revision = saturatingAdd(revision, 1L)
        return removed.stored.config.id == connectionId
    }

    @Synchronized
    fun recordIncident(incident: NmeaRuntimeIncident) {
        while (incidents.size >= maxIncidents) incidents.removeFirst()
        incidents.addLast(incident)
        revision = saturatingAdd(revision, 1L)
    }

    /** Returns true only when the exact active generation and config revision admitted the frame. */
    @Synchronized
    fun accept(frame: NmeaInboundFrame): Boolean {
        val id = frame.sessionToken.connectionId
        val current = connections[id]
        if (current == null) {
            appendIncident(
                NmeaRuntimeIncident(
                    connectionId = id,
                    sessionToken = frame.sessionToken,
                    failure = NmeaRuntimeFailure.ConnectionNotFound,
                    occurredAtMillis = frame.receivedAtMillis,
                ),
            )
            return false
        }
        if (current.stored.revision != frame.sessionToken.configRevision) {
            rejectStale(current, frame)
            return false
        }
        if (
            current.token != null &&
            frame.sessionToken.generation.value < current.token.generation.value
        ) {
            rejectStale(current, frame)
            return false
        }
        if (!originMatchesEndpoint(current, frame)) {
            appendIncident(
                NmeaRuntimeIncident(
                    connectionId = id,
                    sessionToken = frame.sessionToken,
                    failure = NmeaRuntimeFailure.InternalError,
                    occurredAtMillis = frame.receivedAtMillis,
                ),
            )
            return false
        }

        val parsed = Nmea0183Parser(current.stored.config.checksumPolicy).parse(
            rawFrame = frame.raw,
            context = NmeaParseContext(
                source = frame.source,
                sessionGeneration = frame.sessionToken.generation,
                receivedAtMonotonicMillis = frame.receivedAtMillis,
                observationGroupId = frame.groupId,
                sender = frame.sender,
            ),
        )
        val admitted = sessionRegistry.commitInbound(id, frame.sessionToken.generation) {
            commitAdmitted(current, frame, parsed)
        }
        if (!admitted) rejectStale(connections.getValue(id), frame)
        return admitted
    }

    @Synchronized
    fun snapshot(): NmeaRuntimeSnapshot {
        val now = clock.nowMillis()
        require(now >= 0L) { "Monotonic time must be non-negative" }
        projectTime(now)
        return NmeaRuntimeSnapshot(
            connections = connections.values.sortedBy { it.stored.config.id.value },
            sentenceCatalog = sentenceCatalog.snapshot(),
            observationCatalog = observationCatalog.snapshot(),
            rawPreview = rawPreview.snapshot(),
            incidents = incidents.toList(),
            activeSocketCount = connections.values.count { it.transport.hasOpenSocket },
            activeTaskCount = connections.values.count { it.transport.hasRuntimeTask },
            revision = revision,
        )
    }

    private fun commitAdmitted(
        previous: ConnectionRuntimeSnapshot,
        frame: NmeaInboundFrame,
        parsed: NmeaParseResult,
    ) {
        val id = frame.sessionToken.connectionId
        val sessionChanged = previous.token != frame.sessionToken
        val base = if (sessionChanged) previous.metrics.withSessionEvidenceCleared() else previous.metrics
        val rateWindow = rateWindows.getOrPut(id, ::FiveSecondFrameRateWindow).also {
            if (sessionChanged) it.clear()
        }

        val beforeRawDrops = rawPreview.snapshot().overflowEntryCount
        val sentenceId = parsed.sentenceOrNull()?.sentenceId
        val rawSnapshot = rawPreview.record(
            RawPreviewEntry(
                source = frame.source,
                sessionGeneration = frame.sessionToken.generation,
                sender = frame.sender,
                sentenceId = sentenceId,
                receivedAtMillis = frame.receivedAtMillis,
                raw = frame.raw,
            ),
        )
        val rawDrops = rawSnapshot.overflowEntryCount - beforeRawDrops

        parsed.sentenceOrNull()?.let { sentence ->
            sentenceCatalog.record(
                SentenceCatalogEvent.fromSentence(sentence, parsed.sentenceParseStatus()),
            )
        }
        parsed.observationsOrEmpty().forEach(observationCatalog::record)

        val legal = parsed.isLegalInput
        if (legal) rateWindow.record(frame.receivedAtMillis)
        val updatedMetrics = base.afterFrame(
            frame = frame,
            parsed = parsed,
            rawDropIncrement = rawDrops,
            validRate = rateWindow.snapshot(frame.receivedAtMillis),
        )
        val evaluatedAtMillis = maxOf(clock.nowMillis(), frame.receivedAtMillis)
        val inputState = inputHealthPolicy.evaluate(
            nowMillis = evaluatedAtMillis,
            lastByteAtMillis = updatedMetrics.lastByteAtMillis,
            lastValidFrameAtMillis = updatedMetrics.lastValidFrameAtMillis,
        )
        val finalMetrics = updatedMetrics.copy(
            inputInterruptedAtMillis = when (inputState) {
                ConnectionInputState.INTERRUPTED ->
                    base.inputInterruptedAtMillis ?: evaluatedAtMillis
                else -> null
            },
        )
        connections[id] = previous.copy(
            token = frame.sessionToken,
            input = inputState,
            metrics = finalMetrics,
        )
        revision = saturatingAdd(revision, 1L)
    }

    private fun rejectStale(current: ConnectionRuntimeSnapshot, frame: NmeaInboundFrame) {
        connections[current.stored.config.id] = current.copy(
            metrics = current.metrics.copy(
                staleGenerationDropCount = saturatingAdd(current.metrics.staleGenerationDropCount, 1L),
            ),
        )
        appendIncident(
            NmeaRuntimeIncident(
                connectionId = current.stored.config.id,
                sessionToken = frame.sessionToken,
                failure = NmeaRuntimeFailure.StaleSession,
                occurredAtMillis = frame.receivedAtMillis,
            ),
        )
    }

    private fun appendIncident(incident: NmeaRuntimeIncident) {
        while (incidents.size >= maxIncidents) incidents.removeFirst()
        incidents.addLast(incident)
        revision = saturatingAdd(revision, 1L)
    }

    private fun projectTime(now: Long) {
        connections.replaceAll { id, connection ->
            val rate = rateWindows[id]?.snapshot(now) ?: FiveSecondFrameRate.EMPTY
            val state = inputHealthPolicy.evaluate(
                nowMillis = now,
                lastByteAtMillis = connection.metrics.lastByteAtMillis,
                lastValidFrameAtMillis = connection.metrics.lastValidFrameAtMillis,
                overloaded = connection.input == ConnectionInputState.OVERLOADED,
            )
            val interruptedAt = if (
                state == ConnectionInputState.INTERRUPTED &&
                connection.input != ConnectionInputState.INTERRUPTED
            ) {
                now
            } else if (state == ConnectionInputState.INTERRUPTED) {
                connection.metrics.inputInterruptedAtMillis
            } else {
                null
            }
            val metrics = connection.metrics.copy(
                validRate = rate,
                inputInterruptedAtMillis = interruptedAt,
            )
            if (state == connection.input && metrics == connection.metrics) {
                connection
            } else {
                revision = saturatingAdd(revision, 1L)
                connection.copy(input = state, metrics = metrics)
            }
        }
    }

    private fun originMatchesEndpoint(
        connection: ConnectionRuntimeSnapshot,
        frame: NmeaInboundFrame,
    ): Boolean = when (val endpoint = connection.stored.config.endpoint) {
        is NmeaEndpoint.TcpClient -> frame.source.udpOrigin == null && frame.sender == null
        is NmeaEndpoint.UdpListener -> {
            val identity = frame.source.udpOrigin ?: return false
            val sender = frame.sender ?: return false
            val identityMatchesPolicy = when (endpoint.originIdentityPolicy) {
                com.yokuli.marine.data.model.UdpOriginIdentityPolicy.HOST_ADDRESS -> identity.port == null
                com.yokuli.marine.data.model.UdpOriginIdentityPolicy.HOST_AND_PORT -> identity.port == sender.port
            }
            // The Android adapter resolves and enforces an optional host filter. Re-comparing its
            // configured hostname to an observed numeric address here would reject valid DNS aliases.
            identityMatchesPolicy
        }
    }
}

private fun NmeaConnectionDiagnostics.withSessionEvidenceCleared(): NmeaConnectionDiagnostics = copy(
    validRate = FiveSecondFrameRate.EMPTY,
    lastByteAtMillis = null,
    lastValidFrameAtMillis = null,
    inputInterruptedAtMillis = null,
    tcpBufferedByteCount = 0,
    pendingIngressFrameCount = 0,
)

private fun NmeaConnectionDiagnostics.afterFrame(
    frame: NmeaInboundFrame,
    parsed: NmeaParseResult,
    rawDropIncrement: Long,
    validRate: FiveSecondFrameRate,
): NmeaConnectionDiagnostics {
    val observations = parsed.observationsOrEmpty()
    val senderSet = frame.sender?.let { sender ->
        if (sender in observedUdpSenders || observedUdpSenders.size < MAX_OBSERVED_UDP_SENDERS_PER_CONNECTION) {
            observedUdpSenders + sender
        } else {
            observedUdpSenders
        }
    } ?: observedUdpSenders
    return copy(
        byteCount = saturatingAdd(byteCount, frame.receivedByteCount.toLong()),
        frameCount = saturatingAdd(frameCount, 1L),
        legalFrameCount = saturatingAdd(legalFrameCount, if (parsed.isLegalInput) 1L else 0L),
        parsedFrameCount = saturatingAdd(parsedFrameCount, if (parsed is NmeaParseResult.Parsed) 1L else 0L),
        parsedObservationCount = saturatingAdd(parsedObservationCount, observations.size.toLong()),
        positionFrameCount = saturatingAdd(
            positionFrameCount,
            if (observations.any { it.key == DataKey.Position }) 1L else 0L,
        ),
        explicitInvalidFrameCount = saturatingAdd(
            explicitInvalidFrameCount,
            if (parsed is NmeaParseResult.ExplicitInvalid) 1L else 0L,
        ),
        unsupportedFrameCount = saturatingAdd(
            unsupportedFrameCount,
            if (parsed is NmeaParseResult.Unsupported) 1L else 0L,
        ),
        checksumFailureCount = saturatingAdd(
            checksumFailureCount,
            if (parsed is NmeaParseResult.ChecksumFailure) 1L else 0L,
        ),
        malformedFrameCount = saturatingAdd(
            malformedFrameCount,
            if (parsed is NmeaParseResult.Malformed) 1L else 0L,
        ),
        rawPreviewDropCount = saturatingAdd(rawPreviewDropCount, rawDropIncrement.coerceAtLeast(0L)),
        observedUdpSenders = senderSet,
        validRate = validRate,
        lastByteAtMillis = maxNullable(lastByteAtMillis, frame.receivedAtMillis),
        lastValidFrameAtMillis = if (parsed.isLegalInput) {
            maxNullable(lastValidFrameAtMillis, frame.receivedAtMillis)
        } else {
            lastValidFrameAtMillis
        },
    )
}

private val NmeaParseResult.isLegalInput: Boolean
    get() = this is NmeaParseResult.Parsed ||
        this is NmeaParseResult.ExplicitInvalid ||
        this is NmeaParseResult.Unsupported

private fun NmeaParseResult.sentenceOrNull() = when (this) {
    is NmeaParseResult.Parsed -> sentence
    is NmeaParseResult.ExplicitInvalid -> sentence
    is NmeaParseResult.Unsupported -> sentence
    is NmeaParseResult.ChecksumFailure,
    is NmeaParseResult.Malformed,
    -> null
}

private fun NmeaParseResult.observationsOrEmpty() = when (this) {
    is NmeaParseResult.Parsed -> observations
    is NmeaParseResult.ExplicitInvalid -> observations
    is NmeaParseResult.Unsupported,
    is NmeaParseResult.ChecksumFailure,
    is NmeaParseResult.Malformed,
    -> emptyList()
}

private fun NmeaParseResult.sentenceParseStatus(): SentenceParseStatus = when (this) {
    is NmeaParseResult.Parsed -> SentenceParseStatus.PARSED
    is NmeaParseResult.ExplicitInvalid -> SentenceParseStatus.EXPLICIT_INVALID
    is NmeaParseResult.Unsupported -> SentenceParseStatus.UNSUPPORTED
    is NmeaParseResult.ChecksumFailure,
    is NmeaParseResult.Malformed,
    -> error("Diagnostic-only parser results do not enter the sentence catalog")
}

private val ConnectionTransportState.hasOpenSocket: Boolean
    get() = this == ConnectionTransportState.TcpConnected || this == ConnectionTransportState.UdpListening

private val ConnectionTransportState.hasRuntimeTask: Boolean
    get() = this != ConnectionTransportState.Stopped && this != ConnectionTransportState.PlatformStartRequired

private fun maxNullable(current: Long?, incoming: Long): Long =
    current?.let { maxOf(it, incoming) } ?: incoming
