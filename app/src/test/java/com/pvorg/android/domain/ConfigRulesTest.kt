package com.pvorg.android.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConfigRulesTest {
    private val galleries = listOf("beach", "city", "forest")

    @Test fun triStateCyclesUndecidedYesNo() {
        assertEquals(TriState.YES, TriState.UNDECIDED.next)
        assertEquals(TriState.NO, TriState.YES.next)
        assertEquals(TriState.UNDECIDED, TriState.NO.next)
    }

    @Test fun encodesSortedKeysWithTrailingNewlineAndDropsUnknownKeys() {
        val config = ConfigCodec.decode("""{"videosPath":"/v","videoRelations":{"b.mp4":{"tags":[]},"a.mp4":{"galleries":["x"]}},"tags":["t"],"extra":1}""")
        val text = ConfigCodec.encode(config)
        assertTrue(text.endsWith("}\n"))
        assertFalse(text.contains("extra"))
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals(listOf("tags", "videoRelations", "videosPath"), root.keys.toList())
        assertEquals(listOf("a.mp4", "b.mp4"), root["videoRelations"]!!.jsonObject.keys.toList())
        assertTrue(text.contains("\n  \"tags\""))
    }

    @Test fun reportsMissingRequiredKeys() {
        listOf("""{"tags":[]}""" to "videoRelations", """{"videoRelations":{}}""" to "tags").forEach { (text, key) ->
            try {
                ConfigCodec.decode(text)
                fail("expected an error for $key")
            } catch (error: ConfigCodec.InvalidConfig) {
                assertEquals("pvorg.json isn’t valid: the required key “$key” is missing.", error.message)
            }
        }
        try {
            ConfigCodec.decode("not json")
            fail("expected an error")
        } catch (error: ConfigCodec.InvalidConfig) {
            assertTrue(error.message!!.startsWith("pvorg.json isn’t valid"))
        }
    }

    @Test fun normalizingFillsMissingArraysOnly() {
        val config = AppConfig(videoRelations = mapOf("a.mp4" to VideoRelation(galleries = listOf("x")), "b.mp4" to VideoRelation.Empty))
        val (normalized, changed) = ConfigRules.normalized(config)
        assertEquals(1, changed)
        assertEquals(VideoRelation(emptyList(), emptyList(), listOf("x"), emptyList()), normalized.videoRelations["a.mp4"])
        assertEquals(0, ConfigRules.normalized(normalized).second)
    }

    @Test fun mappingKeepsVocabularyOrderUnknownValuesAndTheOtherPair() {
        val config = AppConfig(
            videoRelations = mapOf("a.mp4" to VideoRelation(listOf("forest"), listOf("dark"), listOf("retired", "city"), listOf("sunny", "gone"))),
        )
        val updated = ConfigRules.applyingMappings(
            config, MappingKind.GALLERIES, galleries,
            mapOf("a.mp4" to mapOf("forest" to TriState.YES, "beach" to TriState.YES, "city" to TriState.NO, "unknown" to TriState.YES)),
        )
        val relation = updated.videoRelations["a.mp4"]!!
        assertEquals(listOf("beach", "forest", "retired"), relation.galleries)
        assertEquals(listOf("city"), relation.excludedGalleries)
        assertEquals(listOf("sunny", "gone"), relation.tags)
        assertEquals(listOf("dark"), relation.excludedTags)
    }

    @Test fun mappingCreatesNormalizedEntries() {
        val updated = ConfigRules.applyingMappings(AppConfig(tags = listOf("t")), MappingKind.TAGS, listOf("t"), mapOf("new.mp4" to mapOf("t" to TriState.NO)))
        assertEquals(VideoRelation(emptyList(), listOf("t"), emptyList(), emptyList()), updated.videoRelations["new.mp4"])
    }

    @Test fun excludeThenRestoreIsExact() {
        val original = AppConfig(videoRelations = mapOf("a.mp4" to VideoRelation(emptyList(), emptyList(), listOf("beach"), listOf("t"))))
        val (excluded, exclusion) = ConfigRules.excludingPairing(original, "a.mp4", "beach", galleries)
        assertEquals(TriState.YES, exclusion.previousState)
        assertEquals(listOf("beach"), excluded.videoRelations["a.mp4"]!!.excludedGalleries)
        assertEquals(emptyList<String>(), excluded.videoRelations["a.mp4"]!!.galleries)
        assertEquals(original, ConfigRules.restoringPairing(excluded, exclusion, galleries))
    }

    @Test fun restoreRemovesAnEntryTheExclusionCreated() {
        val original = AppConfig(tags = listOf("t"))
        val (excluded, exclusion) = ConfigRules.excludingPairing(original, "a.mp4", "city", galleries)
        assertFalse(exclusion.hadRelation)
        assertEquals(TriState.UNDECIDED, exclusion.previousState)
        assertEquals(ConfigCodec.encode(original), ConfigCodec.encode(ConfigRules.restoringPairing(excluded, exclusion, galleries)))
    }

    @Test fun restoreKeepsAnEntryThatGainedOtherDecisions() {
        val (excluded, exclusion) = ConfigRules.excludingPairing(AppConfig(tags = listOf("t")), "a.mp4", "city", galleries)
        val decided = ConfigRules.applyingMappings(excluded, MappingKind.TAGS, listOf("t"), mapOf("a.mp4" to mapOf("t" to TriState.YES)))
        val restored = ConfigRules.restoringPairing(decided, exclusion, galleries)
        assertEquals(VideoRelation(emptyList(), emptyList(), emptyList(), listOf("t")), restored.videoRelations["a.mp4"])
    }

    @Test fun staleEntriesAreRelationsWithoutFiles() {
        val config = AppConfig(videoRelations = mapOf("b.mp4" to VideoRelation.Empty, "a.mp4" to VideoRelation.Empty, "c.mp4" to VideoRelation.Empty))
        assertEquals(listOf("a.mp4", "c.mp4"), ConfigRules.staleEntries(config, setOf("b.mp4")))
        assertEquals(setOf("b.mp4"), ConfigRules.removingEntries(config, listOf("a.mp4", "c.mp4")).videoRelations.keys)
    }

    @Test fun cacheEntriesAreRemovedAndUnchangedCacheIsLeftAlone() {
        val cache = """{"b.mp4":{"d":1},"a.mp4":{"d":2}}"""
        assertNull(ConfigCodec.removingCacheEntries(cache, setOf("zzz.mp4")))
        val updated = ConfigCodec.removingCacheEntries(cache, setOf("a.mp4"))!!
        assertEquals(setOf("b.mp4"), Json.parseToJsonElement(updated).jsonObject.keys)
        assertNull(ConfigCodec.removingCacheEntries("garbage", setOf("a.mp4")))
    }

    @Test fun mappingRowsHideFullyDecidedVideos() {
        val config = AppConfig(
            tags = listOf("t"),
            videoRelations = mapOf("acme-a-one.mp4" to VideoRelation(emptyList(), emptyList(), listOf("beach"), listOf("t"))),
        )
        val library = TestLibraries.library(listOf("acme-a-one.mp4", "acme-b-two.mp4"), galleries = listOf("beach"), config = config)
        assertEquals(listOf("acme-b-two.mp4"), LibraryRules.mappingRows(library, MappingKind.GALLERIES, hideDecided = true, query = "").map { it.video.filename })
        val all = LibraryRules.mappingRows(library, MappingKind.TAGS, hideDecided = false, query = "")
        assertEquals(mapOf("t" to TriState.YES), all.first { it.video.filename == "acme-a-one.mp4" }.saved)
        assertEquals(listOf("acme-a-one.mp4"), LibraryRules.mappingRows(library, MappingKind.TAGS, hideDecided = false, query = "ONE").map { it.video.filename })
    }
}
