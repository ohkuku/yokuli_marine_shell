package com.yokuli.runtime.marine

import com.yokuli.anchorwatch.MainUiState
import com.yokuli.anchorwatch.api.MarineServices
import com.yokuli.anchorwatch.api.VoyageService
import com.yokuli.anchorwatch.data.database.TripSessionEntity
import com.yokuli.anchorwatch.data.preferences.AppSettings
import com.yokuli.anchorwatch.domain.model.GpsDataSource
import com.yokuli.runtime.contract.VoyageCommandStatus
import com.yokuli.runtime.contract.VoyagePhase
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoyageSessionCoordinatorTest {
    @Test fun twoConsumersSharePendingAndClosingUiDoesNotCancelCommand() = runTest {
        val backend = Backend()
        val runtimeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val coordinator = VoyageSessionCoordinator(backend.services, runtimeScope)
            val screen = launch { coordinator.state.collect {} }
            coordinator.start("First tap")
            coordinator.start("Second app tap")
            runCurrent()
            assertEquals(listOf("startTrip"), backend.commands)
            assertEquals(VoyagePhase.STARTING, coordinator.state.value.phase)
            screen.cancel()

            backend.state.value = backend.state.value.copy(activeTrip = trip(41))
            runCurrent()
            assertEquals(VoyageCommandStatus.CONFIRMED, coordinator.events.first().status)
            assertEquals(41L, coordinator.state.value.id)
            assertFalse(coordinator.state.value.commandPending)
        } finally { runtimeScope.cancel() }
    }

    @Test fun missingConfirmationTimesOutAtEighteenSecondsAndPermitsAnotherCommand() = runTest {
        val backend = Backend()
        val runtimeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val coordinator = VoyageSessionCoordinator(backend.services, runtimeScope)
            coordinator.start("Waiting")
            runCurrent()
            advanceTimeBy(17_999)
            assertTrue(coordinator.state.value.commandPending)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(VoyageCommandStatus.NOT_CONFIRMED, coordinator.events.first().status)
            assertFalse(coordinator.state.value.commandPending)
            assertEquals(VoyagePhase.IDLE, coordinator.state.value.phase)
            coordinator.start("Explicit retry")
            runCurrent()
            assertEquals(2, backend.commands.count { it == "startTrip" })
        } finally { runtimeScope.cancel() }
    }

    @Test fun finishWaitsForTheMatchingDurablyEndedSession() = runTest {
        val backend = Backend()
        val original = trip(72)
        backend.state.value = backend.state.value.copy(activeTrip = original)
        val runtimeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val coordinator = VoyageSessionCoordinator(backend.services, runtimeScope)
            val receipt = async { coordinator.events.first() }
            coordinator.finish()
            runCurrent()
            backend.state.value = backend.state.value.copy(activeTrip = null, tripSessions = listOf(trip(99).copy(active = false, endedAt = 200)))
            runCurrent()
            assertFalse(receipt.isCompleted)
            assertTrue(coordinator.state.value.commandPending)

            backend.state.value = backend.state.value.copy(tripSessions = listOf(original.copy(active = false, endedAt = 300)))
            runCurrent()
            assertEquals(VoyageCommandStatus.CONFIRMED, receipt.await().status)
            assertEquals(listOf("endTrip"), backend.commands)
            assertFalse(coordinator.state.value.commandPending)
        } finally { runtimeScope.cancel() }
    }

    @Test fun pauseRequiresConfirmationForTheSameVoyage() = runTest {
        val backend = Backend()
        val original = trip(51)
        backend.state.value = backend.state.value.copy(activeTrip = original)
        val runtimeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val coordinator = VoyageSessionCoordinator(backend.services, runtimeScope)
            val receipt = async { coordinator.events.first() }
            coordinator.pause()
            runCurrent()
            assertEquals(listOf("pauseTrip"), backend.commands)
            assertFalse(receipt.isCompleted)
            backend.state.value = backend.state.value.copy(activeTrip = trip(52).copy(paused = true))
            runCurrent()
            assertFalse(receipt.isCompleted)

            backend.state.value = backend.state.value.copy(activeTrip = original.copy(paused = true))
            runCurrent()
            assertEquals(VoyageCommandStatus.CONFIRMED, receipt.await().status)
            assertEquals(VoyagePhase.PAUSED, coordinator.state.value.phase)
            assertFalse(coordinator.state.value.commandPending)
        } finally { runtimeScope.cancel() }
    }

    @Test fun systemScopeCancellationBeforeDispatchCannotLeavePendingForever() = runTest {
        val backend = Backend()
        val runtimeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val coordinator = VoyageSessionCoordinator(backend.services, runtimeScope)
        coordinator.start("Never dispatched")
        assertTrue(coordinator.state.value.commandPending)
        runtimeScope.cancel()
        runCurrent()
        assertFalse(coordinator.state.value.commandPending)
        assertTrue(backend.commands.isEmpty())
    }

    private class Backend {
        val state = MutableStateFlow(MainUiState(settings = AppSettings(gpsDataSource = GpsDataSource.SYSTEM), settingsReady = true))
        val commands = mutableListOf<String>()
        private val voyage = proxy<VoyageService> { method ->
            when (method) {
                "getState" -> state
                "startTrip" -> { commands += method; Job().apply { complete() } }
                "endTrip", "pauseTrip" -> { commands += method; null }
                else -> error("Unexpected voyage call: $method")
            }
        }
        val services = proxy<MarineServices> { method ->
            when (method) {
                "getState" -> state
                "getVoyages" -> voyage
                else -> error("Unexpected marine service access: $method")
            }
        }
    }

    companion object {
        private inline fun <reified T : Any> proxy(crossinline invoke: (String) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> invoke(method.name) } as T

        private fun trip(id: Long) = TripSessionEntity(
            id = id, name = "Voyage $id", startedAt = 100,
            boatLengthMeters = null, draftMeters = null,
            positionPreference = "PHONE", headingPreference = "AUTO",
            phoneMotionEnabled = false, mountCalibrationVersion = null,
        )
    }
}
