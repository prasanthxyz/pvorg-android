package com.pvorg.android.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class CombinationEngineTest {
    private val config = AppConfig(
        tags = listOf("night", "outdoor"),
        videoRelations = mapOf(
            "acme-ann-one.mp4" to VideoRelation(listOf("city"), emptyList(), emptyList(), listOf("outdoor", "night")),
            "acme-bea-two.mp4" to VideoRelation(emptyList(), listOf("outdoor"), emptyList(), listOf("night")),
            "globex-ann-bea-three.mp4" to VideoRelation(emptyList(), emptyList(), emptyList(), emptyList()),
        ),
    )
    private val library = TestLibraries.library(listOf("acme-ann-one.mp4", "acme-bea-two.mp4", "globex-ann-bea-three.mp4"), config = config)

    private fun ids(filters: Filters) = CombinationEngine.generate(library, filters, Random(1)).map { it.id }.sorted()

    @Test fun galleriesAreOptOut() {
        assertEquals(
            listOf("acme-ann-one.mp4::beach", "acme-bea-two.mp4::beach", "acme-bea-two.mp4::city", "globex-ann-bea-three.mp4::beach", "globex-ann-bea-three.mp4::city"),
            ids(Filters.defaults(library)),
        )
    }

    @Test fun gallerySelectionIncludesAnyOf() {
        assertEquals(listOf("acme-bea-two.mp4::city", "globex-ann-bea-three.mp4::city"), ids(Filters.defaults(library).copy(galleries = setOf("city"))))
    }

    @Test fun studiosInclude() {
        assertEquals(listOf("globex-ann-bea-three.mp4::beach", "globex-ann-bea-three.mp4::city"), ids(Filters.defaults(library).copy(studios = setOf("globex"))))
    }

    @Test fun tagsAreOptInAndRequireAll() {
        assertEquals(listOf("acme-ann-one.mp4::beach", "acme-bea-two.mp4::beach", "acme-bea-two.mp4::city"), ids(Filters.defaults(library).copy(tags = setOf("night"))))
        assertEquals(listOf("acme-ann-one.mp4::beach"), ids(Filters.defaults(library).copy(tags = setOf("night", "outdoor"))))
    }

    @Test fun castRequiresAll() {
        assertEquals(listOf("acme-ann-one.mp4::beach", "globex-ann-bea-three.mp4::beach", "globex-ann-bea-three.mp4::city"), ids(Filters.defaults(library).copy(cast = setOf("ann"))))
        assertEquals(listOf("globex-ann-bea-three.mp4::beach", "globex-ann-bea-three.mp4::city"), ids(Filters.defaults(library).copy(cast = setOf("ann", "bea"))))
    }

    @Test fun reconcilingDropsMissingValuesAndRefillsEmptyIncludes() {
        val filters = Filters(galleries = setOf("gone"), studios = setOf("acme", "gone"), tags = setOf("night", "gone"), cast = setOf("zed"))
        val reconciled = filters.reconciled(library)
        assertEquals(setOf("beach", "city"), reconciled.galleries)
        assertEquals(setOf("acme"), reconciled.studios)
        assertEquals(setOf("night"), reconciled.tags)
        assertEquals(emptySet<String>(), reconciled.cast)
    }
}
