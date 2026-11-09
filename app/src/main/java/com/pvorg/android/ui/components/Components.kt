package com.pvorg.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onInterceptKeyBeforeSoftKeyboard
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.pvorg.android.domain.TriState
import com.pvorg.android.ui.theme.LocalPvorgColors
import com.pvorg.android.ui.theme.Radius
import com.pvorg.android.ui.theme.Spacing

/**
 * An image decoded off the main thread at the size it's drawn (Coil sizes
 * requests to the layout) and kept in Coil's memory cache (spec §9).
 */
@Composable
fun Thumbnail(
    location: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    missingTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var failed by remember(location) { mutableStateOf(false) }
    Box(modifier.background(placeholder), contentAlignment = Alignment.Center) {
        if (location == null || failed) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = missingTint.copy(alpha = 0.6f))
        }
        if (location != null && !failed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(location).crossfade(180).build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Toggle token (spec §9): a leading check-circle / circle glyph plus label,
 * the same width in both states. Long-press opens [onLongPress] (context menu).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterToken(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(Radius.small + 1.dp)
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.17f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), shape)
            .combinedClickable(onClick = onToggle, onLongClick = onLongPress, role = Role.Checkbox)
            .semantics {
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

fun TriState.icon(): ImageVector = when (this) {
    TriState.YES -> Icons.Rounded.Check
    TriState.NO -> Icons.Rounded.Close
    TriState.UNDECIDED -> Icons.Outlined.Remove
}

@Composable
fun TriState.tint(): Color = when (this) {
    TriState.YES -> LocalPvorgColors.current.decisionYes
    TriState.NO -> LocalPvorgColors.current.decisionNo
    TriState.UNDECIDED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun TriState.fill(): Color = when (this) {
    TriState.YES -> LocalPvorgColors.current.decisionYes.copy(alpha = 0.18f)
    TriState.NO -> LocalPvorgColors.current.decisionNo.copy(alpha = 0.16f)
    TriState.UNDECIDED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
}

/** The decision glyph used in cells and the legend (spec §9). */
@Composable
fun TriStateBadge(state: TriState, modifier: Modifier = Modifier, modified: Boolean = false, width: Int = 36, height: Int = 28) {
    val shape = RoundedCornerShape(Radius.small)
    Box(modifier.size(width.dp, height.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(state.fill(), shape)
                .then(if (modified) Modifier.border(1.5.dp, LocalPvorgColors.current.pending, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(state.icon(), contentDescription = null, tint = state.tint(), modifier = Modifier.size((height * 0.6f).dp))
        }
        if (modified) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 0.dp)
                    .size(7.dp)
                    .background(LocalPvorgColors.current.pending, CircleShape),
            )
        }
    }
}

enum class CalloutTone { WARNING, CRITICAL }

/** Inline notice (spec §9): icon, title, message, optional action. */
@Composable
fun Callout(
    tone: CalloutTone,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    val tint = if (tone == CalloutTone.WARNING) LocalPvorgColors.current.warning else LocalPvorgColors.current.critical
    val shape = RoundedCornerShape(Radius.medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.10f), shape)
            .border(0.5.dp, tint.copy(alpha = 0.25f), shape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(if (tone == CalloutTone.WARNING) Icons.Outlined.WarningAmber else Icons.Outlined.Error, contentDescription = null, tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

/** Empty, error and no-results states: icon, title, specific explanation, the fixing actions. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxSize().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), content = actions)
        }
    }
}

/** A page's top app bar: title, subtitle and actions (spec §4, §15.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTopBar(
    title: String,
    subtitle: String?,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        windowInsets = TopAppBarDefaults.windowInsets,
    )
}

/** An icon button with a tooltip on long-press or hover; the label is also its accessible name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.38f))
        }
    }
}

/** Compact search field for top app bars. Reports focus so shortcuts stand down while typing. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequest: Int = 0,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Only requests made while this field is shown focus it, not earlier ones.
    val initialRequest = remember { focusRequest }
    LaunchedEffect(focusRequest) { if (focusRequest != initialRequest) runCatching { focusRequester.requestFocus() } }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .widthIn(min = 200.dp, max = 280.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onEditingChange(it.isFocused) }
            .onInterceptKeyBeforeSoftKeyboard { event ->
                // Esc leaves the field so single-key shortcuts work again (spec §7.1).
                // Intercepted before the soft keyboard, which would otherwise consume it.
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) { focusManager.clearFocus(); true } else false
            }
            .semantics { contentDescription = placeholder },
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}
