package com.yokuli.marine.data.android.runtime

import com.yokuli.marine.data.phone.PhoneLocationRuntimePort
import com.yokuli.marine.data.runtime.NmeaInputRuntimePort
import com.yokuli.marine.data.source.DefaultMarineSourceRuntime
import com.yokuli.marine.data.source.MarineSourceRuntimePort
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.SourceCatalogProjector
import com.yokuli.marine.data.source.SourceSelectionCommand
import com.yokuli.marine.data.source.SourceSelectionCommandResult
import com.yokuli.marine.data.source.SourceSelectionRepository
import com.yokuli.marine.data.time.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Combines NMEA and phone platform truth before applying the single OS-wide policy. */
class AndroidMarineSourceRuntime(
    private val nmeaRuntime: NmeaInputRuntimePort,
    private val phoneRuntime: PhoneLocationRuntimePort,
    repository: SourceSelectionRepository,
    private val clock: MonotonicClock,
    applicationScope: CoroutineScope,
    private val projector: SourceCatalogProjector = SourceCatalogProjector(),
) : MarineSourceRuntimePort {
    private val engine = DefaultMarineSourceRuntime(repository, clock)
    override val state: StateFlow<MarineSourceSnapshot> = engine.state
    private val knownConnectionNames = linkedMapOf<com.yokuli.marine.data.model.ConnectionId, String>()

    init {
        applicationScope.launch {
            engine.initialize()
            combine(nmeaRuntime.state, phoneRuntime.state) { nmea, phone -> nmea to phone }
                .collect { (nmea, phone) ->
                    val activeNames = nmea.connections.associate {
                        it.stored.config.id to it.stored.config.displayName
                    }
                    knownConnectionNames.putAll(activeNames)
                    engine.updateCatalog(
                        projector.project(
                            observationCatalog = nmea.observationCatalog,
                            connectionNames = knownConnectionNames,
                            phone = phone,
                            nowMillis = clock.nowMillis(),
                            activeConnectionIds = activeNames.keys,
                        ),
                    )
                }
        }
        applicationScope.launch {
            while (isActive) {
                delay(1_000L)
                runCatching { engine.tick() }
            }
        }
    }

    override suspend fun execute(command: SourceSelectionCommand): SourceSelectionCommandResult =
        engine.execute(command)
}
