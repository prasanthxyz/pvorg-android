package com.pvorg.android.model

import android.net.Uri

data class SelectedRoot(
    val rootUri: Uri,
    val rootName: String,
    val configUri: Uri,
    val videosDirUri: Uri,
    val imageDirUri: Uri,
    val galleriesDirUri: Uri,
)
