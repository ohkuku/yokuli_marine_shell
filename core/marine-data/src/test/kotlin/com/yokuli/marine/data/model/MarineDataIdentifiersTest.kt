package com.yokuli.marine.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MarineDataIdentifiersTest {
    @Test
    fun connectionIdentityIsIndependentFromDisplayNameAndSession() {
        val connection = ConnectionId("c-123")
        val source = SourceIdentity(connection)

        assertEquals(source, SourceIdentity(ConnectionId("c-123")))
        assertEquals(
            source,
            ObservationOrigin(source, SessionGeneration(8), "GP", "RMC").source,
        )
        assertNotEquals(source, SourceIdentity(ConnectionId("c-456")))
    }

    @Test
    fun talkerIsProvenanceAndNeverPhysicalSourceIdentity() {
        val source = SourceIdentity(ConnectionId("gateway"))
        val gp = ObservationOrigin(source, SessionGeneration(1), "GP", "RMC")
        val gn = ObservationOrigin(source, SessionGeneration(1), "GN", "GGA")

        assertEquals(gp.source, gn.source)
        assertNotEquals(gp.sentenceId, gn.sentenceId)
    }

    @Test
    fun udpHostIdentitySurvivesEphemeralPortChange() {
        val listener = ConnectionId("udp-10110")
        val first = SenderIdentity("192.0.2.10", 50_000)
        val sameHostNewPort = SenderIdentity("192.0.2.10", 50_001)
        val otherHost = SenderIdentity("192.0.2.11", 50_000)

        val hostOnlyA = UdpOriginIdentityPolicy.HOST_ADDRESS.sourceIdentity(listener, first)
        val hostOnlyB = UdpOriginIdentityPolicy.HOST_ADDRESS.sourceIdentity(listener, sameHostNewPort)
        val hostOnlyOther = UdpOriginIdentityPolicy.HOST_ADDRESS.sourceIdentity(listener, otherHost)
        val endpointA = UdpOriginIdentityPolicy.HOST_AND_PORT.sourceIdentity(listener, first)
        val endpointB = UdpOriginIdentityPolicy.HOST_AND_PORT.sourceIdentity(listener, sameHostNewPort)

        assertEquals(hostOnlyA, hostOnlyB)
        assertEquals(
            CandidateId(DataKey.Position, hostOnlyA),
            CandidateId(DataKey.Position, hostOnlyB),
        )
        assertNotEquals(hostOnlyA, hostOnlyOther)
        assertNotEquals(endpointA, endpointB)
        assertNotEquals(hostOnlyA, endpointA)

        val provenance = ObservationOrigin(
            source = hostOnlyA,
            sessionGeneration = SessionGeneration(2),
            talker = "GP",
            formatter = "RMC",
            sender = sameHostNewPort,
        )
        assertEquals(sameHostNewPort, provenance.sender)
    }

    @Test
    fun udpOriginRejectsMismatchedOrMissingProvenance() {
        val connection = ConnectionId("udp-10110")
        val expected = SenderIdentity("192.0.2.10", 50_000)
        val hostIdentity = SourceIdentity.forUdp(
            connection,
            expected,
            UdpOriginIdentityPolicy.HOST_ADDRESS,
        )
        val endpointIdentity = SourceIdentity.forUdp(
            connection,
            expected,
            UdpOriginIdentityPolicy.HOST_AND_PORT,
        )

        assertThrows(IllegalArgumentException::class.java) {
            ObservationOrigin(hostIdentity, SessionGeneration(1), "GP", "RMC")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObservationOrigin(
                hostIdentity,
                SessionGeneration(1),
                "GP",
                "RMC",
                SenderIdentity("192.0.2.11", 50_000),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObservationOrigin(
                endpointIdentity,
                SessionGeneration(1),
                "GP",
                "RMC",
                SenderIdentity("192.0.2.10", 50_001),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObservationOrigin(
                SourceIdentity(connection),
                SessionGeneration(1),
                "GP",
                "RMC",
                expected,
            )
        }
    }

    @Test
    fun referencesCannotCollapseDifferentMarineSemantics() {
        assertNotEquals(
            DataKey.Heading(HeadingReference.TRUE),
            DataKey.Heading(HeadingReference.MAGNETIC),
        )
        assertNotEquals(
            DataKey.Depth(DepthReference.BELOW_TRANSDUCER),
            DataKey.Depth(DepthReference.BELOW_KEEL),
        )
        assertNotEquals(
            DataKey.WindAngle(WindReference.APPARENT),
            DataKey.WindAngle(WindReference.TRUE_RELATIVE),
        )
    }

    @Test
    fun invalidObservationsCannotCarryValuesAndValidOnesCannotBeBlank() {
        val origin = ObservationOrigin(
            SourceIdentity(ConnectionId("gateway")),
            SessionGeneration(1),
            "GP",
            "RMC",
        )

        assertThrows(IllegalArgumentException::class.java) {
            MarineObservation(
                DataKey.Position,
                null,
                ObservationValidity.VALID,
                origin,
                10,
                ObservationGroupId(1),
                ChecksumTrust.VERIFIED,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarineObservation(
                DataKey.Position,
                MarineValue.Position(-36.8, 174.7),
                ObservationValidity.EXPLICIT_INVALID,
                origin,
                10,
                ObservationGroupId(1),
                ChecksumTrust.VERIFIED,
            )
        }
    }

    @Test
    fun identifiersRejectAmbiguousOrImpossibleValues() {
        assertThrows(IllegalArgumentException::class.java) { ConnectionId(" ") }
        assertThrows(IllegalArgumentException::class.java) { SessionGeneration(-1) }
        assertThrows(IllegalArgumentException::class.java) { ObservationGroupId(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            SenderIdentity("192.0.2.1", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarineValue.Position(91.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarineValue.Decimal(Double.NaN, MarineUnit.KNOTS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObservationOrigin(
                SourceIdentity(ConnectionId("gateway")),
                SessionGeneration(1),
                "gp",
                "RMC",
            )
        }
        assertNotEquals(
            DataKey.WindSpeed(WindSpeedReference.TRUE),
            DataKey.WindSpeed(WindSpeedReference.APPARENT),
        )
    }
}
