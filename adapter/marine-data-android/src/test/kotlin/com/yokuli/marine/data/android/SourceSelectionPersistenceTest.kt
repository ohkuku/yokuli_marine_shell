package com.yokuli.marine.data.android

import com.yokuli.marine.data.android.persistence.ProtoDataStoreSourceSelectionRepository
import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.model.SourceIdentity
import com.yokuli.marine.data.source.PersistedSourceSelections
import com.yokuli.marine.data.source.SelectionReason
import com.yokuli.marine.data.source.SourcePreference
import com.yokuli.marine.data.source.SourceSelectionLoadResult
import com.yokuli.marine.data.source.SourceSelectionSaveResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SourceSelectionPersistenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun selectionAndDisableSurviveRestartWithoutAnyLiveValue() = runBlocking {
        val file = File(temporaryFolder.newFolder("selection"), "source-selection.pb")
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + Dispatchers.IO)
        val first = ProtoDataStoreSourceSelectionRepository.create(file, firstScope)
        val source = SourceIdentity(ConnectionId("stable-uuid"))
        val preferences = mapOf(
            DataKey.Position to SourcePreference.Selected(source, SelectionReason.USER),
            DataKey.SpeedOverGround to SourcePreference.Disabled,
        )
        val saved = first.save(expectedRevision = 0L, preferences = preferences)
        assertTrue(saved is SourceSelectionSaveResult.Saved)
        firstJob.cancelAndJoin()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val restored = ProtoDataStoreSourceSelectionRepository.create(file, secondScope)
            val loaded = restored.load() as SourceSelectionLoadResult.Loaded
            assertEquals(preferences, loaded.state.preferences)
            assertEquals(1L, loaded.state.revision)
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun staleExpectedRevisionCannotOverwriteNewerSelection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = ProtoDataStoreSourceSelectionRepository.create(
                File(temporaryFolder.newFolder("conflict"), "source-selection.pb"),
                scope,
            )
            assertTrue(repository.load() is SourceSelectionLoadResult.Loaded)
            val source = SourceIdentity(ConnectionId("a"))
            assertTrue(
                repository.save(
                    0L,
                    mapOf(DataKey.Position to SourcePreference.Selected(source, SelectionReason.USER)),
                ) is SourceSelectionSaveResult.Saved,
            )

            val conflict = repository.save(0L, mapOf(DataKey.Position to SourcePreference.Disabled))
            assertTrue(conflict is SourceSelectionSaveResult.Conflict)
            assertEquals(source, (repository.load() as SourceSelectionLoadResult.Loaded)
                .state.preferences.getValue(DataKey.Position).let { it as SourcePreference.Selected }.source)
        } finally {
            scope.cancel()
        }
    }
}
