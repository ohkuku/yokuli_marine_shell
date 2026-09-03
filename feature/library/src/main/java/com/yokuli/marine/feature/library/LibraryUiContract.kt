package com.yokuli.marine.feature.library

import com.yokuli.marine.core.model.LibrarySection

data class LibraryUiState(
    val section: LibrarySection,
    val counts: Map<LibrarySection, Int>,
)

sealed interface LibraryUiAction {
    data class SelectSection(val section: LibrarySection) : LibraryUiAction
    data object Home : LibraryUiAction
}

/** 中文：未接入存储层的 UI 样例计数。 English: UI fixture counts before storage integration. */
object LibraryUiFixtures {
    fun state(section: LibrarySection) = LibraryUiState(
        section,
        mapOf(
            LibrarySection.PLACES to 12,
            LibrarySection.ROUTES to 3,
            LibrarySection.TRIPS to 27,
            LibrarySection.ANCHORS to 18,
            LibrarySection.SURVEYS to 4,
        ),
    )
}
