package com.yokuli.marine.data.android

import com.yokuli.marine.data.android.location.AndroidPhoneLocationRuntime
import com.yokuli.marine.data.android.location.PhoneLocationPlatform
import com.yokuli.marine.data.phone.PhoneLocationCommand
import com.yokuli.marine.data.phone.PhoneLocationCommandResult
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PhoneLocationState
import com.yokuli.marine.data.phone.PlatformLocationFix
import com.yokuli.marine.data.time.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLocationRuntimeTest {
    @Test
    fun enableWithDeniedPermissionRequestsPermissionWithoutStartingPlatform() = runBlocking {
        val platform = FakePhonePlatform(permission = PhoneLocationPermission.DENIED)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = AndroidPhoneLocationRuntime(platform, TestClock(10L), scope)
            val result = runtime.execute(PhoneLocationCommand.Enable)

            assertTrue(result is PhoneLocationCommandResult.PermissionRequired)
            assertEquals(0, platform.startCount)
            assertEquals(PhoneLocationState.PERMISSION_REQUIRED, runtime.state.value.state)
            assertTrue(runtime.state.value.enabledByUser)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun precisePlatformFixPreservesOptionalFieldAbsenceAndProviderTruth() = runBlocking {
        val platform = FakePhonePlatform(permission = PhoneLocationPermission.PRECISE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = AndroidPhoneLocationRuntime(platform, TestClock(20L), scope)
            assertTrue(runtime.execute(PhoneLocationCommand.Enable) is PhoneLocationCommandResult.Success)
            platform.emit(
                PlatformLocationFix(
                    latitudeDegrees = -36.8,
                    longitudeDegrees = 174.7,
                    speedMetersPerSecond = null,
                    bearingDegrees = null,
                    horizontalAccuracyMeters = 4.0,
                    sourceTimeEpochMillis = 1_700_000_000_000L,
                    provider = "fused",
                ),
            )

            val fix = runtime.state.value.latestFix!!
            assertNull(fix.speedKnots)
            assertNull(fix.courseOverGroundDegrees)
            assertEquals(4.0, fix.horizontalAccuracyMeters!!, 0.0)
            assertEquals("fused", fix.provider)
            assertEquals(PhoneLocationState.RECEIVING, runtime.state.value.state)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun systemLocationOffAndExplicitDisableNeverPretendToReceive() = runBlocking {
        val platform = FakePhonePlatform(
            permission = PhoneLocationPermission.APPROXIMATE,
            locationEnabled = false,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = AndroidPhoneLocationRuntime(platform, TestClock(30L), scope)
            val result = runtime.execute(PhoneLocationCommand.Enable)
            assertTrue(result is PhoneLocationCommandResult.SystemLocationDisabled)
            assertEquals(PhoneLocationState.SYSTEM_LOCATION_DISABLED, runtime.state.value.state)
            assertEquals(0, platform.startCount)

            runtime.execute(PhoneLocationCommand.Disable)
            assertEquals(PhoneLocationState.DISABLED_BY_USER, runtime.state.value.state)
            assertFalse(runtime.state.value.enabledByUser)
            assertEquals(1, platform.stopCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun permissionResultCanRepresentPermanentDenialWithoutStartingAListener() = runBlocking {
        val platform = FakePhonePlatform(permission = PhoneLocationPermission.DENIED)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = AndroidPhoneLocationRuntime(platform, TestClock(40L), scope)
            runtime.execute(PhoneLocationCommand.Enable)
            runtime.execute(PhoneLocationCommand.PermissionResult(permanentlyDenied = true))

            assertEquals(PhoneLocationPermission.PERMANENTLY_DENIED, runtime.state.value.permission)
            assertEquals(PhoneLocationState.PERMISSION_REQUIRED, runtime.state.value.state)
            assertEquals(0, platform.startCount)
        } finally {
            scope.cancel()
        }
    }
}

private class FakePhonePlatform(
    var permission: PhoneLocationPermission,
    var locationEnabled: Boolean = true,
) : PhoneLocationPlatform {
    var startCount = 0
    var stopCount = 0
    private var listener: ((PlatformLocationFix) -> Unit)? = null

    override fun permission(): PhoneLocationPermission = permission
    override fun isSystemLocationEnabled(): Boolean = locationEnabled
    override fun start(onFix: (PlatformLocationFix) -> Unit, onProviderChanged: () -> Unit) {
        startCount++
        listener = onFix
    }
    override fun stop() {
        stopCount++
        listener = null
    }

    fun emit(fix: PlatformLocationFix) = requireNotNull(listener).invoke(fix)
}

private class TestClock(private var value: Long) : MonotonicClock {
    override fun nowMillis(): Long = value++
}
