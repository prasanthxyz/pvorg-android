package com.pvorg.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameParserTest {
    @Test
    fun `parse extracts studio actors and title`() {
        val parsed = FilenameParser.parse("acme-jane-john-roadtrip.mp4")

        assertEquals("acme", parsed.studio)
        assertEquals(listOf("jane", "john"), parsed.actors)
        assertEquals("roadtrip", parsed.title)
    }

    @Test
    fun `parse falls back gracefully for short names`() {
        val parsed = FilenameParser.parse("roadtrip.mp4")

        assertEquals("roadtrip", parsed.title)
        assertEquals(emptyList<String>(), parsed.actors)
    }
}
