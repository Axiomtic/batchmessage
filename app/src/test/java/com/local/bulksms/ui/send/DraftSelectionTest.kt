package com.local.bulksms.ui.send

import org.junit.Assert.assertEquals
import org.junit.Test

class DraftSelectionTest {
    @Test
    fun reconcilePreservesExistingChoicesAndSelectsOnlyNewRows() {
        val actual = reconcileDraftSelection(
            previousDraftIds = setOf(1L, 2L),
            previousSelectedIds = setOf(1L),
            newDraftIds = setOf(1L, 2L, 3L),
        )

        assertEquals(setOf(1L, 3L), actual)
    }

    @Test
    fun reconcileDropsRowsThatNoLongerExist() {
        val actual = reconcileDraftSelection(
            previousDraftIds = setOf(1L, 2L),
            previousSelectedIds = setOf(1L, 2L),
            newDraftIds = setOf(2L),
        )

        assertEquals(setOf(2L), actual)
    }
}
