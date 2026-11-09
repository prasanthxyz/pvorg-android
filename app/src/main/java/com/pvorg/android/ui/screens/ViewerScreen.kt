package com.pvorg.android.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import com.pvorg.android.domain.Combination
import com.pvorg.android.domain.DisplayName
import com.pvorg.android.domain.Format
import com.pvorg.android.domain.Gallery
import com.pvorg.android.state.AppViewModel
import com.pvorg.android.state.Page
import com.pvorg.android.state.Playback
import com.pvorg.android.state.PlayerController
import com.pvorg.android.state.UiState
import com.pvorg.android.state.ViewerMode
import com.pvorg.android.ui.GlobalActions
import com.pvorg.android.ui.OverflowMenu
import com.pvorg.android.ui.components.ActionIcon
import com.pvorg.android.ui.components.EmptyState
import com.pvorg.android.ui.components.PageTopBar
import com.pvorg.android.ui.components.rememberReduceMotion
import com.pvorg.android.ui.components.Thumbnail
import com.pvorg.android.ui.theme.Radius
import com.pvorg.android.ui.theme.Spacing
import com.pvorg.android.ui.theme.Stage
import com.pvorg.android.ui.theme.StageTheme
import com.pvorg.android.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(state: UiState, viewModel: AppViewModel, actions: GlobalActions) {
    val combination = state.currentCombination
    Column(Modifier.fillMaxSize()) {
        PageTopBar(
            title = combination?.let { DisplayName.title(it.video) } ?: "Viewer",
            subtitle = combination?.let { DisplayName.credits(it.video) },
            actions = {
                ActionIcon(
                    Icons.Outlined.ThumbDown,
                    combination?.let { "Exclude “${it.gallery.name}” from this video (Delete)" } ?: "Exclude Pairing",
                    onClick = viewModel::excludeCurrentPairing,
                    enabled = combination != null,
                )
                // The row sizes itself to its labels' longest word, wrapping "Contact Sheet"; fix its width.
                SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = Spacing.sm).width(340.dp)) {
                    listOf(ViewerMode.CONTACT_SHEET to "Contact Sheet", ViewerMode.VIDEO to "Video").forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = state.viewerMode == mode,
                            onClick = { viewModel.setViewerMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            enabled = combination != null,
                            icon = {
                                Icon(
                                    if (mode == ViewerMode.CONTACT_SHEET) Icons.Outlined.GridOn else Icons.Outlined.PlayCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                )
                            },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
                ActionIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous combination (B)", viewModel::previousCombination, enabled = state.combinations.size > 1)
                ActionIcon(Icons.AutoMirrored.Outlined.ArrowForward, "Next combination (N)", viewModel::nextCombination, enabled = state.combinations.size > 1)
                if (state.combinations.isNotEmpty()) {
                    Text(
                        "${Format.number(state.combinationIndex + 1)} of ${Format.number(state.combinations.size)}",
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    )
                }
                OverflowMenu(actions)
            },
        )
        StageTheme {
            Box(Modifier.fillMaxSize().background(Stage.background)) {
                if (combination == null) {
                    ViewerEmptyState(state, viewModel, actions)
                } else {
                    Stage(combination, state, viewModel)
                }
            }
        }
    }
    PrefetchNext(state)
}

/** Decodes the next combination's contact sheet ahead of time (spec §5). */
@Composable
private fun PrefetchNext(state: UiState) {
    val context = LocalContext.current
    val next = state.combinations.takeIf { it.size > 1 }?.let { it[(state.combinationIndex + 1) % it.size] }
    LaunchedEffect(next?.id) {
        next?.video?.contactSheet?.let { context.imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
    }
}

@Composable
private fun Stage(combination: Combination, state: UiState, viewModel: AppViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(0.75f).fillMaxHeight()) {
                when (state.viewerMode) {
                    ViewerMode.CONTACT_SHEET -> ContactSheet(combination, viewModel)
                    ViewerMode.VIDEO -> VideoStage(combination, viewModel)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Stage.hairline))
            GallerySlideshow(combination.gallery, Modifier.weight(0.25f).fillMaxHeight())
        }
    }
}

private fun Modifier.swipeToNavigate(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (abs(total) > 120.dp.toPx()) if (total < 0) onNext() else onPrevious()
        },
        onHorizontalDrag = { _, delta -> total += delta },
    )
}

@Composable
private fun ContactSheet(combination: Combination, viewModel: AppViewModel) {
    val sheet = combination.video.contactSheet
    if (sheet == null) {
        EmptyState(
            Icons.Outlined.GridOn,
            "No Contact Sheet",
            "Android can’t generate contact sheets. Create one with PP Video Organizer on a computer, or play the video.",
        ) {
            Button(onClick = { viewModel.setViewerMode(ViewerMode.VIDEO) }) { Text("Play Video") }
        }
        return
    }
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .swipeToNavigate(onPrevious = viewModel::previousCombination, onNext = viewModel::nextCombination)
            .pointerInput(combination.id) {
                detectTapGestures(
                    onDoubleTap = { viewModel.setViewerMode(ViewerMode.VIDEO) },
                    onLongPress = { with(density) { menuAt = DpOffset(it.x.toDp(), it.y.toDp() - size.height.toDp()) } },
                )
            }
            .semantics { contentDescription = "Contact sheet for ${DisplayName.title(combination.video)}. Double-tap to play." },
    ) {
        Thumbnail(
            location = sheet,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            placeholder = Color.Transparent,
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        )
        StageMenu(combination, menuAt, onDismiss = { menuAt = null }, viewModel)
    }
}

@Composable
private fun StageMenu(combination: Combination, at: DpOffset?, onDismiss: () -> Unit, viewModel: AppViewModel) {
    DropdownMenu(expanded = at != null, onDismissRequest = onDismiss, offset = at ?: DpOffset.Zero) {
        DropdownMenuItem(
            text = { Text("Exclude “${combination.gallery.name}” from This Video") },
            leadingIcon = { Icon(Icons.Outlined.ThumbDown, contentDescription = null) },
            onClick = { onDismiss(); viewModel.excludeCurrentPairing() },
        )
        DropdownMenuItem(
            text = { Text("Play Video") },
            leadingIcon = { Icon(Icons.Outlined.PlayCircle, contentDescription = null) },
            onClick = { onDismiss(); viewModel.setViewerMode(ViewerMode.VIDEO) },
        )
    }
}

@Composable
private fun VideoStage(combination: Combination, viewModel: AppViewModel) {
    val controller = viewModel.playerController
    val playback by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            if (playback.status == Playback.Status.FAILED) {
                EmptyState(
                    Icons.Outlined.WarningAmber,
                    "Can’t Play This Video",
                    playback.failure.orEmpty(),
                    modifier = Modifier.background(Stage.background),
                ) {
                    if (combination.video.location.startsWith("content:")) {
                        Button(onClick = { openExternally(context, combination.video.location) }) { Text("Open in Another App") }
                    }
                    OutlinedButton(onClick = { viewModel.setViewerMode(ViewerMode.CONTACT_SHEET) }) { Text("Show Contact Sheet") }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            player = controller.player
                        }
                    },
                    update = { it.player = controller.player },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
                // Tap toggles play/pause; a horizontal swipe moves between combinations.
                Box(
                    Modifier
                        .fillMaxSize()
                        .swipeToNavigate(onPrevious = viewModel::previousCombination, onNext = viewModel::nextCombination)
                        .pointerInput(Unit) { detectTapGestures(onTap = { controller.togglePlayPause() }) }
                        .semantics { contentDescription = if (playback.isPlaying) "Video, playing. Tap to pause." else "Video, paused. Tap to play." },
                )
                if (playback.status == Playback.Status.LOADING) CircularProgressIndicator(Modifier.size(32.dp), color = Stage.primary)
            }
        }
        if (playback.status != Playback.Status.FAILED) TransportBar(controller, playback)
    }
}

private fun openExternally(context: android.content.Context, location: String) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(location), "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Open video with"))
    } catch (_: ActivityNotFoundException) {
    }
}

/** Skip, play/pause, timecodes and a scrubber with frame previews (spec §9). */
@Composable
private fun TransportBar(controller: PlayerController, playback: Playback) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Stage.background)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val enabled = playback.canControl
        IconButton(onClick = { controller.seekBy(-10_000) }, enabled = enabled) {
            Icon(Icons.Outlined.Replay10, contentDescription = "Skip back 10 seconds", tint = Stage.primary)
        }
        IconButton(onClick = controller::togglePlayPause, enabled = enabled) {
            Icon(
                if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                tint = Stage.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = { controller.seekBy(10_000) }, enabled = enabled) {
            Icon(Icons.Outlined.Forward10, contentDescription = "Skip forward 10 seconds", tint = Stage.primary)
        }
        Text(Format.timecode(playback.positionMs), style = MaterialTheme.typography.labelMedium.tabular(), color = Stage.secondary)
        Scrubber(controller, playback, Modifier.weight(1f))
        Text(Format.timecode(playback.durationMs), style = MaterialTheme.typography.labelMedium.tabular(), color = Stage.secondary)
    }
}

@Composable
private fun Scrubber(controller: PlayerController, playback: Playback, modifier: Modifier) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var width by remember { mutableIntStateOf(1) }
    val duration = playback.durationMs
    val progress = if (duration > 0) (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val previewMs = dragFraction?.let { (it * duration).toLong() }

    LaunchedEffect(previewMs?.div(1000)) {
        if (previewMs == null) {
            preview = null
            return@LaunchedEffect
        }
        delay(40)
        controller.frame(previewMs)?.let { preview = it }
    }

    BoxWithConstraints(
        modifier
            .height(48.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(duration) {
                detectTapGestures { offset -> controller.scrub(((offset.x / width) * duration).toLong(), finished = true) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = (it.x / width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { controller.scrub((it * duration).toLong(), finished = true) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ ->
                        val fraction = (change.position.x / width).coerceIn(0f, 1f)
                        dragFraction = fraction
                        controller.scrub((fraction * duration).toLong(), finished = false)
                    },
                )
            }
            .semantics {
                contentDescription = "Playback position"
                stateDescription = "${Format.timecode(playback.positionMs)} of ${Format.timecode(duration)}"
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                setProgress { value -> controller.seekToFraction(value); true }
                customActions = listOf(
                    CustomAccessibilityAction("Skip back 10 seconds") { controller.seekBy(-10_000); true },
                    CustomAccessibilityAction("Skip forward 10 seconds") { controller.seekBy(10_000); true },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val active = dragFraction != null
        val track = if (active) 6.dp else 4.dp
        val shown = dragFraction ?: progress
        Box(Modifier.fillMaxWidth().height(track).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
        Box(Modifier.fillMaxWidth(shown.coerceAtLeast(0.001f)).height(track).clip(CircleShape).background(Stage.primary))
        Box(
            Modifier
                .offset(x = maxWidth * shown - 7.dp)
                .size(14.dp)
                .background(Color.White, CircleShape),
        )
        if (previewMs != null) {
            // A popup, so the preview isn't squeezed into the 48 dp bar and draws above the video surface.
            val density = LocalDensity.current
            val previewWidth = 224.dp
            val x = (maxWidth * shown - previewWidth / 2).coerceIn(0.dp, (maxWidth - previewWidth).coerceAtLeast(0.dp))
            val offset = with(density) { IntOffset(x.roundToPx(), -(PreviewHeight + Spacing.xs + 24.dp + Spacing.sm).roundToPx()) }
            Popup(offset = offset, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        Modifier
                            .size(previewWidth, PreviewHeight)
                            .clip(RoundedCornerShape(Radius.medium))
                            .background(Stage.raised)
                            .border(1.dp, Stage.hairline, RoundedCornerShape(Radius.medium)),
                    ) {
                        preview?.let {
                            androidx.compose.foundation.Image(
                                it.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(
                        Format.timecode(previewMs),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = Stage.primary,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    )
                }
            }
        }
    }
}

private val PreviewHeight = 126.dp

/** The gallery: shuffled, every 2 s, a 0.3 s crossfade; the next image is decoded before switching (spec §5). */
@Composable
private fun GallerySlideshow(gallery: Gallery, modifier: Modifier) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    // Decode at the settled size: while the layout animates (hiding or showing
    // the sidebar), the shown image scales instead of being decoded every frame.
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(measured) {
        if (size != IntSize.Zero) delay(300)
        size = measured
    }
    val order = remember(gallery) { gallery.images.shuffled() }
    var index by remember(gallery) { mutableIntStateOf(0) }

    fun request(location: String) = ImageRequest.Builder(context).data(location).apply {
        if (size != IntSize.Zero) size(size.width, size.height)
    }.build()

    LaunchedEffect(gallery, size) {
        if (order.size < 2 || size == IntSize.Zero) return@LaunchedEffect
        while (true) {
            val next = (index + 1) % order.size
            val started = System.currentTimeMillis()
            context.imageLoader.execute(request(order[next]))
            delay((2_000 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            index = next
        }
    }

    Box(modifier.background(Stage.background).onSizeChanged { measured = it }) {
        if (order.isEmpty()) {
            EmptyState(Icons.Outlined.PhotoLibrary, "No Images", "This gallery folder is empty.")
        } else if (size != IntSize.Zero) {
            Crossfade(targetState = index, animationSpec = tween(if (reduceMotion) 0 else 300), label = "slideshow") { shown ->
                AsyncImage(
                    model = request(order[shown % order.size]),
                    contentDescription = "Image ${shown + 1} of ${order.size} from ${gallery.name}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.xl, bottom = Spacing.sm + 2.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                gallery.name,
                style = MaterialTheme.typography.titleSmall,
                color = Stage.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (order.isNotEmpty()) {
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "${index % order.size + 1} of ${order.size}",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = Stage.secondary,
                )
            }
        }
    }
}

/** Each empty cause has its own explanation and fix (spec §5). */
@Composable
private fun ViewerEmptyState(state: UiState, viewModel: AppViewModel, actions: GlobalActions) {
    val library = state.library ?: return
    when {
        library.videos.isEmpty() -> EmptyState(
            Icons.Outlined.Movie,
            "No Videos",
            "PP Video Organizer didn’t find any videos in the VID folder of “${library.rootName}”.",
        ) { Button(onClick = actions.onReload) { Text("Reload Library") } }
        library.galleries.isEmpty() -> EmptyState(
            Icons.Outlined.Collections,
            "No Galleries",
            "Add folders of images to the GAL folder of “${library.rootName}”, then reload the library.",
        ) { Button(onClick = actions.onReload) { Text("Reload Library") } }
        !state.filters.isDefault(library) -> EmptyState(
            Icons.Outlined.FilterList,
            "No Matching Combinations",
            if (state.filters.tags.isEmpty()) "Your filters exclude every video and gallery pairing."
            else "Your filters exclude every video and gallery pairing. Required tags only match videos marked Yes for them in Tag Mapping.",
        ) {
            Button(onClick = { viewModel.showPage(Page.FILTERS) }) { Text("Edit Filters") }
            OutlinedButton(onClick = viewModel::resetFilters) { Text("Reset Filters") }
        }
        else -> EmptyState(
            Icons.Outlined.Collections,
            "No Combinations",
            "Every video is excluded from every gallery. Include some galleries in Gallery Mapping.",
        ) { Button(onClick = { viewModel.showPage(Page.GALLERY_MAPPING) }) { Text("Open Gallery Mapping") } }
    }
}
