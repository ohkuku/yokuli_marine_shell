package com.yokuli.marine.data.catalog

import com.yokuli.marine.data.model.ChecksumTrust
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineObservation
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.ObservationOrigin
import com.yokuli.marine.data.model.ObservationValidity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.session.ActiveSessionRegistry
import com.yokuli.marine.data.time.MonotonicClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPressureTest {
    @Test
    fun allCatalogsRemainBoundedWhileManyEndedOriginsAreEvicted() {
        val sessions = ActiveSessionRegistry(maxActiveConnections = 1)
        val sentences = SentenceCatalog(sessions, maxEntries = 5)
        val observations = ObservationCatalog(FixedClock(2_000L), sessions, maxCandidates = 5)
        val raw = RawPreviewBuffer(sessions, maxEntries = 5, maxBytes = 100)

        repeat(1_000) { index ->
            val connection = ConnectionId("origin-$index")
            val source = SourceIdentity(connection)
            val generation = SessionGeneration(1)
            sessions.beginSession(connection, generation)
            sentences.record(
                SentenceCatalogEvent.fromOrigin(
                    origin = ObservationOrigin(source, generation, "GP", "RMC"),
                    receivedAtMillis = index.toLong(),
                    parseStatus = SentenceParseStatus.PARSED,
                ),
            )
            observations.record(
                MarineObservation(
                    key = DataKey.Position,
                    value = MarineValue.Position((index % 170) - 85.0, 174.0),
                    validity = ObservationValidity.VALID,
                    origin = ObservationOrigin(source, generation, "GP", "RMC"),
                    measuredAtMillis = index.toLong(),
                    groupId = ObservationGroupId(index.toLong()),
                    checksumTrust = ChecksumTrust.VERIFIED,
                ),
            )
            raw.record(
                RawPreviewEntry(
                    source = source,
                    sessionGeneration = generation,
                    sentenceId = "GPRMC",
                    receivedAtMillis = index.toLong(),
                    raw = "\$GPRMC,$index",
                ),
            )
            sessions.endSession(connection, generation)
            sessions.forgetConnection(connection)
        }

        assertEquals(5, sentences.snapshot().entries.size)
        assertEquals(995L, sentences.snapshot().capacityEvictionCount)
        assertEquals(5, observations.snapshot().candidates.size)
        assertEquals(995L, observations.snapshot().capacityEvictionCount)
        assertTrue(raw.snapshot().entries.size <= 5)
        assertTrue(raw.snapshot().retainedBytes <= 100)
        assertTrue(raw.snapshot().overflowEntryCount >= 995L)
        assertTrue(sessions.snapshot().activeSessions.isEmpty())
        assertEquals(1, sessions.snapshot().highWaterMark)
    }
}

private class FixedClock(private val now: Long) : MonotonicClock {
    override fun nowMillis(): Long = now
}
