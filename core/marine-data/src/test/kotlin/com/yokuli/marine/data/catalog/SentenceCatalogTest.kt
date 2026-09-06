package com.yokuli.marine.data.catalog

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.ObservationOrigin
import com.yokuli.marine.data.model.SenderIdentity
import com.yokuli.marine.data.model.SessionGeneration
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.model.UdpOriginIdentityPolicy
import com.yokuli.marine.data.session.ActiveSessionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceCatalogTest {
    @Test
    fun unknownLegalSentenceRemainsVisible() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, generation = 1)
        val catalog = SentenceCatalog(sessions)

        catalog.record(event(source, "GP", "XYZ", 100L, SentenceParseStatus.UNSUPPORTED))
        catalog.record(event(source, "GP", "XYZ", 200L, SentenceParseStatus.UNSUPPORTED))

        val entry = catalog.snapshot().entries.single()
        assertEquals(source, entry.key.source)
        assertEquals("GP", entry.key.talker)
        assertEquals("XYZ", entry.key.formatter)
        assertEquals(2L, entry.receivedCount)
        assertEquals(SentenceParseStatus.UNSUPPORTED, entry.lastParseStatus)
    }

    @Test
    fun separateSentenceKeysPreserveTalkerAndMwvReference() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("gateway"))
        sessions.begin(source, generation = 1)
        val catalog = SentenceCatalog(sessions)

        catalog.record(event(source, "GP", "RMC", 1L))
        catalog.record(event(source, "GN", "RMC", 2L))
        catalog.record(event(source, "II", "MWV", 3L, semanticInstance = "MWV:R"))
        catalog.record(event(source, "II", "MWV", 4L, semanticInstance = "MWV:T"))

        val keys = catalog.snapshot().entries.map { it.key }.toSet()
        assertEquals(4, keys.size)
        assertTrue(keys.any { it.talker == "GP" && it.formatter == "RMC" })
        assertTrue(keys.any { it.talker == "GN" && it.formatter == "RMC" })
        assertTrue(keys.any { it.semanticInstance == "MWV:R" })
        assertTrue(keys.any { it.semanticInstance == "MWV:T" })
    }

    @Test
    fun udpSendersRemainSeparateSentenceOriginsWhenPolicyIncludesPort() {
        val connection = ConnectionId("udp")
        val senderA = SenderIdentity("192.0.2.1", 10_001)
        val senderB = SenderIdentity("192.0.2.2", 10_001)
        val a = SourceIdentity.forUdp(
            connection,
            senderA,
            UdpOriginIdentityPolicy.HOST_AND_PORT,
        )
        val b = SourceIdentity.forUdp(
            connection,
            senderB,
            UdpOriginIdentityPolicy.HOST_AND_PORT,
        )
        val sessions = ActiveSessionRegistry()
        sessions.beginSession(connection, SessionGeneration(1))
        val catalog = SentenceCatalog(sessions)

        catalog.record(event(a, "GP", "RMC", 1L, sender = senderA))
        catalog.record(event(b, "GP", "RMC", 2L, sender = senderB))

        assertEquals(setOf(a, b), catalog.snapshot().entries.map { it.key.source }.toSet())
    }

    @Test
    fun quietNewSessionRejectsLateOldCallbacks() {
        val source = SourceIdentity(ConnectionId("tcp"))
        val sessions = ActiveSessionRegistry()
        sessions.begin(source, generation = 1)
        val catalog = SentenceCatalog(sessions)
        catalog.record(event(source, "GP", "RMC", 10L, generation = 1))

        // Generation 2 is authoritative before it has received a single frame.
        sessions.begin(source, generation = 2)
        assertFalse(catalog.snapshot().entries.single().isCurrentSession)
        catalog.record(event(source, "GP", "RMC", 20L, generation = 1))

        val snapshot = catalog.snapshot()
        assertEquals(SessionGeneration(1), snapshot.entries.single().sessionGeneration)
        assertEquals(1L, snapshot.entries.single().receivedCount)
        assertFalse(snapshot.entries.single().isCurrentSession)
        assertEquals(1L, snapshot.staleSessionDropCount)
    }

    @Test
    fun connectionGenerationAppliesAcrossUdpSendersAndRawPreview() {
        val connection = ConnectionId("udp")
        val observedA = SenderIdentity("192.0.2.1", 9_001)
        val observedB = SenderIdentity("192.0.2.2", 9_002)
        val senderA = SourceIdentity.forUdp(
            connection,
            observedA,
            UdpOriginIdentityPolicy.HOST_ADDRESS,
        )
        val senderB = SourceIdentity.forUdp(
            connection,
            observedB,
            UdpOriginIdentityPolicy.HOST_ADDRESS,
        )
        val sessions = ActiveSessionRegistry()
        sessions.beginSession(connection, SessionGeneration(1))
        val sentences = SentenceCatalog(sessions)
        val raw = RawPreviewBuffer(sessions)
        sentences.record(event(senderA, "GP", "RMC", 1L, generation = 1, sender = observedA))
        raw.record(raw(senderA, "old-a", 1L, generation = 1))

        sessions.beginSession(connection, SessionGeneration(2))
        sentences.record(
            event(
                senderB,
                "II",
                "MWV",
                2L,
                generation = 1,
                semanticInstance = "MWV:R",
                sender = observedB,
            ),
        )
        raw.record(raw(senderB, "late-old-b", 2L, generation = 1))
        sentences.record(
            event(
                senderB,
                "II",
                "MWV",
                3L,
                generation = 2,
                semanticInstance = "MWV:R",
                sender = observedB,
            ),
        )
        raw.record(raw(senderB, "new-b", 3L, generation = 2))

        val sentenceSnapshot = sentences.snapshot()
        assertEquals(2, sentenceSnapshot.entries.size)
        assertFalse(sentenceSnapshot.entries.single { it.key.source == senderA }.isCurrentSession)
        assertTrue(sentenceSnapshot.entries.single { it.key.source == senderB }.isCurrentSession)
        assertEquals(1L, sentenceSnapshot.staleSessionDropCount)
        val rawSnapshot = raw.snapshot()
        assertEquals(listOf("old-a", "new-b"), rawSnapshot.entries.map { it.raw })
        assertFalse(rawSnapshot.entries.first().isCurrentSession)
        assertTrue(rawSnapshot.entries.last().isCurrentSession)
        assertEquals(1L, rawSnapshot.staleSessionDropCount)
    }

    @Test
    fun catalogIsBoundedAndReportsEvictionWithoutTouchingProtectedEntries() {
        val sessions = ActiveSessionRegistry()
        val one = SourceIdentity(ConnectionId("one"))
        val two = SourceIdentity(ConnectionId("two"))
        val three = SourceIdentity(ConnectionId("three"))
        listOf(one, two, three).forEach { sessions.begin(it, 1) }
        val catalog = SentenceCatalog(sessions, maxEntries = 2)
        val protected = SentenceCatalogKey(one, "GP", "RMC")
        catalog.record(event(one, "GP", "RMC", 1L))
        catalog.record(event(two, "GP", "GGA", 2L))
        catalog.record(
            event(three, "II", "MWV", 3L, semanticInstance = "MWV:R"),
            protectedKeys = setOf(protected),
        )

        val snapshot = catalog.snapshot()
        assertEquals(2, snapshot.entries.size)
        assertTrue(snapshot.entries.any { it.key == protected })
        assertTrue(snapshot.entries.any { it.key.source.connectionId.value == "three" })
        assertEquals(1L, snapshot.capacityEvictionCount)
    }

    @Test
    fun catalogRejectsANewEntryWhenEveryExistingEntryIsProtected() {
        val sessions = ActiveSessionRegistry()
        val one = SourceIdentity(ConnectionId("one"))
        val two = SourceIdentity(ConnectionId("two"))
        sessions.begin(one, 1)
        sessions.begin(two, 1)
        val catalog = SentenceCatalog(sessions, maxEntries = 1)
        val existing = SentenceCatalogKey(one, "GP", "RMC")
        catalog.record(event(one, "GP", "RMC", 1L))

        catalog.record(event(two, "GP", "GGA", 2L), protectedKeys = setOf(existing))

        val snapshot = catalog.snapshot()
        assertEquals(listOf(existing), snapshot.entries.map { it.key })
        assertEquals(1L, snapshot.capacityRejectionCount)
    }

    @Test
    fun snapshotsCannotMutateCatalogState() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("one"))
        sessions.begin(source, 1)
        val catalog = SentenceCatalog(sessions)
        catalog.record(event(source, "GP", "RMC", 1L))
        val copied = catalog.snapshot().entries.toMutableList()
        copied.clear()

        assertEquals(1, catalog.snapshot().entries.size)
    }

    private fun event(
        source: SourceIdentity,
        talker: String,
        formatter: String,
        at: Long,
        status: SentenceParseStatus = SentenceParseStatus.PARSED,
        generation: Long = 1L,
        semanticInstance: String = DEFAULT_SENTENCE_SEMANTIC_INSTANCE,
        sender: SenderIdentity? = null,
    ) = SentenceCatalogEvent.fromOrigin(
        origin = ObservationOrigin(source, SessionGeneration(generation), talker, formatter, sender),
        receivedAtMillis = at,
        parseStatus = status,
        semanticInstance = semanticInstance,
    )
}

class RawPreviewBufferTest {
    @Test
    fun defaultBudgetsMatchThePhaseContract() {
        assertEquals(200, RawPreviewBuffer.DEFAULT_MAX_ENTRIES)
        assertEquals(256 * 1024, RawPreviewBuffer.DEFAULT_MAX_BYTES)
    }

    @Test
    fun catalogAndRawPreviewRemainBounded() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("diagnostic"))
        sessions.begin(source, 1)
        val buffer = RawPreviewBuffer(sessions, maxEntries = 3, maxBytes = 10)

        buffer.record(raw(source, "1234", 1L))
        buffer.record(raw(source, "5678", 2L))
        buffer.record(raw(source, "海", 3L)) // Three UTF-8 bytes.
        buffer.record(raw(source, "90", 4L))

        val snapshot = buffer.snapshot()
        assertTrue(snapshot.entries.size <= 3)
        assertTrue(snapshot.retainedBytes <= 10)
        assertEquals(snapshot.entries.sumOf { it.utf8Bytes }, snapshot.retainedBytes)
        assertTrue(snapshot.overflowEntryCount > 0L)
    }

    @Test
    fun oneOversizePreviewIsRejectedAndObservable() {
        val sessions = ActiveSessionRegistry()
        val source = SourceIdentity(ConnectionId("diagnostic"))
        sessions.begin(source, 1)
        val buffer = RawPreviewBuffer(sessions, maxEntries = 2, maxBytes = 3)

        buffer.record(raw(source, "1234", 1L))

        val snapshot = buffer.snapshot()
        assertTrue(snapshot.entries.isEmpty())
        assertEquals(1L, snapshot.oversizeRejectionCount)
        assertEquals(1L, snapshot.overflowEntryCount)
        assertFalse(snapshot.retainedBytes > 3)
    }
}

private fun ActiveSessionRegistry.begin(source: SourceIdentity, generation: Long) {
    beginSession(source.connectionId, SessionGeneration(generation))
}

private fun raw(
    source: SourceIdentity,
    text: String,
    at: Long,
    generation: Long = 1L,
) = RawPreviewEntry(
    source = source,
    sessionGeneration = SessionGeneration(generation),
    sentenceId = "GPRMC",
    receivedAtMillis = at,
    raw = text,
)
