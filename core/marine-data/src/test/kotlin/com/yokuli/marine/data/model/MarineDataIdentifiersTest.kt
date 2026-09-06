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
    fun validObservationsRejectMismatchedValueKindsUnitsAndRanges() {
        val origin = ObservationOrigin(
            SourceIdentity(ConnectionId("gateway")),
            SessionGeneration(1),
            "GP",
            "RMC",
        )

        fun rejects(key: DataKey, value: MarineValue) {
            assertThrows(IllegalArgumentException::class.java) {
                MarineObservation(
                    key = key,
                    value = value,
                    validity = ObservationValidity.VALID,
                    origin = origin,
                    measuredAtMillis = 10,
                    groupId = ObservationGroupId(1),
                    checksumTrust = ChecksumTrust.VERIFIED,
                )
            }
        }

        rejects(DataKey.Position, MarineValue.Decimal(1.0, MarineUnit.DEGREES))
        rejects(DataKey.SpeedOverGround, MarineValue.Position(-36.8, 174.7))
        rejects(DataKey.SpeedOverGround, MarineValue.Decimal(4.0, MarineUnit.METERS))
        rejects(DataKey.SpeedOverGround, MarineValue.Decimal(-1.0, MarineUnit.KNOTS))
        rejects(DataKey.CourseOverGround, MarineValue.Decimal(360.0, MarineUnit.DEGREES))
        rejects(DataKey.Heading(HeadingReference.TRUE), MarineValue.Decimal(12.0, MarineUnit.KNOTS))
        rejects(DataKey.Depth(DepthReference.BELOW_KEEL), MarineValue.Decimal(-0.1, MarineUnit.METERS))
        rejects(DataKey.WindAngle(WindReference.APPARENT), MarineValue.Decimal(361.0, MarineUnit.DEGREES))
        rejects(DataKey.WindSpeed(WindSpeedReference.TRUE), MarineValue.Decimal(2.0, MarineUnit.METERS))
        rejects(DataKey.MagneticVariation, MarineValue.Decimal(181.0, MarineUnit.DEGREES))
        rejects(DataKey.SourceTime, MarineValue.Count(1))
        rejects(DataKey.FixQuality, MarineValue.Count(9))
        rejects(DataKey.Satellites, MarineValue.Decimal(8.0, MarineUnit.DIMENSIONLESS))
        rejects(DataKey.HorizontalDilution, MarineValue.Decimal(-0.1, MarineUnit.DIMENSIONLESS))
        rejects(DataKey.Altitude, MarineValue.Decimal(1.0, MarineUnit.KNOTS))
    }

    @Test
    fun countsCannotBeNegativeAndSourceTimeEvidenceCannotContradictItsValue() {
        assertThrows(IllegalArgumentException::class.java) { MarineValue.Count(-1) }

        val origin = ObservationOrigin(
            SourceIdentity(ConnectionId("gateway")),
            SessionGeneration(1),
            "GP",
            "ZDA",
        )
        assertThrows(IllegalArgumentException::class.java) {
            MarineObservation(
                key = DataKey.SourceTime,
                value = MarineValue.UtcEpochMillis(100L),
                validity = ObservationValidity.VALID,
                origin = origin,
                measuredAtMillis = 10,
                groupId = ObservationGroupId(1),
                checksumTrust = ChecksumTrust.VERIFIED,
                sourceTimeEpochMillis = 200L,
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
