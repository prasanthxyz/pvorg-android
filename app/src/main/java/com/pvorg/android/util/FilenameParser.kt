package com.pvorg.android.util

data class ParsedFilename(
    val studio: String,
    val actors: List<String>,
    val title: String,
)

object FilenameParser {
    fun parse(filename: String): ParsedFilename {
        val stem = filename.substringBeforeLast('.', filename)
        val parts = stem.split("-")
        if (parts.size < 3) {
            return ParsedFilename(
                studio = parts.firstOrNull().orEmpty(),
                actors = emptyList(),
                title = stem,
            )
        }
        return ParsedFilename(
            studio = parts.first(),
            actors = parts.subList(1, parts.lastIndex),
            title = parts.last(),
        )
    }
}
