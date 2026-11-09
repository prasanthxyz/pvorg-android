package com.pvorg.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.pvorg.android.MainUiState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    uiState: MainUiState,
    player: ExoPlayer,
    onPreviousCombination: () -> Unit,
    onNextCombination: () -> Unit,
    onToggleViewerMode: () -> Unit,
) {
    val combination = uiState.currentCombination
    if (combination == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No combinations found.")
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.68f)
                .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = combination.video.filename,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Button(onClick = onToggleViewerMode) {
                    Text(if (uiState.showTgp) "VID" else "TGP")
                }
                Button(onClick = onPreviousCombination) { Text("Prev") }
                Button(onClick = onNextCombination) { Text("Next") }
                Text(combination.gallery.name)
                Text("${uiState.combinationIndex + 1}/${maxOf(1, uiState.combinations.size)}")
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.showTgp) {
                    AsyncImage(
                        model = combination.video.tgpUri,
                        contentDescription = combination.video.filename,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            PlayerView(context).apply {
                                useController = true
                                this.player = player
                            }
                        },
                        update = { playerView ->
                            playerView.player = player
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            var currentImageIndex by remember(combination.gallery.id) { mutableIntStateOf(0) }
            val imageCount = combination.gallery.imageUris.size

            LaunchedEffect(combination.gallery.id, imageCount) {
                if (imageCount <= 1) return@LaunchedEffect
                while (true) {
                    delay(2500L)
                    currentImageIndex = (currentImageIndex + 1) % imageCount
                }
            }

            Text(
                text = combination.gallery.name,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.titleMedium,
            )

            if (imageCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Gallery is empty.")
                }
            } else {
                AsyncImage(
                    model = combination.gallery.imageUris[currentImageIndex],
                    contentDescription = combination.gallery.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
