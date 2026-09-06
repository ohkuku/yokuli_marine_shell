package com.yokuli.marine.map.storage

import com.yokuli.marine.map.storage.proto.ActiveNavigationSessionProto
import com.yokuli.marine.navigation.domain.ActiveNavigationSession
import com.yokuli.marine.navigation.domain.NavigationAdvancePolicy
import com.yokuli.marine.navigation.domain.NavigationSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ActiveNavigationSessionProtoMapperTest {
    @Test
    fun `session proto round trip keeps exact route revision policy and state`() {
        val session = ActiveNavigationSession(
            "route", 7, 1_234, 2, 80.0, NavigationAdvancePolicy.ARRIVAL_RADIUS, NavigationSessionState.PAUSED,
        )
        assertEquals(session, ActiveNavigationSessionProtoMapper.decode(ActiveNavigationSessionProtoMapper.encode(session)))
        assertNull(ActiveNavigationSessionProtoMapper.decode(ActiveNavigationSessionProtoMapper.encode(null)))
    }

    @Test
    fun `future schema and invalid enum are rejected instead of cleared`() {
        assertThrows(IllegalArgumentException::class.java) {
            ActiveNavigationSessionProtoMapper.decode(
                ActiveNavigationSessionProto.newBuilder().setSchemaVersion(2).setHasSession(false).build(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ActiveNavigationSessionProtoMapper.decode(
                ActiveNavigationSessionProtoMapper.encode(null).toBuilder()
                    .setHasSession(true).setRouteId("route").setRouteRevision(1).setArrivalRadiusMeters(50.0)
                    .setAdvancePolicy("UNKNOWN").setState("ACTIVE").build(),
            )
        }
    }
}
