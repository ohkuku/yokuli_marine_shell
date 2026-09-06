package com.yokuli.marine.map.storage

import com.yokuli.marine.map.storage.proto.ActiveNavigationSessionProto
import com.yokuli.marine.navigation.domain.ActiveNavigationSession
import com.yokuli.marine.navigation.domain.NavigationAdvancePolicy
import com.yokuli.marine.navigation.domain.NavigationSessionState

internal object ActiveNavigationSessionProtoMapper {
    const val SCHEMA_VERSION = 1

    fun encode(session: ActiveNavigationSession?): ActiveNavigationSessionProto =
        ActiveNavigationSessionProto.newBuilder()
            .setSchemaVersion(SCHEMA_VERSION)
            .setHasSession(session != null)
            .apply {
                session?.let {
                    routeId = it.routeId
                    routeRevision = it.routeRevision
                    startedAtEpochMillis = it.startedAtEpochMillis
                    activeLegIndex = it.activeLegIndex
                    arrivalRadiusMeters = it.arrivalRadiusMeters
                    advancePolicy = it.advancePolicy.name
                    state = it.state.name
                }
            }
            .build()

    fun decode(proto: ActiveNavigationSessionProto): ActiveNavigationSession? {
        require(proto.schemaVersion in 0..SCHEMA_VERSION) { "Unsupported active navigation schema ${proto.schemaVersion}" }
        if (!proto.hasSession) return null
        return ActiveNavigationSession(
            routeId = proto.routeId,
            routeRevision = proto.routeRevision,
            startedAtEpochMillis = proto.startedAtEpochMillis,
            activeLegIndex = proto.activeLegIndex,
            arrivalRadiusMeters = proto.arrivalRadiusMeters,
            advancePolicy = enumValueOf<NavigationAdvancePolicy>(proto.advancePolicy),
            state = enumValueOf<NavigationSessionState>(proto.state),
        )
    }
}
