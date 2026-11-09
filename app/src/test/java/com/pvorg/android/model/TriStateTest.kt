package com.pvorg.android.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TriStateTest {
    @Test
    fun `next cycles through the expected mapper states`() {
        assertEquals(TriState.NO, TriState.YES.next)
        assertEquals(TriState.UNDECIDED, TriState.NO.next)
        assertEquals(TriState.YES, TriState.UNDECIDED.next)
    }
}
