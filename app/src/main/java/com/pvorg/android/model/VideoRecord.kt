package com.pvorg.android.model

import android.net.Uri

data class VideoRecord(
    val filename: String,
    val title: String,
    val studio: String,
    val actors: List<String>,
    val videoUri: Uri,
    val tgpUri: Uri,
    val relatedGalleries: List<String>,
    val tags: List<String>,
) {
    val id: String = filename
}
