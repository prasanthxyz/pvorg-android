package com.pvorg.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppConfigDto(
    val videosPath: String? = null,
    val galleriesPath: String? = null,
    val tags: List<String> = emptyList(),
    val videoRelations: Map<String, VideoRelationDto> = emptyMap(),
)

@Serializable
data class VideoRelationDto(
    val galleries: List<String>? = null,
    @SerialName("excludedGalleries")
    val excludedGalleries: List<String>? = null,
    val tags: List<String>? = null,
    @SerialName("excludedTags")
    val excludedTags: List<String>? = null,
) {
    val yesGalleries: List<String> get() = galleries.orEmpty()
    val noGalleries: List<String> get() = excludedGalleries.orEmpty()
    val yesTags: List<String> get() = tags.orEmpty()
    val noTags: List<String> get() = excludedTags.orEmpty()
}
