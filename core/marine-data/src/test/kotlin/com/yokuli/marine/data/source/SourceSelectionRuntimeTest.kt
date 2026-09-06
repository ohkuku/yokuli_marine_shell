package com.yokuli.marine.data.source

import com.yokuli.marine.data.model.CandidateId
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.MarineValue
import com.yokuli.marine.data.model.ObservationGroupId
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.time.MonotonicClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSelectionRuntimeTest {
    private val clock = RuntimeClock(0L)
    private val key = DataKey.Position
    private val sourceA = SourceIdentity(ConnectionId("a"))
    private val sourceB = SourceIdentity(ConnectionId("b"))

    @Test
    fun persistenceFailureKeepsTheOldSelectionAndResolvedValueAtomically() = runBlocking {
        val repository = FakeSelectionRepository()
        val runtime = DefaultMarineSourceRuntime(repository, clock)
        runtime.initialize()
        runtime.updateCatalog(catalog(candidate(sourceA, 1.0), candidate(sourceB, 2.0)))
        assertTrue(runtime.execute(SourceSelectionCommand.Select(key, sourceA)) is SourceSelectionCommandResult.Success)
        val before = runtime.state.value

        repository.failNextSave = true
        val result = runtime.execute(SourceSelectionCommand.Select(key, sourceB))

        assertEquals(SourceSelectionFailure.PERSISTENCE_FAILED, (result as SourceSelectionCommandResult.Rejected).failure)
        assertEquals(before.selectionRevision, runtime.state.value.selectionRevision)
        assertEquals(sourceA, runtime.state.value.resolvedData.items.getValue(key).source)
        assertEquals(sourceA, repository.current.preferences.getValue(key).selectedSourceOrNull())
    }

    @Test
    fun restartRestoresPreferenceButNeverResurrectsLiveValue() = runBlocking {
        val repository = FakeSelectionRepository(
            PersistedSourceSelections(
                revision = 4L,
                preferences = mapOf(key to SourcePreference.Selected(sourceA, SelectionReason.USER)),
            ),
        )
        val runtime = DefaultMarineSourceRuntime(repository, clock)
        runtime.initialize()

        assertEquals(sourceA, runtime.state.value.decisions.single().selectedSource)
        assertEquals(SourceDecisionStatus.SELECTED_MISSING, runtime.state.value.decisions.single().status)
        assertNull(runtime.state.value.resolvedData.items.getValue(key).value)
    }

    @Test
    fun renamingADescriptorDoesNotLosePreferenceButDeletingSourceIsTruthful() = runBlocking {
        val repository = FakeSelectionRepository()
        val runtime = DefaultMarineSourceRuntime(repository, clock)
        runtime.initialize()
        runtime.updateCatalog(catalog(candidate(sourceA, 1.0, "Gateway")))
        assertTrue(runtime.execute(SourceSelectionCommand.Select(key, sourceA)) is SourceSelectionCommandResult.Success)

        runtime.updateCatalog(catalog(candidate(sourceA, 1.1, "Renamed gateway")))
        assertEquals("Renamed gateway", runtime.state.value.resolvedData.items.getValue(key).candidate?.descriptor?.displayName)
        assertEquals(sourceA, runtime.state.value.resolvedData.items.getValue(key).source)

        runtime.updateCatalog(SourceCatalogSnapshot(emptyList(), clock.nowMillis(), revision = 2L))
        val missing = runtime.state.value.resolvedData.items.getValue(key)
        assertEquals(sourceA, missing.source)
        assertEquals(SourceCandidateAvailability.MISSING, missing.availability)
        assertEquals(SourceDecisionStatus.SELECTED_MISSING, runtime.state.value.decisions.single().status)
    }

    @Test
    fun aLateSubscriberImmediatelyReceivesOneAtomicSourceAndValueSnapshot() = runBlocking {
        val runtime = DefaultMarineSourceRuntime(FakeSelectionRepository(), clock)
        runtime.initialize()
        runtime.updateCatalog(catalog(candidate(sourceA, 1.0), candidate(sourceB, 2.0)))
        runtime.execute(SourceSelectionCommand.Select(key, sourceB))

        val late = runtime.state.value
        val resolved = late.resolvedData.items.getValue(key)
        assertEquals(sourceB, resolved.source)
        assertEquals("b", resolved.candidate?.descriptor?.displayName)
        assertEquals(2.0, (resolved.value as MarineValue.Position).latitudeDegrees, 0.0)
        assertEquals(late.selectionRevision, resolved.selectionRevision)
    }

    private fun catalog(vararg candidates: SourceCandidate) = SourceCatalogSnapshot(
        candidates = candidates.toList(),
        evaluatedAtMillis = clock.nowMillis(),
        revision = 1L,
    )

    private fun candidate(source: SourceIdentity, latitude: Double, name: String = source.connectionId.value) =
        SourceCandidate(
            id = CandidateId(key, source),
            descriptor = SourceDescriptor(source, SourceKind.NMEA, name, "RMC"),
            value = MarineValue.Position(latitude, 174.0),
            lastValidValue = MarineValue.Position(latitude, 174.0),
            availability = SourceCandidateAvailability.LIVE,
            ageMillis = 0L,
            receivedAtMillis = clock.nowMillis(),
            groupId = ObservationGroupId(1L),
            evidence = SourceEvidence.Nmea(setOf("GPRMC"), setOf("RMC")),
        )
}

private class RuntimeClock(var now: Long) : MonotonicClock {
    override fun nowMillis(): Long = now
}

private class FakeSelectionRepository(
    initial: PersistedSourceSelections = PersistedSourceSelections(),
) : SourceSelectionRepository {
    var current = initial
    var failNextSave = false

    override suspend fun load(): SourceSelectionLoadResult = SourceSelectionLoadResult.Loaded(current)

    override suspend fun save(
        expectedRevision: Long,
        preferences: Map<DataKey, SourcePreference>,
    ): SourceSelectionSaveResult {
        if (failNextSave) {
            failNextSave = false
            return SourceSelectionSaveResult.Failed(IllegalStateException("disk full"))
        }
        if (expectedRevision != current.revision) return SourceSelectionSaveResult.Conflict(current)
        current = PersistedSourceSelections(
            revision = expectedRevision + 1L,
            preferences = preferences,
        )
        return SourceSelectionSaveResult.Saved(current)
    }
}

private fun SourcePreference.selectedSourceOrNull(): SourceIdentity? =
    (this as? SourcePreference.Selected)?.source
