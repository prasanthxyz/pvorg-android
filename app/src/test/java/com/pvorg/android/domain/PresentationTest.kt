package com.pvorg.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationTest {
    @Test fun parsesStudioCastAndTitle() {
        val parsed = FilenameRules.parse("acme-jane_doe-john_roe-sunrise_ridge.mp4")
        assertEquals("acme", parsed.studio)
        assertEquals(listOf("jane_doe", "john_roe"), parsed.actors)
        assertEquals("sunrise_ridge", parsed.title)
    }

    @Test fun reportsEveryNamingProblem() {
        assertEquals(emptyList<String>(), FilenameRules.problems("acme-jane-one.mp4"))
        assertEquals(
            listOf("Contains uppercase characters", "Contains spaces", "Fewer than 3 dash-separated parts (need studio-cast-title)"),
            FilenameRules.problems("Bad Name.mp4"),
        )
    }

    @Test fun recognizesExtensionsCaseInsensitively() {
        assertTrue(FilenameRules.isVideo("a-b-c.MKV"))
        assertTrue(FilenameRules.isVideo("a-b-c.wmv"))
        assertFalse(FilenameRules.isVideo(".hidden-a-b.mp4"))
        assertFalse(FilenameRules.isVideo("cache.json"))
        assertTrue(FilenameRules.isImage("1.JPEG"))
        assertFalse(FilenameRules.isImage("notes.txt"))
    }

    @Test fun humanizesTokens() {
        assertEquals("Jane Doe", DisplayName.humanize("jane_doe"))
        assertEquals("Sunrise Ridge", DisplayName.humanize("sunrise__ridge"))
    }

    @Test fun formatsCreditsWithSerialComma() {
        val video = TestLibraries.library(listOf("acme-ann-bea-cat-title.mp4")).videos.single()
        assertEquals("Acme · Ann, Bea, and Cat", DisplayName.credits(video))
        assertEquals("Ann and Bea", DisplayName.joinList(listOf("Ann", "Bea")))
        assertEquals("Title", DisplayName.title(video))
    }

    @Test fun formatsCountsAndTimecodes() {
        assertEquals("1 video", Format.counted(1, "video"))
        assertEquals("3 videos", Format.counted(3, "video"))
        assertEquals("0:07", Format.timecode(7_400))
        assertEquals("12:34", Format.timecode(754_000))
        assertEquals("1:02:03", Format.timecode(3_723_000))
    }

    @Test fun searchIgnoresCaseAndDiacritics() {
        val video = TestLibraries.library(listOf("acme-zoe-cafe_noir.mp4")).videos.single()
        assertTrue(Search.matches(video, "CAFÉ"))
        assertTrue(Search.matches(video, "zoe"))
        assertTrue(Search.matches(video, "  "))
        assertFalse(Search.matches(video, "beach"))
        assertTrue(Search.matches("Café", "cafe"))
    }

    @Test fun sortsByHumanizedTitleThenFilename() {
        val library = TestLibraries.library(listOf("zeta-a-beta.mp4", "acme-b-alpha.mp4", "acme-a-beta.mp4"))
        assertEquals(listOf("acme-b-alpha.mp4", "acme-a-beta.mp4", "zeta-a-beta.mp4"), library.videos.map { it.filename })
    }
}
