package com.pvorg.android.util

import com.pvorg.android.model.Combination
import com.pvorg.android.model.GalleryRecord
import com.pvorg.android.model.VideoRecord
import kotlin.random.Random

object CombinationEngine {
    fun generate(
        videos: List<VideoRecord>,
        galleries: List<GalleryRecord>,
        selectedGalleries: Set<String>,
        selectedTags: Set<String>,
    ): List<Combination> {
        val effectiveGalleries = if (selectedGalleries.isEmpty()) {
            galleries.mapTo(linkedSetOf()) { it.name }
        } else {
            selectedGalleries
        }

        val filtered = buildList {
            for (video in videos) {
                if (selectedTags.isNotEmpty() && !selectedTags.all { it in video.tags }) {
                    continue
                }
                for (gallery in galleries) {
                    if (gallery.name in effectiveGalleries && gallery.name in video.relatedGalleries) {
                        add(Combination(video = video, gallery = gallery))
                    }
                }
            }
        }
        return filtered.shuffled(Random.Default)
    }
}
