package com.pvorg.android.model

data class Combination(
    val video: VideoRecord,
    val gallery: GalleryRecord,
) {
    val id: String = "${video.filename}::${gallery.name}"
}
