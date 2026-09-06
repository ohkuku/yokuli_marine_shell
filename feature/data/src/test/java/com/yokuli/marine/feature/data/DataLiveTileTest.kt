package com.yokuli.marine.feature.data

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineUnit
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.runtime.NmeaRuntimeSnapshot
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.ResolvedDataSnapshot
import com.yokuli.marine.data.source.ResolvedDatum
import com.yokuli.marine.data.source.SelectionReason
import com.yokuli.marine.data.source.SourceCandidate
import com.yokuli.marine.data.source.SourceCandidateAvailability
import com.yokuli.marine.data.source.SourceCatalogSnapshot
import com.yokuli.marine.data.source.SourceDescriptor
import com.yokuli.marine.data.source.SourceEvidence
import com.yokuli.marine.data.source.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DataLiveTileTest {
    @Test fun `tile projects bounded resolved values rather than raw sentence traffic`() {
        val source = SourceIdentity(ConnectionId("boat-gateway"))
        val values = linkedMapOf(
            DataKey.Position to MarineValue.Position(-36.8, 174.7),
            DataKey.SpeedOverGround to MarineValue.Decimal(6.2, MarineUnit.KNOTS),
            DataKey.CourseOverGround to MarineValue.Decimal(91.0, MarineUnit.DEGREES),
            DataKey.SourceTime to MarineValue.UtcEpochMillis(1_700_000_000_000L),
        )
        val candidates = values.map { (key, value) ->
            SourceCandidate(
                id = CandidateId(key, source),
                descriptor = SourceDescriptor(source, SourceKind.NMEA, "Boat Gateway", "RMC"),
                value = value,
                lastValidValue = value,
                availability = SourceCandidateAvailability.LIVE,
                ageMillis = 0L,
                receivedAtMillis = 10L,
                groupId = ObservationGroupId(1L),
                evidence = SourceEvidence.Nmea(setOf("GPRMC"), setOf("RMC")),
            )
        }
        val resolved = candidates.associate { candidate ->
            candidate.id.key to ResolvedDatum(
                key = candidate.id.key,
                source = source,
                candidate = candidate,
                value = candidate.value,
                availability = candidate.availability,
                selectionReason = SelectionReason.USER,
                selectionRevision = 1L,
            )
        }
        val snapshot = MarineSourceSnapshot.EMPTY.copy(
            sourceCatalog = SourceCatalogSnapshot(candidates, 10L, 1L),
            resolvedData = ResolvedDataSnapshot(resolved, 1L, 10L),
            selectionRevision = 1L,
            revision = 1L,
        )

        val tile = DataLauncherProjector.project(NmeaRuntimeSnapshot.EMPTY, snapshot)

        assertEquals(listOf(DataTileValueKind.POSITION, DataTileValueKind.COURSE_SPEED), tile.importantValues.map { it.kind })
        assertEquals(2, tile.importantValues.size)
        assertFalse(tile.importantValues.any { "RMC" in it.value || "GPRMC" in it.value })
    }
}
