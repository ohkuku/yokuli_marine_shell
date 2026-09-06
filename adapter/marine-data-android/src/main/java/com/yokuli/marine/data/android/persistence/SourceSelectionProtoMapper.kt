package com.yokuli.marine.data.android.persistence

import com.yokuli.marine.data.android.proto.PersistedSelectedSource
import com.yokuli.marine.data.android.proto.PersistedSelectionReason
import com.yokuli.marine.data.android.proto.PersistedSourcePreference
import com.yokuli.marine.data.android.proto.PersistedSourceSelectionStore
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.DepthReference
import com.yokuli.marine.data.model.HeadingReference
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.model.WindReference
import com.yokuli.marine.data.model.WindSpeedReference
import com.yokuli.marine.data.source.PersistedSourceSelections
import com.yokuli.marine.data.source.SelectionReason
import com.yokuli.marine.data.source.SourcePreference

internal data class DecodedSourceSelections(
    val state: PersistedSourceSelections,
    val quarantinedRecordCount: Int,
)

internal object SourceSelectionProtoMapper {
    fun decode(store: PersistedSourceSelectionStore): DecodedSourceSelections {
        var quarantined = 0
        val preferences = linkedMapOf<DataKey, SourcePreference>()
        store.preferencesList.forEach { record ->
            val decoded = runCatching { decodePreference(record) }.getOrNull()
            if (decoded == null || decoded.first in preferences) {
                quarantined++
            } else {
                preferences[decoded.first] = decoded.second
            }
        }
        return DecodedSourceSelections(
            state = PersistedSourceSelections(
                schemaVersion = store.schemaVersion.takeIf { it > 0 }
                    ?: SourceSelectionStateSerializer.CURRENT_SCHEMA_VERSION,
                revision = store.revision.coerceAtLeast(0L),
                preferences = preferences,
            ),
            quarantinedRecordCount = quarantined,
        )
    }

    fun encode(state: PersistedSourceSelections): PersistedSourceSelectionStore =
        PersistedSourceSelectionStore.newBuilder()
            .setSchemaVersion(SourceSelectionStateSerializer.CURRENT_SCHEMA_VERSION)
            .setRevision(state.revision)
            .addAllPreferences(
                state.preferences.entries
                    .sortedBy { encodeKey(it.key) }
                    .map { (key, preference) -> encodePreference(key, preference) },
            )
            .build()

    private fun encodePreference(key: DataKey, preference: SourcePreference): PersistedSourcePreference =
        PersistedSourcePreference.newBuilder()
            .setDataKey(encodeKey(key))
            .apply {
                when (preference) {
                    SourcePreference.Disabled -> disabled = true
                    is SourcePreference.Selected -> selected = PersistedSelectedSource.newBuilder()
                        .setConnectionId(preference.source.connectionId.value)
                        .apply {
                            preference.source.udpOrigin?.let { origin ->
                                udpHostAddress = origin.hostAddress
                                origin.port?.let { udpPort = it }
                            }
                        }
                        .setReason(preference.reason.toProto())
                        .build()
                }
            }
            .build()

    private fun decodePreference(record: PersistedSourcePreference): Pair<DataKey, SourcePreference>? {
        val key = decodeKey(record.dataKey) ?: return null
        val preference = when (record.preferenceCase) {
            PersistedSourcePreference.PreferenceCase.DISABLED -> {
                if (!record.disabled) return null
                SourcePreference.Disabled
            }
            PersistedSourcePreference.PreferenceCase.SELECTED -> {
                val selected = record.selected
                if (selected.connectionId.isBlank()) return null
                val reason = when (selected.reason) {
                    PersistedSelectionReason.PERSISTED_SELECTION_REASON_AUTOMATIC_UNIQUE ->
                        SelectionReason.AUTOMATIC_UNIQUE
                    PersistedSelectionReason.PERSISTED_SELECTION_REASON_USER -> SelectionReason.USER
                    else -> return null
                }
                SourcePreference.Selected(
                    source = SourceIdentity.fromStableOrigin(
                        connectionId = ConnectionId(selected.connectionId),
                        udpHostAddress = selected.udpHostAddress.takeIf { selected.hasUdpHostAddress() },
                        udpPort = selected.udpPort.takeIf { selected.hasUdpPort() },
                    ),
                    reason = reason,
                )
            }
            PersistedSourcePreference.PreferenceCase.PREFERENCE_NOT_SET,
            null,
            -> return null
        }
        return key to preference
    }

    private fun SelectionReason.toProto(): PersistedSelectionReason = when (this) {
        SelectionReason.AUTOMATIC_UNIQUE ->
            PersistedSelectionReason.PERSISTED_SELECTION_REASON_AUTOMATIC_UNIQUE
        SelectionReason.USER -> PersistedSelectionReason.PERSISTED_SELECTION_REASON_USER
    }

    private fun encodeKey(key: DataKey): String = when (key) {
        DataKey.Position -> "position"
        DataKey.SpeedOverGround -> "speed_over_ground"
        DataKey.CourseOverGround -> "course_over_ground"
        is DataKey.Heading -> "heading:${key.reference.name.lowercase()}"
        is DataKey.Depth -> "depth:${key.reference.name.lowercase()}"
        is DataKey.WindAngle -> "wind_angle:${key.reference.name.lowercase()}"
        is DataKey.WindSpeed -> "wind_speed:${key.reference.name.lowercase()}"
        DataKey.MagneticVariation -> "magnetic_variation"
        DataKey.SourceTime -> "source_time"
        DataKey.FixQuality -> "fix_quality"
        DataKey.Satellites -> "satellites"
        DataKey.HorizontalDilution -> "horizontal_dilution"
        DataKey.Altitude -> "altitude"
        DataKey.PositionAccuracy -> "position_accuracy"
    }

    private fun decodeKey(value: String): DataKey? = when (value) {
        "position" -> DataKey.Position
        "speed_over_ground" -> DataKey.SpeedOverGround
        "course_over_ground" -> DataKey.CourseOverGround
        "heading:true" -> DataKey.Heading(HeadingReference.TRUE)
        "heading:magnetic" -> DataKey.Heading(HeadingReference.MAGNETIC)
        "depth:below_transducer" -> DataKey.Depth(DepthReference.BELOW_TRANSDUCER)
        "depth:below_surface" -> DataKey.Depth(DepthReference.BELOW_SURFACE)
        "depth:below_keel" -> DataKey.Depth(DepthReference.BELOW_KEEL)
        "wind_angle:apparent" -> DataKey.WindAngle(WindReference.APPARENT)
        "wind_angle:true_relative" -> DataKey.WindAngle(WindReference.TRUE_RELATIVE)
        "wind_angle:true_north" -> DataKey.WindAngle(WindReference.TRUE_NORTH)
        "wind_angle:magnetic_north" -> DataKey.WindAngle(WindReference.MAGNETIC_NORTH)
        "wind_speed:apparent" -> DataKey.WindSpeed(WindSpeedReference.APPARENT)
        "wind_speed:true" -> DataKey.WindSpeed(WindSpeedReference.TRUE)
        "magnetic_variation" -> DataKey.MagneticVariation
        "source_time" -> DataKey.SourceTime
        "fix_quality" -> DataKey.FixQuality
        "satellites" -> DataKey.Satellites
        "horizontal_dilution" -> DataKey.HorizontalDilution
        "altitude" -> DataKey.Altitude
        "position_accuracy" -> DataKey.PositionAccuracy
        else -> null
    }
}
