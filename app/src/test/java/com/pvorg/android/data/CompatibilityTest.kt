package com.pvorg.android.data

import com.pvorg.android.domain.MappingKind
import com.pvorg.android.domain.TriState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Runs the shared compatibility sequence (spec §1.2): the same input and
 * operations the macOS and web apps run must produce the same pvorg.json.
 */
class CompatibilityTest {
    @get:Rule val temp = TemporaryFolder()

    private fun resource(name: String) = javaClass.getResource("/compat/$name")!!.readText()

    @Test fun matchesTheSharedExpectedOutput() {
        val root = temp.newFolder("compat")
        File(root, "pvorg.json").writeText(resource("input.json"))
        File(root, "VID/img").mkdirs()
        listOf("acme-jane-one", "acme-jane-two", "globex-sam-three", "hooli-li-four").forEach { File(root, "VID/$it.mp4").writeText("x") }
        listOf("G1", "G2", "G3").forEach { File(root, "GAL/$it").mkdirs() }

        val repository = LibraryRepository(FileLibraryStorage(root))
        var library = repository.load()
        library = repository.saveMappings(
            library, MappingKind.GALLERIES,
            mapOf(
                "acme-jane-one.mp4" to mapOf("G1" to TriState.YES, "G2" to TriState.NO),
                "acme-jane-two.mp4" to mapOf("G3" to TriState.NO),
                "globex-sam-three.mp4" to mapOf("G1" to TriState.UNDECIDED, "G2" to TriState.YES),
            ),
        )
        library = repository.saveMappings(
            library, MappingKind.TAGS,
            mapOf(
                "acme-jane-one.mp4" to mapOf("t1" to TriState.YES, "t3" to TriState.NO),
                "globex-sam-three.mp4" to mapOf("t2" to TriState.NO),
            ),
        )
        library = repository.excludePairing(library, "hooli-li-four.mp4", "G2").first
        library = repository.deleteVideos(library, listOf("acme-jane-two.mp4")).first
        repository.removeStaleEntries(library, repository.stalePreview(library))

        val written = File(root, "pvorg.json").readText()
        assertEquals(Json.parseToJsonElement(resource("expected.json")), Json.parseToJsonElement(written))
        assert(written.endsWith("}\n"))
    }
}
