package com.pvorg.android.data

import com.pvorg.android.domain.ConfigCodec
import com.pvorg.android.domain.MappingKind
import com.pvorg.android.domain.TriState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryRepositoryTest {
    @get:Rule val temp = TemporaryFolder()

    private fun root(config: String = """{"tags":["night"],"videoRelations":{}}"""): File {
        val root = temp.newFolder("Library")
        File(root, "pvorg.json").writeText(config)
        File(root, "VID/img").mkdirs()
        File(root, "GAL/beach").mkdirs()
        File(root, "GAL/city").mkdirs()
        File(root, "GAL/beach/1.jpg").writeText("x")
        File(root, "GAL/beach/notes.txt").writeText("x")
        for (name in listOf("acme-ann-one.mp4", "acme-bea-two.mkv", "Bad Name.mp4", "nodashes.mp4", "readme.txt")) File(root, "VID/$name").writeText("x")
        File(root, "VID/img/acme-ann-one.mp4.jpg").writeText("x")
        return root
    }

    @Test fun loadsVideosGalleriesAndSkippedFiles() {
        val library = LibraryRepository(FileLibraryStorage(root())).load()
        assertEquals(listOf("acme-ann-one.mp4", "acme-bea-two.mkv"), library.videos.map { it.filename }.sorted())
        assertEquals(listOf("Bad Name.mp4", "nodashes.mp4"), library.skipped.map { it.filename })
        assertEquals(listOf("beach", "city"), library.galleryNames)
        assertEquals(1, library.galleries.first { it.name == "beach" }.images.size)
        assertTrue(library.video("acme-ann-one.mp4")!!.contactSheet!!.endsWith("acme-ann-one.mp4.jpg"))
        assertNull("videos without a contact sheet still appear", library.video("acme-bea-two.mkv")!!.contactSheet)
    }

    @Test fun missingFoldersAreNamed() {
        val root = root()
        File(root, "GAL").deleteRecursively()
        try {
            LibraryRepository(FileLibraryStorage(root)).load()
            fail("expected an error")
        } catch (error: LibraryLoadException) {
            assertTrue(error.message!!, error.message!!.contains("the GAL folder"))
        }
    }

    @Test fun loadOnlyRewritesConfigWhenNormalizationChangesIt() {
        val normalized = ConfigCodec.encode(ConfigCodec.decode("""{"tags":[],"videoRelations":{"acme-ann-one.mp4":{"excludedGalleries":[],"excludedTags":[],"galleries":[],"tags":[]}}}"""))
        val untidy = """{"tags":[],"videoRelations":{"acme-ann-one.mp4":{"excludedGalleries":[],"excludedTags":[],"galleries":[],"tags":[]}}}"""
        val root = root(untidy)
        LibraryRepository(FileLibraryStorage(root)).load()
        assertEquals("already normal: left byte-for-byte", untidy, File(root, "pvorg.json").readText())
        File(root, "pvorg.json").writeText("""{"tags":[],"videoRelations":{"acme-ann-one.mp4":{}}}""")
        LibraryRepository(FileLibraryStorage(root)).load()
        assertEquals(normalized, File(root, "pvorg.json").readText())
    }

    @Test fun deletionRemovesVideoSheetRelationAndCacheEntry() {
        val root = root("""{"tags":[],"videoRelations":{"acme-ann-one.mp4":{"excludedGalleries":["city"],"excludedTags":[],"galleries":[],"tags":[]}}}""")
        File(root, "VID/cache.json").writeText("""{"acme-ann-one.mp4":{"duration":1},"acme-bea-two.mkv":{"duration":2}}""")
        val repository = LibraryRepository(FileLibraryStorage(root))
        val (library, result) = repository.deleteVideos(repository.load(), listOf("acme-ann-one.mp4", "../pvorg.json"))
        assertEquals(1, result.deletedCount)
        assertEquals(listOf("Rejected unknown filename: ../pvorg.json"), result.errors)
        assertFalse(File(root, "VID/acme-ann-one.mp4").exists())
        assertFalse(File(root, "VID/img/acme-ann-one.mp4.jpg").exists())
        assertTrue(File(root, "pvorg.json").exists())
        assertFalse(File(root, "pvorg.json").readText().contains("acme-ann-one"))
        assertFalse(File(root, "VID/cache.json").readText().contains("acme-ann-one"))
        assertTrue(File(root, "VID/cache.json").readText().contains("acme-bea-two"))
        assertEquals(listOf("acme-bea-two.mkv"), library.videos.map { it.filename })
    }

    @Test fun deletingAVideoWithoutASheetIsNotAnError() {
        val root = root()
        val repository = LibraryRepository(FileLibraryStorage(root))
        val (_, result) = repository.deleteVideos(repository.load(), listOf("acme-bea-two.mkv"))
        assertEquals(1, result.deletedCount)
        assertEquals(emptyList<String>(), result.errors)
        assertFalse(File(root, "VID/acme-bea-two.mkv").exists())
    }

    @Test fun stalePreviewFlagsAnEmptyVideosFolderAsHighRisk() {
        val root = root("""{"tags":[],"videoRelations":{"acme-ann-one.mp4":{},"gone-x-y.mp4":{}}}""")
        val repository = LibraryRepository(FileLibraryStorage(root))
        val library = repository.load()
        val preview = repository.stalePreview(library)
        assertEquals(listOf("gone-x-y.mp4"), preview.staleEntries)
        assertFalse(preview.isHighRisk)
        File(root, "VID").listFiles()!!.filter { it.isFile }.forEach { it.delete() }
        val risky = repository.stalePreview(library)
        assertTrue(risky.isHighRisk)
        assertEquals(2, risky.staleEntries.size)
    }

    @Test fun savingMappingsWritesThroughStorage() {
        val root = root()
        val repository = LibraryRepository(FileLibraryStorage(root))
        val library = repository.saveMappings(repository.load(), MappingKind.TAGS, mapOf("acme-ann-one.mp4" to mapOf("night" to TriState.YES)))
        assertEquals(listOf("night"), library.video("acme-ann-one.mp4")!!.tags)
        assertEquals(library.config, ConfigCodec.decode(File(root, "pvorg.json").readText()))
    }
}
