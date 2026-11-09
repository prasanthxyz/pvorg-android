package com.pvorg.android.state

import com.pvorg.android.domain.MappingKind
import com.pvorg.android.domain.MappingRow
import com.pvorg.android.domain.TestLibraries
import com.pvorg.android.domain.TriState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingSessionTest {
    private val library = TestLibraries.library(listOf("acme-a-one.mp4", "acme-b-two.mp4"))
    private val rows = library.videos.map { MappingRow(it, mapOf("beach" to TriState.YES, "city" to TriState.UNDECIDED)) }

    @Test fun cyclingBackToTheSavedValueClearsThePendingChange() {
        var session = MappingSession(MappingKind.GALLERIES)
        session = session.cycling(rows[0], "city")
        assertEquals(TriState.YES, session.state(rows[0], "city"))
        assertTrue(session.isModified(rows[0], "city"))
        session = session.cycling(rows[0], "city").cycling(rows[0], "city")
        assertFalse(session.hasChanges)
    }

    @Test fun bulkActionsCountOnlyRealChanges() {
        val session = MappingSession(MappingKind.GALLERIES).settingColumn(TriState.YES, "beach", rows)
        assertFalse(session.hasChanges)
        val row = MappingSession(MappingKind.GALLERIES).settingRow(TriState.NO, rows[1], listOf("beach", "city"))
        assertEquals(2, row.changeCount)
        assertEquals(0, row.reverted().changeCount)
    }

    @Test fun pruningDropsVanishedVideosAndColumns() {
        val session = MappingSession(
            MappingKind.GALLERIES,
            pending = mapOf("acme-a-one.mp4" to mapOf("beach" to TriState.NO, "gone" to TriState.YES), "gone.mp4" to mapOf("city" to TriState.YES)),
        )
        assertEquals(mapOf("acme-a-one.mp4" to mapOf("beach" to TriState.NO)), session.pruned(library).pending)
    }

    @Test fun selectionKeepsOnlyVisibleItems() {
        assertEquals(setOf("a"), LibrarySelection.pruned(setOf("a", "b"), listOf("a", "c")))
    }
}
