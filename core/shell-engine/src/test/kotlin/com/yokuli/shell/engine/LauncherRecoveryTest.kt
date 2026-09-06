package com.yokuli.shell.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherRecoveryTest {
    @Test
    fun thirdConsecutiveStartupAttemptEntersSafeMode() {
        val first = LauncherRecoveryPolicy.beginLaunch(LauncherStartupHealth(), nowEpochMillis = 1_000)
        val second = LauncherRecoveryPolicy.beginLaunch(first.health, nowEpochMillis = 2_000)
        val third = LauncherRecoveryPolicy.beginLaunch(second.health, nowEpochMillis = 3_000)

        assertFalse(first.enterSafeMode)
        assertFalse(second.enterSafeMode)
        assertTrue(third.enterSafeMode)
        assertEquals(3, third.health.startupAttemptCount)
    }

    @Test
    fun healthyOrStaleSessionsResetTheCrashLoop() {
        val pending = LauncherRecoveryPolicy.beginLaunch(LauncherStartupHealth(), 1_000).health
        val healthy = LauncherRecoveryPolicy.markHealthy(pending)
        val afterHealthy = LauncherRecoveryPolicy.beginLaunch(healthy, 2_000)
        val afterStale = LauncherRecoveryPolicy.beginLaunch(pending, 1_000 + LauncherRecoveryPolicy.FAILURE_WINDOW_MILLIS + 1)

        assertEquals(1, afterHealthy.health.startupAttemptCount)
        assertEquals(1, afterStale.health.startupAttemptCount)
        assertFalse(afterHealthy.enterSafeMode)
        assertFalse(afterStale.enterSafeMode)
    }

    @Test
    fun futureSchemaFallsBackDeterministically() {
        val defaults = LauncherPersistedState()
        val future = defaults.copy(schemaVersion = CURRENT_LAUNCHER_PERSISTENCE_SCHEMA + 1, themeModeName = "LIGHT")

        val first = LauncherPersistedStateMigration.migrate(future, defaults)
        val second = LauncherPersistedStateMigration.migrate(future, defaults)

        assertEquals(defaults, first.state)
        assertEquals(first, second)
        assertTrue(first.incidents.isNotEmpty())
    }

    @Test
    fun invalidDisplayPreferencesFallBackWithoutChangingTheStartDocument() {
        val defaults = LauncherPersistedState()
        val result = LauncherPersistedStateMigration.migrate(
            defaults.copy(
                measurementUnitSystemName = "imperial-ish",
                motionPreferenceName = "FAST",
                appPreferenceValues = mapOf("BAD KEY" to "x", "chart.tile.mode" to "c:auto"),
            ),
            defaults,
        )

        assertEquals("NAUTICAL", result.state.measurementUnitSystemName)
        assertEquals("FOLLOW_SYSTEM", result.state.motionPreferenceName)
        assertEquals(mapOf("chart.tile.mode" to "c:auto"), result.state.appPreferenceValues)
        assertTrue(LauncherPersistenceIncident.INVALID_PREFERENCE_REPLACED in result.incidents)
    }
}
