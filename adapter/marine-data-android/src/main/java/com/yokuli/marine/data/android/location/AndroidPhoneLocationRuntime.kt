package com.yokuli.marine.data.android.location

import com.yokuli.marine.data.phone.METERS_PER_SECOND_TO_KNOTS
import com.yokuli.marine.data.phone.PhoneLocationCommand
import com.yokuli.marine.data.phone.PhoneLocationCommandResult
import com.yokuli.marine.data.phone.PhoneLocationFix
import com.yokuli.marine.data.phone.PhoneLocationIntentStore
import com.yokuli.marine.data.phone.PhoneLocationPermission
import com.yokuli.marine.data.phone.PhoneLocationRuntimePort
import com.yokuli.marine.data.phone.PhoneLocationSnapshot
import com.yokuli.marine.data.phone.PhoneLocationState
import com.yokuli.marine.data.phone.PlatformLocationFix
import com.yokuli.marine.data.time.MonotonicClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidPhoneLocationRuntime(
    private val platform: PhoneLocationPlatform,
    private val clock: MonotonicClock,
    private val applicationScope: CoroutineScope,
    private val intentStore: PhoneLocationIntentStore = PhoneLocationIntentStore.NON_PERSISTENT,
    private val foregroundController: PhoneLocationForegroundController =
        PhoneLocationForegroundController.NO_OP,
) : PhoneLocationRuntimePort {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(initialSnapshot())
    override val state: StateFlow<PhoneLocationSnapshot> = mutableState.asStateFlow()
    private var sequence = 0L
    private var listening = false
    private var permanentlyDenied = false

    init {
        applicationScope.launch {
            if (runCatching { intentStore.loadEnabled() }.getOrDefault(false)) {
                mutex.withLock { enable(persistIntent = false) }
            }
        }
    }

    override suspend fun execute(command: PhoneLocationCommand): PhoneLocationCommandResult = mutex.withLock {
        when (command) {
            PhoneLocationCommand.Enable -> enable(persistIntent = true)
            PhoneLocationCommand.Disable -> disable()
            PhoneLocationCommand.RefreshPlatformState -> refresh()
            is PhoneLocationCommand.PermissionResult -> {
                permanentlyDenied = command.permanentlyDenied
                refresh()
            }
        }
    }

    private suspend fun enable(persistIntent: Boolean): PhoneLocationCommandResult {
        if (persistIntent && !runCatching { intentStore.saveEnabled(true) }.getOrDefault(false)) {
            update(
                enabled = false,
                permission = platform.permission(),
                systemEnabled = platform.isSystemLocationEnabled(),
                state = PhoneLocationState.PLATFORM_RESTRICTED,
            )
            return PhoneLocationCommandResult.PlatformRestricted
        }
        val permission = currentPermission()
        val systemEnabled = platform.isSystemLocationEnabled()
        if (!permission.isGranted) {
            stopListener()
            foregroundController.reconcile(false)
            update(true, permission, systemEnabled, PhoneLocationState.PERMISSION_REQUIRED)
            return PhoneLocationCommandResult.PermissionRequired(permission)
        }
        if (!systemEnabled) {
            stopListener()
            foregroundController.reconcile(false)
            update(true, permission, false, PhoneLocationState.SYSTEM_LOCATION_DISABLED)
            return PhoneLocationCommandResult.SystemLocationDisabled
        }
        if (!foregroundController.reconcile(true)) {
            stopListener()
            update(true, permission, true, PhoneLocationState.PLATFORM_RESTRICTED)
            return PhoneLocationCommandResult.PlatformRestricted
        }
        if (!listening) {
            update(true, permission, true, PhoneLocationState.STARTING)
            try {
                listening = true
                platform.start(
                    onFix = { fix -> applicationScope.launch { acceptFix(fix) } },
                    onProviderChanged = { applicationScope.launch { execute(PhoneLocationCommand.RefreshPlatformState) } },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                stopListener()
                foregroundController.reconcile(false)
                update(true, currentPermission(), platform.isSystemLocationEnabled(), PhoneLocationState.PLATFORM_RESTRICTED)
                return PhoneLocationCommandResult.PlatformRestricted
            }
        }
        return PhoneLocationCommandResult.Success
    }

    private suspend fun disable(): PhoneLocationCommandResult {
        val persisted = runCatching { intentStore.saveEnabled(false) }.getOrDefault(false)
        stopListener(force = true)
        foregroundController.reconcile(false)
        update(
            enabled = false,
            permission = currentPermission(),
            systemEnabled = platform.isSystemLocationEnabled(),
            state = PhoneLocationState.DISABLED_BY_USER,
        )
        return if (persisted) PhoneLocationCommandResult.Success else PhoneLocationCommandResult.PlatformRestricted
    }

    private suspend fun refresh(): PhoneLocationCommandResult {
        if (!mutableState.value.enabledByUser) {
            update(false, currentPermission(), platform.isSystemLocationEnabled(), PhoneLocationState.DISABLED_BY_USER)
            return PhoneLocationCommandResult.Success
        }
        stopListener()
        return enable(persistIntent = false)
    }

    private suspend fun acceptFix(fix: PlatformLocationFix) = mutex.withLock {
        if (!listening || !mutableState.value.enabledByUser) return@withLock
        sequence = increment(sequence)
        val converted = PhoneLocationFix(
            latitudeDegrees = fix.latitudeDegrees,
            longitudeDegrees = fix.longitudeDegrees,
            speedKnots = fix.speedMetersPerSecond?.times(METERS_PER_SECOND_TO_KNOTS),
            courseOverGroundDegrees = fix.bearingDegrees,
            horizontalAccuracyMeters = fix.horizontalAccuracyMeters,
            receivedAtMillis = clock.nowMillis(),
            sourceTimeEpochMillis = fix.sourceTimeEpochMillis,
            provider = fix.provider,
            sequence = sequence,
        )
        mutableState.value = mutableState.value.copy(
            permission = currentPermission(),
            systemLocationEnabled = platform.isSystemLocationEnabled(),
            state = PhoneLocationState.RECEIVING,
            latestFix = converted,
            revision = increment(mutableState.value.revision),
        )
    }

    private fun stopListener(force: Boolean = false) {
        if (listening || force) platform.stop()
        listening = false
    }

    private fun update(
        enabled: Boolean,
        permission: PhoneLocationPermission,
        systemEnabled: Boolean,
        state: PhoneLocationState,
    ) {
        mutableState.value = mutableState.value.copy(
            enabledByUser = enabled,
            permission = permission,
            systemLocationEnabled = systemEnabled,
            state = state,
            revision = increment(mutableState.value.revision),
        )
    }

    private fun initialSnapshot() = PhoneLocationSnapshot.EMPTY.copy(
        permission = platform.permission().takeIf { it.isGranted }
            ?: PhoneLocationPermission.NOT_DETERMINED,
        systemLocationEnabled = platform.isSystemLocationEnabled(),
    )

    private fun currentPermission(): PhoneLocationPermission {
        val actual = platform.permission()
        if (actual.isGranted) {
            permanentlyDenied = false
            return actual
        }
        return if (permanentlyDenied) PhoneLocationPermission.PERMANENTLY_DENIED else actual
    }
}

private val PhoneLocationPermission.isGranted: Boolean
    get() = this == PhoneLocationPermission.APPROXIMATE || this == PhoneLocationPermission.PRECISE

private fun increment(value: Long): Long = if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
