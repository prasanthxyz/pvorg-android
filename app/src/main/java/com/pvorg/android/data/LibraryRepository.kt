package com.pvorg.android.data

import com.pvorg.android.domain.AppConfig
import com.pvorg.android.domain.ConfigCodec
import com.pvorg.android.domain.ConfigRules
import com.pvorg.android.domain.DeletionResult
import com.pvorg.android.domain.FilenameRules
import com.pvorg.android.domain.Gallery
import com.pvorg.android.domain.Library
import com.pvorg.android.domain.MappingKind
import com.pvorg.android.domain.PairingExclusion
import com.pvorg.android.domain.ScannedVideo
import com.pvorg.android.domain.SkippedFile
import com.pvorg.android.domain.StaleCleanupPreview
import com.pvorg.android.domain.TriState

/** A startup step (spec §3.2). Android can't generate previews, so it has no preview step. */
data class LoadProgress(
    val title: String,
    val detail: String? = null,
    val fraction: Float? = null,
)

class LibraryLoadException(message: String) : Exception(message)

/** Part I operations against a library folder. Pure Kotlin, so every rule is unit-tested. */
class LibraryRepository(private val storage: LibraryStorage) {
    val displayName: String get() = storage.displayName
    val locationDescription: String get() = storage.locationDescription

    fun load(onProgress: (LoadProgress) -> Unit = {}): Library {
        onProgress(LoadProgress("Loading Configuration", "pvorg.json"))
        val rootEntries = storage.list(emptyList())
            ?: throw LibraryLoadException("The folder “${storage.displayName}” can’t be opened.")
        val missing = listOf(CONFIG to false, VIDEOS to true, GALLERIES to true).filter { (name, isDir) ->
            rootEntries.none { it.name == name && it.isDirectory == isDir }
        }
        if (missing.isNotEmpty()) {
            throw LibraryLoadException(
                "“${storage.displayName}” is missing ${missing.joinToString(" and ") { (name, isDir) -> if (isDir) "the $name folder" else name }}. " +
                    "A library folder contains pvorg.json, VID (videos, with contact sheets in VID/img) and GAL (one folder per gallery).",
            )
        }
        var config = ConfigCodec.decode(storage.read(listOf(CONFIG)) ?: throw LibraryLoadException("pvorg.json can’t be read."))

        onProgress(LoadProgress("Scanning Library", "VID"))
        val videoEntries = storage.list(listOf(VIDEOS)).orEmpty()
        val sheets = storage.list(listOf(VIDEOS, CONTACT_SHEETS)).orEmpty()
            .filter { !it.isDirectory }
            .associateBy { it.name }
        val skipped = mutableListOf<SkippedFile>()
        val scanned = mutableListOf<ScannedVideo>()
        val videoFileNames = mutableSetOf<String>()
        for (entry in videoEntries.filter { !it.isDirectory && FilenameRules.isVideo(it.name) }.sortedBy { it.name }) {
            videoFileNames += entry.name
            val problems = FilenameRules.problems(entry.name)
            if (problems.isNotEmpty()) {
                skipped += SkippedFile(entry.name, problems)
                continue
            }
            scanned += ScannedVideo(entry.name, entry.location, sheets["${entry.name}.jpg"]?.location)
        }

        val galleryFolders = storage.list(listOf(GALLERIES)).orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name }
        val galleries = galleryFolders.mapIndexed { index, folder ->
            onProgress(LoadProgress("Scanning Library", "${index + 1} of ${galleryFolders.size} · ${folder.name}", (index + 1f) / galleryFolders.size))
            val images = storage.list(listOf(GALLERIES, folder.name)).orEmpty()
                .filter { !it.isDirectory && FilenameRules.isImage(it.name) }
                .sortedBy { it.name }
                .map { it.location }
            Gallery(folder.name, images)
        }

        onProgress(LoadProgress("Updating pvorg.json"))
        val (normalized, changed) = ConfigRules.normalized(config)
        if (changed > 0) {
            storage.write(listOf(CONFIG), ConfigCodec.encode(normalized))
            config = normalized
        }

        return Library(
            rootName = storage.displayName,
            config = config,
            scanned = scanned,
            galleries = galleries,
            skipped = skipped,
            videoFileNames = videoFileNames,
        )
    }

    /** Spec §3.3. Returns the library with the new config; derived lists are recomputed from it. */
    fun saveMappings(library: Library, kind: MappingKind, changes: Map<String, Map<String, TriState>>): Library {
        val updated = ConfigRules.applyingMappings(library.config, kind, library.columns(kind), changes)
        writeConfig(updated)
        return library.withConfig(updated)
    }

    fun excludePairing(library: Library, filename: String, gallery: String): Pair<Library, PairingExclusion> {
        val (updated, exclusion) = ConfigRules.excludingPairing(library.config, filename, gallery, library.galleryNames)
        writeConfig(updated)
        return library.withConfig(updated) to exclusion
    }

    fun restorePairing(library: Library, exclusion: PairingExclusion): Library {
        val updated = ConfigRules.restoringPairing(library.config, exclusion, library.galleryNames)
        writeConfig(updated)
        return library.withConfig(updated)
    }

    /**
     * Spec §3.7: the video file, its contact sheet, its relation and its
     * `cache.json` entry. Only filenames from the scanned library are accepted.
     */
    fun deleteVideos(library: Library, filenames: Collection<String>): Pair<Library, DeletionResult> {
        val known = library.videos.map { it.filename }.toSet()
        val errors = mutableListOf<String>()
        val removed = mutableSetOf<String>()
        for (filename in filenames.toSortedSet()) {
            if (filename !in known) {
                errors += "Rejected unknown filename: $filename"
                continue
            }
            val videoDeleted = storage.delete(listOf(VIDEOS, filename))
            val sheetDeleted = storage.delete(listOf(VIDEOS, CONTACT_SHEETS, "$filename.jpg"))
            if (!videoDeleted) errors += "Couldn’t delete $filename"
            if (!sheetDeleted) errors += "Couldn’t delete the contact sheet for $filename"
            if (videoDeleted) removed += filename
        }

        var config: AppConfig = library.config
        if (removed.any { it in config.videoRelations }) {
            config = ConfigRules.removingEntries(config, removed)
            writeConfig(config)
        }
        storage.read(listOf(VIDEOS, CACHE))?.let { cache ->
            ConfigCodec.removingCacheEntries(cache, removed)?.let { storage.write(listOf(VIDEOS, CACHE), it) }
        }

        val remaining = library.copy(
            config = config,
            scanned = library.scanned.filter { it.filename !in removed },
            videoFileNames = library.videoFileNames - removed,
        )
        return remaining to DeletionResult(removed.size, errors)
    }

    /** Spec §3.5. Re-lists the videos folder so the preview reflects what's on disk now. */
    fun stalePreview(library: Library): StaleCleanupPreview {
        val onDisk = storage.list(listOf(VIDEOS)).orEmpty()
            .filter { !it.isDirectory && FilenameRules.isVideo(it.name) }
            .map { it.name }
            .toSet()
        return StaleCleanupPreview(
            videosFolder = "${storage.displayName}/$VIDEOS",
            videoFileCount = onDisk.size,
            totalRelationCount = library.config.videoRelations.size,
            staleEntries = ConfigRules.staleEntries(library.config, onDisk),
        )
    }

    fun removeStaleEntries(library: Library, preview: StaleCleanupPreview): Pair<Library, Int> {
        val removing = preview.staleEntries.filter { it in library.config.videoRelations }
        if (removing.isEmpty()) return library to 0
        val updated = ConfigRules.removingEntries(library.config, removing)
        writeConfig(updated)
        return library.withConfig(updated) to removing.size
    }

    private fun writeConfig(config: AppConfig) = storage.write(listOf(CONFIG), ConfigCodec.encode(config))

    companion object {
        const val CONFIG = "pvorg.json"
        const val VIDEOS = "VID"
        const val CONTACT_SHEETS = "img"
        const val GALLERIES = "GAL"
        const val CACHE = "cache.json"
    }
}
