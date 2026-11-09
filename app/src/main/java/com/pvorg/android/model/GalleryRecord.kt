package com.pvorg.android.model

import android.net.Uri

data class GalleryRecord(
    val name: String,
    val imageUris: List<Uri>,
) {
    val id: String = name
}
