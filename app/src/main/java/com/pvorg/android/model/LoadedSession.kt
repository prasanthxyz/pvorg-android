package com.pvorg.android.model

data class LoadedSession(
    val selectedRoot: SelectedRoot,
    val config: AppConfigDto,
    val videos: List<VideoRecord>,
    val galleries: List<GalleryRecord>,
)
