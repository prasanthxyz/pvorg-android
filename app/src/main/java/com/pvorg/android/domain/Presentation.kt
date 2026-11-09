package com.pvorg.android.domain

import java.text.Collator
import java.text.NumberFormat
import java.text.Normalizer
import java.util.Locale

/** Filename convention (spec §1.3). */
object FilenameRules {
    val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "webm")
    val imageExtensions = setOf("png", "jpg", "jpeg", "bmp", "gif")

    data class Parsed(val studio: String, val actors: List<String>, val title: String)

    fun isVideo(name: String) = !name.startsWith(".") && extension(name) in videoExtensions
    fun isImage(name: String) = !name.startsWith(".") && extension(name) in imageExtensions

    /** Reasons the name breaks the convention; empty when valid. */
    fun problems(filename: String): List<String> = buildList {
        if (filename != filename.lowercase(Locale.ROOT)) add("Contains uppercase characters")
        if (' ' in filename) add("Contains spaces")
        if (stem(filename).split("-").size < 3) add("Fewer than 3 dash-separated parts (need studio-cast-title)")
    }

    fun parse(filename: String): Parsed {
        val stem = stem(filename)
        val parts = stem.split("-")
        if (parts.size < 3) return Parsed(parts.firstOrNull() ?: "unknown", emptyList(), stem)
        return Parsed(parts.first(), parts.subList(1, parts.lastIndex), parts.last())
    }

    fun extension(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    private fun stem(name: String) = if ('.' in name) name.substringBeforeLast('.') else name
}

/** Display rules (spec §2). Raw values stay the identifiers everywhere else. */
object DisplayName {
    fun humanize(token: String): String {
        val words = token.replace('_', ' ').split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return token
        return words.joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }
    }

    fun title(video: Video): String = video.title.trim().ifEmpty { null }?.let(::humanize) ?: video.filename

    fun cast(actors: List<String>): String = joinList(actors.map(::humanize))

    /** "Acme · Jane Doe and John Roe" */
    fun credits(video: Video): String =
        listOf(humanize(video.studio), cast(video.actors)).filter { it.isNotEmpty() }.joinToString(" · ")

    /** "A", "A and B", "A, B, and C" */
    fun joinList(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
    }
}

object Format {
    private val number get() = NumberFormat.getIntegerInstance(Locale.getDefault())

    fun number(value: Int): String = number.format(value)

    /** "1 video", "3 videos" */
    fun counted(value: Int, singular: String, plural: String = singular + "s"): String =
        "${number(value)} ${if (value == 1) singular else plural}"

    /** "0:07", "12:34", "1:02:03" */
    fun timecode(millis: Long): String {
        if (millis <= 0) return "0:00"
        val total = millis / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }
}

/** Case- and diacritic-insensitive matching against everything shown for a video. */
object Search {
    fun matches(video: Video, query: String): Boolean {
        val needle = fold(query.trim())
        if (needle.isEmpty()) return true
        val haystacks = listOf(DisplayName.title(video), video.filename, DisplayName.humanize(video.studio)) +
            video.actors.map(DisplayName::humanize)
        return haystacks.any { fold(it).contains(needle) }
    }

    fun matches(label: String, query: String): Boolean = fold(label).contains(fold(query.trim()))

    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
}

object VideoSort {
    private val collator: Collator
        get() = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }

    /** By humanized title (case-insensitive, locale-aware), then filename. */
    fun sorted(videos: List<Video>): List<Video> {
        val collator = collator
        return videos.sortedWith { a, b ->
            val byTitle = collator.compare(DisplayName.title(a), DisplayName.title(b))
            if (byTitle != 0) return@sortedWith byTitle
            val byFilename = collator.compare(a.filename, b.filename)
            if (byFilename != 0) byFilename else a.filename.compareTo(b.filename)
        }
    }

    fun sortedLabels(labels: List<String>, display: (String) -> String = { it }): List<String> {
        val collator = collator
        return labels.sortedWith { a, b -> collator.compare(display(a), display(b)) }
    }
}
