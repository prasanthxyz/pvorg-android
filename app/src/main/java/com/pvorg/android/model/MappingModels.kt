package com.pvorg.android.model

import android.net.Uri

data class MappingVideoRow(
    val filename: String,
    val title: String,
    val tgpUri: Uri,
    val map: Map<String, TriState>,
)

data class MappingPageData(
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val columns: List<String>,
    val videos: List<MappingVideoRow>,
) {
    companion object {
        fun empty(pageSize: Int) = MappingPageData(
            total = 0,
            page = 1,
            pageSize = pageSize,
            columns = emptyList(),
            videos = emptyList(),
        )
    }
}
