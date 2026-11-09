package com.pvorg.android.domain

/** A video file found on disk, before config decisions are applied. */
data class ScannedVideo(
    val filename: String,
    val location: String,
    val contactSheet: String?,
)

/**
 * A loaded library. Derived lists are computed once here, not on every
 * interaction (spec §11).
 */
data class Library(
    val rootName: String,
    val config: AppConfig,
    val scanned: List<ScannedVideo>,
    val galleries: List<Gallery>,
    val skipped: List<SkippedFile>,
    /** Every file with a video extension, including skipped ones (for the stale-entry preview). */
    val videoFileNames: Set<String>,
) {
    /** Videos in display order, with gallery and tag decisions resolved. */
    val videos: List<Video> = VideoSort.sorted(scanned.map { LibraryRules.resolve(it, config, galleries) })

    val galleryNames: List<String> = galleries.map { it.name }.sorted()
    val studios: List<String> = videos.map { it.studio }.distinct().sorted()
    val cast: List<String> = videos.flatMap { it.actors }.distinct().sorted()
    val tags: List<String> = config.tags.distinct().sorted()

    fun columns(kind: MappingKind): List<String> = when (kind) {
        MappingKind.GALLERIES -> galleryNames
        MappingKind.TAGS -> tags
    }

    fun withConfig(updated: AppConfig): Library = copy(config = updated)

    fun video(filename: String): Video? = videos.firstOrNull { it.filename == filename }
}

object LibraryRules {
    /** Galleries are opt-out and tags opt-in (spec §3.1). */
    fun resolve(scanned: ScannedVideo, config: AppConfig, galleries: List<Gallery>): Video {
        val parsed = FilenameRules.parse(scanned.filename)
        val relation = config.videoRelations[scanned.filename]
        val excluded = relation?.noGalleries.orEmpty().toSet()
        return Video(
            filename = scanned.filename,
            studio = parsed.studio,
            actors = parsed.actors,
            title = parsed.title,
            location = scanned.location,
            contactSheet = scanned.contactSheet,
            relatedGalleries = galleries.map { it.name }.filter { it !in excluded },
            tags = relation?.yesTags.orEmpty(),
        )
    }

    /** Saved decisions for the matrix; `hideDecided` drops fully decided videos. */
    fun mappingRows(library: Library, kind: MappingKind, hideDecided: Boolean, query: String): List<MappingRow> {
        val columns = library.columns(kind)
        if (columns.isEmpty()) return emptyList()
        val trimmed = query.trim()
        return library.videos.mapNotNull { video ->
            if (trimmed.isNotEmpty() && !Search.matches(video, trimmed)) return@mapNotNull null
            val relation = library.config.videoRelations[video.filename] ?: VideoRelation.Empty
            val (yes, no) = when (kind) {
                MappingKind.GALLERIES -> relation.yesGalleries.toSet() to relation.noGalleries.toSet()
                MappingKind.TAGS -> relation.yesTags.toSet() to relation.noTags.toSet()
            }
            val saved = columns.associateWith { column ->
                when (column) {
                    in yes -> TriState.YES
                    in no -> TriState.NO
                    else -> TriState.UNDECIDED
                }
            }
            if (hideDecided && saved.values.none { it == TriState.UNDECIDED }) null else MappingRow(video, saved)
        }
    }
}
