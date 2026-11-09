package com.pvorg.android.domain

import kotlinx.serialization.Serializable

/**
 * `pvorg.json` (spec §1.2). Properties are declared in alphabetical order so
 * the encoder writes keys sorted, matching the other platforms. Keys outside
 * the schema are dropped on read, as on every platform.
 */
@Serializable
data class AppConfig(
    val galleriesPath: String? = null,
    val tags: List<String> = emptyList(),
    val videoRelations: Map<String, VideoRelation> = emptyMap(),
    val videosPath: String? = null,
)

/** A video's tri-state decisions. `null` arrays are normalized to `[]` at startup. */
@Serializable
data class VideoRelation(
    val excludedGalleries: List<String>? = null,
    val excludedTags: List<String>? = null,
    val galleries: List<String>? = null,
    val tags: List<String>? = null,
) {
    val yesGalleries: List<String> get() = galleries.orEmpty()
    val noGalleries: List<String> get() = excludedGalleries.orEmpty()
    val yesTags: List<String> get() = tags.orEmpty()
    val noTags: List<String> get() = excludedTags.orEmpty()

    val isEmpty: Boolean
        get() = yesGalleries.isEmpty() && noGalleries.isEmpty() && yesTags.isEmpty() && noTags.isEmpty()

    companion object {
        val Empty = VideoRelation(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

enum class TriState {
    YES,
    NO,
    UNDECIDED,
    ;

    /** Undecided → Yes → No → Undecided (spec §6.3). */
    val next: TriState
        get() = when (this) {
            UNDECIDED -> YES
            YES -> NO
            NO -> UNDECIDED
        }

    val title: String
        get() = when (this) {
            YES -> "Yes"
            NO -> "No"
            UNDECIDED -> "Undecided"
        }
}

/** The two relation types edited with the tri-state matrix. */
enum class MappingKind(val title: String, val columnNoun: String) {
    GALLERIES("Gallery Mapping", "gallery"),
    TAGS("Tag Mapping", "tag"),
}

data class Video(
    val filename: String,
    val studio: String,
    val actors: List<String>,
    val title: String,
    /** Where the video file is: a content:// or file:// URI string. */
    val location: String,
    /** The contact sheet's location, or `null` when none exists (Android can't generate one). */
    val contactSheet: String?,
    val relatedGalleries: List<String>,
    val tags: List<String>,
)

data class Gallery(
    val name: String,
    val images: List<String>,
)

data class Combination(
    val video: Video,
    val gallery: Gallery,
) {
    val id: String = "${video.filename}::${gallery.name}"
}

data class SkippedFile(
    val filename: String,
    val reasons: List<String>,
)

/** A video–gallery pairing excluded from the viewer, with what's needed to undo it exactly (spec §3.6). */
data class PairingExclusion(
    val filename: String,
    val galleryName: String,
    val previousState: TriState,
    val hadRelation: Boolean,
    val index: Int?,
)

data class StaleCleanupPreview(
    val videosFolder: String,
    val videoFileCount: Int,
    val totalRelationCount: Int,
    val staleEntries: List<String>,
) {
    /** Nothing visible on disk while mappings exist — likely a disconnected or wrong folder. */
    val isHighRisk: Boolean get() = videoFileCount == 0 && totalRelationCount > 0
}

data class DeletionResult(
    val deletedCount: Int,
    val errors: List<String>,
)

/** Filter selections (spec §3.1). Galleries and studios include; tags and cast require all. */
data class Filters(
    val galleries: Set<String> = emptySet(),
    val studios: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val cast: Set<String> = emptySet(),
) {
    fun isDefault(library: Library): Boolean =
        galleries == library.galleryNames.toSet() &&
            studios == library.studios.toSet() &&
            tags.isEmpty() &&
            cast.isEmpty()

    /** Drops values that no longer exist; an emptied include set falls back to everything. */
    fun reconciled(library: Library): Filters {
        val galleryNames = library.galleryNames.toSet()
        val studioNames = library.studios.toSet()
        val keptGalleries = galleries.intersect(galleryNames)
        val keptStudios = studios.intersect(studioNames)
        return Filters(
            galleries = keptGalleries.ifEmpty { galleryNames },
            studios = keptStudios.ifEmpty { studioNames },
            tags = tags.intersect(library.config.tags.toSet()),
            cast = cast.intersect(library.cast.toSet()),
        )
    }

    companion object {
        fun defaults(library: Library) = Filters(
            galleries = library.galleryNames.toSet(),
            studios = library.studios.toSet(),
        )
    }
}

/** One mapping matrix row: a video with its saved decision for every column. */
data class MappingRow(
    val video: Video,
    val saved: Map<String, TriState>,
)
