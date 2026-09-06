package com.yokuli.marine.data.phone

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.SourceIdentity
import kotlinx.coroutines.flow.StateFlow

val PHONE_SYSTEM_LOCATION_SOURCE: SourceIdentity =
    SourceIdentity(ConnectionId("phone-system-location"))

enum class PhoneLocationPermission {
    NOT_DETERMINED,
    DENIED,
    PERMANENTLY_DENIED,
    APPROXIMATE,
    PRECISE,
}

enum class PhoneLocationState {
    DISABLED_BY_USER,
    PERMISSION_REQUIRED,
    SYSTEM_LOCATION_DISABLED,
    STARTING,
    RECEIVING,
    INTERRUPTED,
    PLATFORM_RESTRICTED,
}

data class PlatformLocationFix(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val speedMetersPerSecond: Double?,
    val bearingDegrees: Double?,
    val horizontalAccuracyMeters: Double?,
    val sourceTimeEpochMillis: Long?,
    val provider: String,
) {
    init {
        require(latitudeDegrees.isFinite() && latitudeDegrees in -90.0..90.0)
        require(longitudeDegrees.isFinite() && longitudeDegrees in -180.0..180.0)
        require(speedMetersPerSecond == null || speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0.0)
        require(bearingDegrees == null || bearingDegrees.isFinite() && bearingDegrees in 0.0..<360.0)
        require(
            horizontalAccuracyMeters == null ||
                horizontalAccuracyMeters.isFinite() && horizontalAccuracyMeters >= 0.0,
        )
        require(sourceTimeEpochMillis == null || sourceTimeEpochMillis >= 0L)
        require(provider.isNotBlank())
    }
}

data class PhoneLocationFix(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val speedKnots: Double?,
    val courseOverGroundDegrees: Double?,
    val horizontalAccuracyMeters: Double?,
    val receivedAtMillis: Long,
    val sourceTimeEpochMillis: Long?,
    val provider: String,
    val sequence: Long,
) {
    init {
        PlatformLocationFix(
            latitudeDegrees = latitudeDegrees,
            longitudeDegrees = longitudeDegrees,
            speedMetersPerSecond = speedKnots?.div(METERS_PER_SECOND_TO_KNOTS),
            bearingDegrees = courseOverGroundDegrees,
            horizontalAccuracyMeters = horizontalAccuracyMeters,
            sourceTimeEpochMillis = sourceTimeEpochMillis,
            provider = provider,
        )
        require(receivedAtMillis >= 0L)
        require(sequence >= 0L)
    }
}

data class PhoneLocationSnapshot(
    val enabledByUser: Boolean,
    val permission: PhoneLocationPermission,
    val systemLocationEnabled: Boolean,
    val state: PhoneLocationState,
    val latestFix: PhoneLocationFix?,
    val revision: Long,
) {
    init {
        require(revision >= 0L)
        require(state != PhoneLocationState.RECEIVING || latestFix != null)
    }

    companion object {
        val EMPTY = PhoneLocationSnapshot(
            enabledByUser = false,
            permission = PhoneLocationPermission.NOT_DETERMINED,
            systemLocationEnabled = false,
            state = PhoneLocationState.DISABLED_BY_USER,
            latestFix = null,
            revision = 0L,
        )
    }
}

sealed interface PhoneLocationCommand {
    data object Enable : PhoneLocationCommand
    data object Disable : PhoneLocationCommand
    data object RefreshPlatformState : PhoneLocationCommand
}

sealed interface PhoneLocationCommandResult {
    data object Success : PhoneLocationCommandResult
    data class PermissionRequired(val permission: PhoneLocationPermission) : PhoneLocationCommandResult
    data object SystemLocationDisabled : PhoneLocationCommandResult
    data object PlatformRestricted : PhoneLocationCommandResult
}

interface PhoneLocationRuntimePort {
    val state: StateFlow<PhoneLocationSnapshot>
    suspend fun execute(command: PhoneLocationCommand): PhoneLocationCommandResult
}

interface PhoneLocationIntentStore {
    suspend fun loadEnabled(): Boolean
    suspend fun saveEnabled(enabled: Boolean): Boolean

    companion object {
        val VOLATILE = object : PhoneLocationIntentStore {
            private var enabled = false
            override suspend fun loadEnabled(): Boolean = enabled
            override suspend fun saveEnabled(enabled: Boolean): Boolean {
                this.enabled = enabled
                return true
            }
        }
    }
}

const val METERS_PER_SECOND_TO_KNOTS: Double = 1.9438444924406048
