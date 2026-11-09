package com.pvorg.android.domain

/** Small in-memory libraries for rule tests. */
object TestLibraries {
    fun library(
        files: List<String>,
        galleries: List<String> = listOf("beach", "city"),
        config: AppConfig = AppConfig(tags = listOf("outdoor", "night")),
        sheets: Set<String> = files.toSet(),
    ) = Library(
        rootName = "Library",
        config = config,
        scanned = files.map { ScannedVideo(it, "file:///VID/$it", if (it in sheets) "file:///VID/img/$it.jpg" else null) },
        galleries = galleries.map { Gallery(it, listOf("file:///GAL/$it/1.jpg")) },
        skipped = emptyList(),
        videoFileNames = files.toSet(),
    )
}
