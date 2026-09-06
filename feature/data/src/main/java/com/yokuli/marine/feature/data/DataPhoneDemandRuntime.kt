package com.yokuli.marine.feature.data

import com.yokuli.marine.data.phone.PHONE_SYSTEM_LOCATION_SOURCE
import com.yokuli.marine.data.phone.PhoneLocationCommand
import com.yokuli.marine.data.phone.PhoneLocationCommandResult
import com.yokuli.marine.data.phone.PhoneLocationDemand
import com.yokuli.marine.data.phone.PhoneLocationDemandPolicy
import com.yokuli.marine.data.phone.PhoneLocationRuntimePort
import com.yokuli.marine.data.phone.PhoneLocationSnapshot
import com.yokuli.marine.data.phone.PhoneLocationState
import com.yokuli.marine.data.source.MarineSourceRuntimePort
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.SourceSelectionCommandResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DataPhoneDemandState(
    val demand: PhoneLocationDemand = PhoneLocationDemand.NONE,
    val pendingGroup: SourceGroup? = null,
    val phone: PhoneLocationSnapshot = PhoneLocationSnapshot.EMPTY,
)

sealed interface DataPhoneRequestResult {
    data object Accepted : DataPhoneRequestResult
    data object PermissionRequired : DataPhoneRequestResult
    data object SystemLocationDisabled : DataPhoneRequestResult
    data object PlatformRestricted : DataPhoneRequestResult
    data object CandidateUnavailable : DataPhoneRequestResult
    data object PersistenceFailed : DataPhoneRequestResult
}

interface DataPhoneDemandPort {
    val state: StateFlow<DataPhoneDemandState>
    suspend fun request(group: SourceGroup): DataPhoneRequestResult
    suspend fun permissionResult(permanentlyDenied: Boolean): DataPhoneRequestResult
    suspend fun refreshPlatformState(): DataPhoneRequestResult
    suspend fun cancelPending()
}

/**
 * Process-owned bridge from durable source selection to platform location demand.
 * UI lifetime never starts or stops location; selection truth does.
 */
class DataPhoneDemandRuntime(
    private val sourcePort: MarineSourceRuntimePort,
    private val phonePort: PhoneLocationRuntimePort,
    scope: CoroutineScope,
) : DataPhoneDemandPort {
    private val mutex = Mutex()
    private var sources: MarineSourceSnapshot = sourcePort.state.value
    private var pendingGroup: SourceGroup? = null
    private val mutableState = MutableStateFlow(snapshot())
    override val state: StateFlow<DataPhoneDemandState> = mutableState.asStateFlow()

    init {
        scope.launch {
            sourcePort.state.collect { incoming ->
                mutex.withLock {
                    sources = incoming
                    reconcileLocked()
                    publishLocked()
                }
            }
        }
        scope.launch {
            phonePort.state.collect {
                mutex.withLock { publishLocked() }
            }
        }
    }

    override suspend fun request(group: SourceGroup): DataPhoneRequestResult = mutex.withLock {
        if (group != SourceGroup.POSITION_AND_MOTION) return@withLock DataPhoneRequestResult.CandidateUnavailable
        pendingGroup = group
        publishLocked()
        selectPhoneIfAvailableLocked()?.let {
            publishLocked()
            return@withLock it
        }
        phonePort.execute(PhoneLocationCommand.Enable).toDataResult().also { publishLocked() }
    }

    override suspend fun permissionResult(permanentlyDenied: Boolean): DataPhoneRequestResult = mutex.withLock {
        phonePort.execute(PhoneLocationCommand.PermissionResult(permanentlyDenied)).toDataResult().also {
            publishLocked()
        }
    }

    override suspend fun refreshPlatformState(): DataPhoneRequestResult = mutex.withLock {
        phonePort.execute(PhoneLocationCommand.RefreshPlatformState).toDataResult().also { publishLocked() }
    }

    override suspend fun cancelPending() = mutex.withLock {
        pendingGroup = null
        reconcileLocked()
        publishLocked()
    }

    private suspend fun reconcileLocked() {
        // Revision zero is intentionally not interpreted as "nothing selected" during startup.
        if (sources.revision == 0L) return
        selectPhoneIfAvailableLocked()
        val required = PhoneLocationDemandPolicy.resolve(sources).required || pendingGroup != null
        val phone = phonePort.state.value
        if (required) {
            if (!phone.enabledByUser && phone.state != PhoneLocationState.PERMISSION_REQUIRED) {
                phonePort.execute(PhoneLocationCommand.Enable)
            }
        } else if (phone.enabledByUser) {
            phonePort.execute(PhoneLocationCommand.Disable)
        }
    }

    private suspend fun selectPhoneIfAvailableLocked(): DataPhoneRequestResult? {
        val group = pendingGroup ?: return null
        val plan = SourceGroupSelectionAdapter.select(group, PHONE_SYSTEM_LOCATION_SOURCE, sources)
        if (plan !is SourceGroupSelectionPlan.Ready) return null
        return when (sourcePort.execute(plan.command)) {
            is SourceSelectionCommandResult.Success -> {
                pendingGroup = null
                DataPhoneRequestResult.Accepted
            }
            is SourceSelectionCommandResult.Rejected -> DataPhoneRequestResult.PersistenceFailed
        }
    }

    private fun snapshot() = DataPhoneDemandState(
        demand = PhoneLocationDemandPolicy.resolve(sources),
        pendingGroup = pendingGroup,
        phone = phonePort.state.value,
    )

    private fun publishLocked() {
        mutableState.value = snapshot()
    }
}

private fun PhoneLocationCommandResult.toDataResult(): DataPhoneRequestResult = when (this) {
    PhoneLocationCommandResult.Success -> DataPhoneRequestResult.Accepted
    is PhoneLocationCommandResult.PermissionRequired -> DataPhoneRequestResult.PermissionRequired
    PhoneLocationCommandResult.SystemLocationDisabled -> DataPhoneRequestResult.SystemLocationDisabled
    PhoneLocationCommandResult.PlatformRestricted -> DataPhoneRequestResult.PlatformRestricted
}
