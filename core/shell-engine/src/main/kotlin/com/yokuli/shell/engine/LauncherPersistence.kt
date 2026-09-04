package com.yokuli.shell.engine

import com.yokuli.shell.engine.layout.StartDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface LauncherPersistencePort {
    val document: StateFlow<StartDocument?>
    suspend fun saveDocument(document: StartDocument)
}

class InMemoryLauncherPersistence(initialDocument: StartDocument? = null) : LauncherPersistencePort {
    private val mutableDocument = MutableStateFlow(initialDocument)
    override val document: StateFlow<StartDocument?> = mutableDocument

    override suspend fun saveDocument(document: StartDocument) {
        mutableDocument.value = document
    }
}
