package com.yokuli.marine.data.android.runtime

import com.yokuli.marine.data.android.persistence.ConnectionPersistenceResult
import com.yokuli.marine.data.android.persistence.ProtoDataStoreConnectionRepository
import com.yokuli.marine.data.android.service.ForegroundRuntimeController
import com.yokuli.marine.data.android.transport.NetworkTransportEvent
import com.yokuli.marine.data.android.transport.NmeaTransportFactory
import com.yokuli.marine.data.connection.ConnectionConfigAction
import com.yokuli.marine.data.connection.ConnectionConfigEffect
import com.yokuli.marine.data.connection.ConnectionConfigIssue
import com.yokuli.marine.data.connection.ConnectionConfigReducer
import com.yokuli.marine.data.connection.ConnectionConfigState
import com.yokuli.marine.data.connection.ConnectionRunIntent
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.connection.StoredNmeaConnection
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SenderIdentity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.nmea.NmeaFrameEvent
import com.yokuli.marine.data.nmea.TcpNmeaFramer
import com.yokuli.marine.data.nmea.UdpNmeaDatagramFramer
import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.ConnectionRuntimeSnapshot
import com.yokuli.marine.data.runtime.ConnectionTransportState
import com.yokuli.marine.data.runtime.FiveSecondFrameRate
import com.yokuli.marine.data.runtime.InputHealthPolicy
import com.yokuli.marine.data.runtime.MAX_INGRESS_FRAMES_PER_CONNECTION
import com.yokuli.marine.data.runtime.MAX_OBSERVED_UDP_SENDERS_PER_CONNECTION
import com.yokuli.marine.data.runtime.NmeaConnectionDiagnostics
import com.yokuli.marine.data.runtime.NmeaInboundFrame
import com.yokuli.marine.data.runtime.NmeaInboundPipeline
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.runtime.NmeaRuntimeCommand
import com.yokuli.marine.data.runtime.NmeaRuntimeCommandResult
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.NmeaRuntimeIncident
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.runtime.RetryPolicy
import com.yokuli.marine.data.runtime.SessionToken
import com.yokuli.marine.data.runtime.TransportFailureKind
import com.yokuli.marine.data.session.ActiveSessionRegistry
import com.yokuli.marine.data.time.MonotonicClock
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-owned, serialized NMEA network runtime.
 *
 * Every command, connectivity callback and transport callback crosses [mailbox]. The finite mailbox
 * back-pressures socket reads, so parser/catalog mutation can never race and ingress cannot grow
 * without bound.
 */
class AndroidNmeaInputRuntime(
    private val repository: ProtoDataStoreConnectionRepository,
    private val transportFactory: NmeaTransportFactory,
    private val networkAvailability: NetworkAvailabilityPort,
    private val clock: MonotonicClock,
    private val reconnectDelay: ReconnectDelayPort,
    private val applicationScope: CoroutineScope,
    private val healthTick: HealthTickPort = HealthTickPort.SYSTEM,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val foregroundController: ForegroundRuntimeController = ForegroundRuntimeController.NO_OP,
) : NmeaInputRuntimePort {
    private val sessionRegistry = ActiveSessionRegistry()
    private val pipeline = NmeaInboundPipeline(clock, sessionRegistry)
    private val inputHealthPolicy = InputHealthPolicy()
    private val mutableState = MutableStateFlow(NmeaRuntimeSnapshot.EMPTY)
    override val state: StateFlow<NmeaRuntimeSnapshot> = mutableState.asStateFlow()

    private val mailbox = Channel<RuntimeMessage>(capacity = MAX_INGRESS_FRAMES_PER_CONNECTION)
    private val initialized = CompletableDeferred<Unit>()
    private val sessionJobs = mutableMapOf<ConnectionId, Job>()
    private val retryJobs = mutableMapOf<ConnectionId, Job>()
    private val retryAttempts = mutableMapOf<ConnectionId, Int>()
    private val generations = mutableMapOf<ConnectionId, SessionGeneration>()
    private val tcpFramers = mutableMapOf<SessionToken, TcpNmeaFramer>()
    private val groupSequences = mutableMapOf<SessionToken, Long>()
    private var repositoryAvailable = true
    private var foregroundCount = -1
    private var foregroundPermitted = true

    init {
        applicationScope.launch {
            initialize()
            initialized.complete(Unit)
            for (message in mailbox) {
                handleRuntimeMessageSafely(message)
            }
        }
        applicationScope.launch {
            initialized.await()
            networkAvailability.available.drop(1).collect { available ->
                mailbox.send(RuntimeMessage.NetworkChanged(available))
            }
        }
        applicationScope.launch {
            initialized.await()
            while (currentCoroutineContext().isActive) {
                healthTick.awaitNextTick()
                mailbox.send(RuntimeMessage.HealthTick)
            }
        }
    }

    override suspend fun execute(command: NmeaRuntimeCommand): NmeaRuntimeCommandResult {
        initialized.await()
        val reply = CompletableDeferred<NmeaRuntimeCommandResult>()
        mailbox.send(RuntimeMessage.Command(command, reply))
        return reply.await()
    }

    private suspend fun handleRuntimeMessageSafely(message: RuntimeMessage) {
        if (message is RuntimeMessage.Command) {
            handleCommandSafely(message)
            return
        }
        try {
            when (message) {
                is RuntimeMessage.Command -> error("Command handled above")
                is RuntimeMessage.NetworkChanged -> handleNetworkChanged(message.available)
                is RuntimeMessage.Transport -> handleTransport(message)
                is RuntimeMessage.RetryElapsed -> handleRetryElapsed(message)
                RuntimeMessage.HealthTick -> publish()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            val token = (message as? RuntimeMessage.Transport)?.token
            recordIncident(token?.connectionId, token, NmeaRuntimeFailure.InternalError)
            publish()
        }
    }

    private suspend fun initialize() {
        try {
            repository.awaitLoaded()
            repository.snapshot.value.connections.forEach { stored ->
                pipeline.upsertConnection(stoppedSnapshot(stored))
                generations[stored.config.id] = SessionGeneration(0L)
            }
            publish()
            val enabled = connections().filter { it.stored.runIntent == ConnectionRunIntent.ENABLED }
            if (enabled.isNotEmpty() && !reconcileForeground(force = true)) {
                enabled.forEach { connection ->
                    pipeline.upsertConnection(
                        connection.copy(
                            transport = ConnectionTransportState.PlatformStartRequired,
                            failure = NmeaRuntimeFailure.PlatformRestricted,
                        ),
                    )
                }
                publish()
            } else {
                enabled.forEach { startConnection(it.stored.config.id, foregroundAlreadyChecked = true) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            repositoryAvailable = false
            pipeline.recordIncident(
                NmeaRuntimeIncident(
                    connectionId = null,
                    sessionToken = null,
                    failure = NmeaRuntimeFailure.PersistenceFailed,
                    occurredAtMillis = clock.nowMillis(),
                ),
            )
            publish()
        }
    }

    private suspend fun handleCommandSafely(message: RuntimeMessage.Command) {
        val result = try {
            handleCommand(message.command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            pipeline.recordIncident(
                NmeaRuntimeIncident(
                    connectionId = message.command.connectionIdOrNull(),
                    sessionToken = null,
                    failure = NmeaRuntimeFailure.InternalError,
                    occurredAtMillis = clock.nowMillis(),
                ),
            )
            publish()
            NmeaRuntimeCommandResult.Rejected(NmeaRuntimeFailure.InternalError)
        }
        message.reply.complete(result)
    }

    private suspend fun handleCommand(command: NmeaRuntimeCommand): NmeaRuntimeCommandResult = when (command) {
        is NmeaRuntimeCommand.Save -> save(command.config, command.expectedRevision, command.duplicateTcpConfirmed, false)
        is NmeaRuntimeCommand.SaveAndStart ->
            save(command.config, command.expectedRevision, command.duplicateTcpConfirmed, true)
        is NmeaRuntimeCommand.Start -> start(command.connectionId)
        is NmeaRuntimeCommand.Stop -> stop(command.connectionId)
        is NmeaRuntimeCommand.Retry -> retry(command.connectionId)
        is NmeaRuntimeCommand.Delete -> delete(command.connectionId, command.expectedRevision)
    }

    private suspend fun save(
        config: NmeaConnectionConfig,
        expectedRevision: Long?,
        duplicateTcpConfirmed: Boolean,
        startAfterSave: Boolean,
    ): NmeaRuntimeCommandResult {
        if (!repositoryAvailable) return rejected(NmeaRuntimeFailure.PersistenceFailed)
        val existing = connection(config.id)
        if (expectedRevision != null && existing?.stored?.revision != expectedRevision) {
            return rejected(NmeaRuntimeFailure.PersistenceFailed)
        }
        validate(config, duplicateTcpConfirmed)?.let { return rejected(it) }
        if (startAfterSave) {
            udpConflict(config)?.let { return rejected(it) }
        }

        val targetIntent = if (startAfterSave) ConnectionRunIntent.ENABLED else ConnectionRunIntent.STOPPED_BY_USER
        val changed = existing == null || existing.stored.config != config || existing.stored.runIntent != targetIntent
        val revision = when {
            existing == null -> 0L
            changed -> increment(existing.stored.revision)
            else -> existing.stored.revision
        }
        val stored = StoredNmeaConnection(config, targetIntent, revision)
        if (changed) {
            when (repository.upsert(stored)) {
                is ConnectionPersistenceResult.Saved -> Unit
                is ConnectionPersistenceResult.CapacityExceeded -> {
                    return rejected(
                        NmeaRuntimeFailure.InvalidConfiguration(
                            setOf(ConnectionConfigIssue.CONNECTION_LIMIT_REACHED),
                        ),
                    )
                }
                is ConnectionPersistenceResult.Failed -> return rejected(NmeaRuntimeFailure.PersistenceFailed)
            }
        }

        val replacesSession = existing?.token != null && existing.stored.revision != revision
        if (replacesSession || !startAfterSave) endRuntimeSession(config.id)
        val metrics = when {
            existing == null || existing.stored.config != config -> NmeaConnectionDiagnostics.EMPTY
            existing.token != null && existing.stored.revision != revision -> existing.metrics.clearSessionEvidence()
            else -> existing.metrics
        }
        pipeline.upsertConnection(
            ConnectionRuntimeSnapshot(
                stored = stored,
                token = existing?.token.takeUnless { replacesSession || !startAfterSave },
                transport = existing?.transport.takeUnless { replacesSession || !startAfterSave }
                    ?: ConnectionTransportState.Stopped,
                input = existing?.input.takeUnless { replacesSession || !startAfterSave }
                    ?: ConnectionInputState.NO_BYTES,
                metrics = metrics,
                failure = null,
            ),
        )
        publish()
        if (!startAfterSave) {
            reconcileForeground()
            return success(config.id, revision)
        }

        if (!reconcileForeground()) {
            markPlatformRestricted(config.id)
            return rejected(NmeaRuntimeFailure.PlatformRestricted)
        }
        val startFailure = startConnection(config.id, foregroundAlreadyChecked = true)
        return startFailure?.let(::rejected) ?: success(config.id, revision)
    }

    private suspend fun start(connectionId: ConnectionId): NmeaRuntimeCommandResult {
        val current = connection(connectionId) ?: return rejected(NmeaRuntimeFailure.ConnectionNotFound)
        if (current.stored.runIntent == ConnectionRunIntent.ENABLED && isOwned(connectionId)) {
            return success(connectionId, current.stored.revision)
        }
        val stored = if (current.stored.runIntent == ConnectionRunIntent.ENABLED) {
            current.stored
        } else {
            current.stored.copy(
                runIntent = ConnectionRunIntent.ENABLED,
                revision = increment(current.stored.revision),
            ).also { enabled ->
                if (repository.upsert(enabled) !is ConnectionPersistenceResult.Saved) {
                    return rejected(NmeaRuntimeFailure.PersistenceFailed)
                }
                pipeline.upsertConnection(stoppedSnapshot(enabled, current.metrics))
                publish()
            }
        }
        foregroundCount = -1
        if (!reconcileForeground(force = true)) {
            markPlatformRestricted(connectionId)
            return rejected(NmeaRuntimeFailure.PlatformRestricted)
        }
        return startConnection(stored.config.id, foregroundAlreadyChecked = true)?.let(::rejected)
            ?: success(connectionId, stored.revision)
    }

    private suspend fun stop(connectionId: ConnectionId): NmeaRuntimeCommandResult {
        val current = connection(connectionId) ?: return rejected(NmeaRuntimeFailure.ConnectionNotFound)
        if (current.stored.runIntent == ConnectionRunIntent.STOPPED_BY_USER && !isOwned(connectionId)) {
            return success(connectionId, current.stored.revision)
        }
        endRuntimeSession(connectionId)
        val stopped = current.stored.copy(
            runIntent = ConnectionRunIntent.STOPPED_BY_USER,
            revision = increment(current.stored.revision),
        )
        pipeline.upsertConnection(stoppedSnapshot(stopped, current.metrics))
        publish()
        val persisted = repository.upsert(stopped)
        reconcileForeground()
        return if (persisted is ConnectionPersistenceResult.Saved) {
            success(connectionId, stopped.revision)
        } else {
            recordIncident(connectionId, null, NmeaRuntimeFailure.PersistenceFailed)
            rejected(NmeaRuntimeFailure.PersistenceFailed)
        }
    }

    private suspend fun retry(connectionId: ConnectionId): NmeaRuntimeCommandResult {
        val current = connection(connectionId) ?: return rejected(NmeaRuntimeFailure.ConnectionNotFound)
        if (current.stored.runIntent != ConnectionRunIntent.ENABLED) {
            return rejected(NmeaRuntimeFailure.InvalidConfiguration(emptySet()))
        }
        endRuntimeSession(connectionId)
        foregroundCount = -1
        if (!reconcileForeground(force = true)) {
            markPlatformRestricted(connectionId)
            return rejected(NmeaRuntimeFailure.PlatformRestricted)
        }
        return startConnection(connectionId, foregroundAlreadyChecked = true)?.let(::rejected)
            ?: success(connectionId, current.stored.revision)
    }

    private suspend fun delete(
        connectionId: ConnectionId,
        expectedRevision: Long?,
    ): NmeaRuntimeCommandResult {
        val current = connection(connectionId) ?: return rejected(NmeaRuntimeFailure.ConnectionNotFound)
        if (expectedRevision != null && expectedRevision != current.stored.revision) {
            return rejected(NmeaRuntimeFailure.PersistenceFailed)
        }
        endRuntimeSession(connectionId)
        return when (repository.delete(connectionId)) {
            is ConnectionPersistenceResult.Saved -> {
                pipeline.removeConnection(connectionId)
                sessionRegistry.forgetConnection(connectionId)
                publish()
                reconcileForeground()
                success(connectionId, current.stored.revision)
            }
            else -> {
                pipeline.upsertConnection(stoppedSnapshot(current.stored, current.metrics))
                publish()
                rejected(NmeaRuntimeFailure.PersistenceFailed)
            }
        }
    }

    private suspend fun startConnection(
        connectionId: ConnectionId,
        foregroundAlreadyChecked: Boolean = false,
    ): NmeaRuntimeFailure? {
        val current = connection(connectionId) ?: return NmeaRuntimeFailure.ConnectionNotFound
        if (isOwned(connectionId)) return null
        if (!networkAvailability.available.value) {
            pipeline.upsertConnection(
                current.copy(
                    token = null,
                    transport = ConnectionTransportState.WaitingForNetwork,
                    input = ConnectionInputState.NO_BYTES,
                    metrics = current.metrics.clearSessionEvidence(),
                    failure = null,
                ),
            )
            publish()
            return null
        }
        if (!foregroundAlreadyChecked && !reconcileForeground()) {
            markPlatformRestricted(connectionId)
            return NmeaRuntimeFailure.PlatformRestricted
        }

        val generation = nextGeneration(connectionId)
        val token = SessionToken(connectionId, current.stored.revision, generation)
        val begin = sessionRegistry.beginSession(connectionId, generation)
        if (begin == ActiveSessionRegistry.BeginResult.REJECTED_ACTIVE_CAPACITY) {
            return NmeaRuntimeFailure.ActiveConnectionCapacityExceeded
        }
        if (
            begin == ActiveSessionRegistry.BeginResult.REJECTED_KNOWN_CAPACITY ||
            begin == ActiveSessionRegistry.BeginResult.REJECTED_STALE_GENERATION
        ) {
            return NmeaRuntimeFailure.InternalError
        }
        tcpFramers[token] = TcpNmeaFramer()
        groupSequences[token] = 0L
        pipeline.upsertConnection(
            current.copy(
                token = token,
                transport = ConnectionTransportState.Starting,
                input = ConnectionInputState.NO_BYTES,
                metrics = current.metrics.clearSessionEvidence(),
                failure = null,
            ),
        )
        publish()

        val job = applicationScope.launch(start = CoroutineStart.LAZY) {
            var terminalEventSeen = false
            try {
                transportFactory.run(current.stored.config, token) { event ->
                    if (event is NetworkTransportEvent.Failed) terminalEventSeen = true
                    mailbox.send(RuntimeMessage.Transport(token, event))
                }
                if (!terminalEventSeen && currentCoroutineContext().isActive) {
                    mailbox.send(
                        RuntimeMessage.Transport(
                            token,
                            NetworkTransportEvent.Failed(
                                NmeaRuntimeFailure.TransportFailed(TransportFailureKind.UNKNOWN),
                                recoverable = true,
                            ),
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (currentCoroutineContext().isActive) {
                    mailbox.send(
                        RuntimeMessage.Transport(
                            token,
                            NetworkTransportEvent.Failed(
                                NmeaRuntimeFailure.TransportFailed(TransportFailureKind.UNKNOWN),
                                recoverable = true,
                            ),
                        ),
                    )
                }
            }
        }
        sessionJobs[connectionId] = job
        job.start()
        return null
    }

    private suspend fun handleTransport(message: RuntimeMessage.Transport) {
        when (val event = message.event) {
            NetworkTransportEvent.TcpConnected -> updateCurrentTransport(
                message.token,
                ConnectionTransportState.TcpConnected,
            )
            is NetworkTransportEvent.UdpListening -> updateCurrentTransport(
                message.token,
                ConnectionTransportState.UdpListening,
            )
            is NetworkTransportEvent.TcpBytes -> handleTcpBytes(message.token, event.bytes)
            is NetworkTransportEvent.UdpDatagram -> handleUdpDatagram(message.token, event.bytes, event.sender)
            is NetworkTransportEvent.Failed -> handleTransportFailure(message.token, event)
        }
    }

    private fun updateCurrentTransport(token: SessionToken, transport: ConnectionTransportState) {
        val current = connection(token.connectionId)
        if (current?.token != token) {
            rejectStaleTransportEvent(token)
            return
        }
        retryAttempts.remove(token.connectionId)
        pipeline.upsertConnection(current.copy(transport = transport, failure = null))
        publish()
    }

    private fun handleTcpBytes(token: SessionToken, bytes: ByteArray) {
        val current = connection(token.connectionId)
        if (current?.token != token) {
            rejectStaleTransportEvent(token)
            publish()
            return
        }
        val framer = tcpFramers.getOrPut(token, ::TcpNmeaFramer)
        val events = framer.accept(bytes)
        val now = clock.nowMillis()
        var updated = current.copy(
            metrics = current.metrics.copy(
                byteCount = add(current.metrics.byteCount, bytes.size.toLong()),
                lastByteAtMillis = now,
                tcpBufferedByteCount = framer.bufferedByteCount,
            ),
        )
        updated = updated.copy(
            input = inputHealthPolicy.evaluate(
                now,
                updated.metrics.lastByteAtMillis,
                updated.metrics.lastValidFrameAtMillis,
            ),
        )
        pipeline.upsertConnection(updated)
        events.forEach { event -> handleFrameEvent(token, event, sender = null, udp = false) }
        publish()
    }

    private fun handleUdpDatagram(token: SessionToken, bytes: ByteArray, sender: SenderIdentity) {
        val current = connection(token.connectionId)
        if (current?.token != token) {
            rejectStaleTransportEvent(token)
            publish()
            return
        }
        val endpoint = current.stored.config.endpoint as? NmeaEndpoint.UdpListener
            ?: return rejectStaleTransportEvent(token)
        val now = clock.nowMillis()
        val senders = if (
            sender in current.metrics.observedUdpSenders ||
            current.metrics.observedUdpSenders.size < MAX_OBSERVED_UDP_SENDERS_PER_CONNECTION
        ) {
            current.metrics.observedUdpSenders + sender
        } else {
            current.metrics.observedUdpSenders
        }
        var updated = current.copy(
            metrics = current.metrics.copy(
                byteCount = add(current.metrics.byteCount, bytes.size.toLong()),
                datagramCount = add(current.metrics.datagramCount, 1L),
                observedUdpSenders = senders,
                lastByteAtMillis = now,
            ),
        )
        updated = updated.copy(
            input = inputHealthPolicy.evaluate(
                now,
                updated.metrics.lastByteAtMillis,
                updated.metrics.lastValidFrameAtMillis,
            ),
        )
        pipeline.upsertConnection(updated)
        UdpNmeaDatagramFramer().frame(bytes, sender).events.forEach { event ->
            handleFrameEvent(token, event, sender, udp = true, endpoint = endpoint)
        }
        publish()
    }

    private fun handleFrameEvent(
        token: SessionToken,
        event: NmeaFrameEvent,
        sender: SenderIdentity?,
        udp: Boolean,
        endpoint: NmeaEndpoint.UdpListener? = null,
    ) {
        when (event) {
            is NmeaFrameEvent.Frame -> acceptFrame(token, event.raw, sender, endpoint)
            is NmeaFrameEvent.Overflow -> mutateMetrics(token.connectionId) { metrics ->
                metrics.copy(
                    frameOverflowCount = add(metrics.frameOverflowCount, if (udp) 0L else 1L),
                    datagramOverflowCount = add(metrics.datagramOverflowCount, if (udp) 1L else 0L),
                )
            }
            is NmeaFrameEvent.InvalidEncoding -> mutateMetrics(token.connectionId) { metrics ->
                metrics.copy(malformedFrameCount = add(metrics.malformedFrameCount, 1L))
            }
            is NmeaFrameEvent.FragmentDiscarded -> mutateMetrics(token.connectionId) { metrics ->
                metrics.copy(malformedFrameCount = add(metrics.malformedFrameCount, 1L))
            }
        }
    }

    private fun acceptFrame(
        token: SessionToken,
        raw: String,
        sender: SenderIdentity?,
        udpEndpoint: NmeaEndpoint.UdpListener? = null,
    ) {
        val source = if (sender == null) {
            SourceIdentity(token.connectionId)
        } else {
            val endpoint = udpEndpoint
                ?: (connection(token.connectionId)?.stored?.config?.endpoint as? NmeaEndpoint.UdpListener)
                ?: return rejectStaleTransportEvent(token)
            endpoint.originIdentityPolicy.sourceIdentity(token.connectionId, sender)
        }
        pipeline.accept(
            NmeaInboundFrame(
                source = source,
                sessionToken = token,
                receivedAtMillis = clock.nowMillis(),
                groupId = ObservationGroupId(nextGroupSequence(token)),
                raw = raw,
                sender = sender,
                receivedByteCount = 0,
            ),
        )
    }

    private suspend fun handleTransportFailure(
        token: SessionToken,
        event: NetworkTransportEvent.Failed,
    ) {
        val current = connection(token.connectionId)
        if (current?.token != token) {
            rejectStaleTransportEvent(token)
            publish()
            return
        }
        endRuntimeSession(token.connectionId)
        val afterEnd = connection(token.connectionId) ?: return
        recordIncident(token.connectionId, token, event.failure)
        if (afterEnd.stored.runIntent != ConnectionRunIntent.ENABLED) return
        if (!event.recoverable) {
            pipeline.upsertConnection(
                afterEnd.copy(
                    transport = ConnectionTransportState.Failed,
                    failure = event.failure,
                ),
            )
            publish()
            return
        }
        if (!networkAvailability.available.value) {
            pipeline.upsertConnection(
                afterEnd.copy(
                    transport = ConnectionTransportState.WaitingForNetwork,
                    failure = event.failure,
                ),
            )
            publish()
            return
        }
        scheduleRetry(afterEnd, event.failure)
    }

    private fun scheduleRetry(connection: ConnectionRuntimeSnapshot, failure: NmeaRuntimeFailure) {
        val id = connection.stored.config.id
        retryJobs.remove(id)?.cancel()
        val attempt = (retryAttempts[id] ?: 0) + 1
        retryAttempts[id] = attempt
        val delayMillis = retryPolicy.delayMillis(attempt)
        pipeline.upsertConnection(
            connection.copy(
                transport = ConnectionTransportState.ReconnectWaiting(
                    attempt = attempt,
                    retryAtMillis = add(clock.nowMillis(), delayMillis),
                ),
                failure = failure,
            ),
        )
        publish()
        retryJobs[id] = applicationScope.launch {
            reconnectDelay.await(delayMillis)
            mailbox.send(RuntimeMessage.RetryElapsed(id, connection.stored.revision))
        }
    }

    private suspend fun handleRetryElapsed(message: RuntimeMessage.RetryElapsed) {
        retryJobs.remove(message.connectionId)
        val current = connection(message.connectionId) ?: return
        if (
            current.stored.revision != message.configRevision ||
            current.stored.runIntent != ConnectionRunIntent.ENABLED
        ) return
        startConnection(message.connectionId)
    }

    private suspend fun handleNetworkChanged(available: Boolean) {
        if (!available) return
        connections()
            .filter { it.stored.runIntent == ConnectionRunIntent.ENABLED }
            .filter { it.transport == ConnectionTransportState.WaitingForNetwork }
            .forEach { connection -> startConnection(connection.stored.config.id) }
    }

    private fun endRuntimeSession(connectionId: ConnectionId) {
        retryJobs.remove(connectionId)?.cancel()
        sessionJobs.remove(connectionId)?.cancel()
        val current = connection(connectionId) ?: return
        current.token?.let { token ->
            sessionRegistry.endSession(connectionId, token.generation)
            tcpFramers.remove(token)?.resetOnDisconnect()
            groupSequences.remove(token)
        }
        pipeline.upsertConnection(
            current.copy(
                token = null,
                transport = ConnectionTransportState.Stopped,
                input = ConnectionInputState.NO_BYTES,
                metrics = current.metrics.clearSessionEvidence(),
                failure = null,
            ),
        )
        publish()
    }

    private fun rejectStaleTransportEvent(token: SessionToken) {
        val current = connection(token.connectionId)
        if (current != null) {
            pipeline.upsertConnection(
                current.copy(
                    metrics = current.metrics.copy(
                        staleGenerationDropCount = add(current.metrics.staleGenerationDropCount, 1L),
                    ),
                ),
            )
        }
        recordIncident(token.connectionId, token, NmeaRuntimeFailure.StaleSession)
    }

    private fun markPlatformRestricted(connectionId: ConnectionId) {
        val current = connection(connectionId) ?: return
        pipeline.upsertConnection(
            current.copy(
                token = null,
                transport = ConnectionTransportState.PlatformStartRequired,
                input = ConnectionInputState.NO_BYTES,
                metrics = current.metrics.clearSessionEvidence(),
                failure = NmeaRuntimeFailure.PlatformRestricted,
            ),
        )
        recordIncident(connectionId, null, NmeaRuntimeFailure.PlatformRestricted)
        publish()
    }

    private fun validate(
        config: NmeaConnectionConfig,
        duplicateTcpConfirmed: Boolean,
    ): NmeaRuntimeFailure? {
        val configState = ConnectionConfigState(
            connections().associate { it.stored.config.id to it.stored.config },
        )
        val result = ConnectionConfigReducer().reduce(
            configState,
            ConnectionConfigAction.Save(config, duplicateTcpConfirmed),
        )
        return when (val effect = result.effects.single()) {
            is ConnectionConfigEffect.Invalid -> NmeaRuntimeFailure.InvalidConfiguration(effect.issues)
            is ConnectionConfigEffect.ConfirmDuplicateTcp -> NmeaRuntimeFailure.DuplicateTcpEndpoint(
                effect.existingConnectionIds.sortedBy { it.value }.first(),
            )
            is ConnectionConfigEffect.Persist -> null
        }
    }

    private fun udpConflict(config: NmeaConnectionConfig): NmeaRuntimeFailure? {
        val endpoint = config.endpoint as? NmeaEndpoint.UdpListener ?: return null
        return connections()
            .asSequence()
            .filter { it.stored.config.id != config.id }
            .filter { it.stored.runIntent == ConnectionRunIntent.ENABLED }
            .mapNotNull { it.stored.config.endpoint as? NmeaEndpoint.UdpListener }
            .firstOrNull { it.localPort == endpoint.localPort }
            ?.let { NmeaRuntimeFailure.UdpAddressInUse(endpoint.localPort) }
    }

    private fun reconcileForeground(force: Boolean = false): Boolean {
        val count = connections().count { it.stored.runIntent == ConnectionRunIntent.ENABLED }
        if (force || count != foregroundCount) {
            foregroundCount = count
            foregroundPermitted = foregroundController.reconcile(count)
        }
        return foregroundPermitted
    }

    private fun stoppedSnapshot(
        stored: StoredNmeaConnection,
        metrics: NmeaConnectionDiagnostics = NmeaConnectionDiagnostics.EMPTY,
    ) = ConnectionRuntimeSnapshot(
        stored = stored,
        token = null,
        transport = ConnectionTransportState.Stopped,
        input = ConnectionInputState.NO_BYTES,
        metrics = metrics.clearSessionEvidence(),
        failure = null,
    )

    private fun isOwned(connectionId: ConnectionId): Boolean =
        sessionJobs[connectionId]?.isActive == true || retryJobs[connectionId]?.isActive == true

    private fun nextGeneration(connectionId: ConnectionId): SessionGeneration {
        val next = generations[connectionId]?.next() ?: SessionGeneration(1L)
        generations[connectionId] = next
        return next
    }

    private fun nextGroupSequence(token: SessionToken): Long {
        val current = groupSequences[token] ?: 0L
        groupSequences[token] = increment(current)
        return current
    }

    private fun mutateMetrics(
        connectionId: ConnectionId,
        transform: (NmeaConnectionDiagnostics) -> NmeaConnectionDiagnostics,
    ) {
        val current = connection(connectionId) ?: return
        pipeline.upsertConnection(current.copy(metrics = transform(current.metrics)))
    }

    private fun recordIncident(
        connectionId: ConnectionId?,
        token: SessionToken?,
        failure: NmeaRuntimeFailure,
    ) {
        pipeline.recordIncident(
            NmeaRuntimeIncident(connectionId, token, failure, clock.nowMillis()),
        )
    }

    private fun connection(connectionId: ConnectionId): ConnectionRuntimeSnapshot? =
        pipeline.snapshot().connections.singleOrNull { it.stored.config.id == connectionId }

    private fun connections(): List<ConnectionRuntimeSnapshot> = pipeline.snapshot().connections

    private fun publish() {
        mutableState.value = pipeline.snapshot()
    }

    private fun success(connectionId: ConnectionId?, revision: Long) =
        NmeaRuntimeCommandResult.Success(connectionId, revision)

    private fun rejected(failure: NmeaRuntimeFailure) = NmeaRuntimeCommandResult.Rejected(failure)

    private sealed interface RuntimeMessage {
        data class Command(
            val command: NmeaRuntimeCommand,
            val reply: CompletableDeferred<NmeaRuntimeCommandResult>,
        ) : RuntimeMessage

        data class NetworkChanged(val available: Boolean) : RuntimeMessage
        data class Transport(val token: SessionToken, val event: NetworkTransportEvent) : RuntimeMessage
        data class RetryElapsed(val connectionId: ConnectionId, val configRevision: Long) : RuntimeMessage
        data object HealthTick : RuntimeMessage
    }
}

private fun NmeaConnectionDiagnostics.clearSessionEvidence(): NmeaConnectionDiagnostics = copy(
    validRate = FiveSecondFrameRate.EMPTY,
    lastByteAtMillis = null,
    lastValidFrameAtMillis = null,
    inputInterruptedAtMillis = null,
    tcpBufferedByteCount = 0,
    pendingIngressFrameCount = 0,
)

private fun NmeaRuntimeCommand.connectionIdOrNull(): ConnectionId? = when (this) {
    is NmeaRuntimeCommand.Save -> config.id
    is NmeaRuntimeCommand.SaveAndStart -> config.id
    is NmeaRuntimeCommand.Start -> connectionId
    is NmeaRuntimeCommand.Stop -> connectionId
    is NmeaRuntimeCommand.Retry -> connectionId
    is NmeaRuntimeCommand.Delete -> connectionId
}

private fun add(value: Long, increment: Long): Long =
    if (Long.MAX_VALUE - value < increment) Long.MAX_VALUE else value + increment

private fun increment(value: Long): Long = add(value, 1L)
