package com.pvorg.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pvorg.android.state.Appearance

/** Spacing scale (spec §8.2), in dp. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radii (spec §8.2). */
object Radius {
    val small = 5.dp
    val medium = 8.dp
    val large = 12.dp
}

/** Fixed media-stage colors: the stage stays dark in every appearance (spec §8.3). */
object Stage {
    val background = Color(0xFF0F0F0F)
    val raised = Color(0xFF1F1F1F)
    val hairline = Color.White.copy(alpha = 0.08f)
    val primary = Color.White.copy(alpha = 0.94f)
    val secondary = Color.White.copy(alpha = 0.62f)
}

/** Semantic colors outside Material's scheme (spec §8.3). */
@Immutable
data class PvorgColors(
    val decisionYes: Color,
    val decisionNo: Color,
    val pending: Color,
    val warning: Color,
    val critical: Color,
)

private val LightPvorg = PvorgColors(
    decisionYes = Color(0xFF1A8740),
    decisionNo = Color(0xFFCC2121),
    pending = Color(0xFFFF9500),
    warning = Color(0xFFC77700),
    critical = Color(0xFFCC2121),
)

private val DarkPvorg = PvorgColors(
    decisionYes = Color(0xFF45CC73),
    decisionNo = Color(0xFFFF6B66),
    pending = Color(0xFFFF9F0A),
    warning = Color(0xFFFF9F0A),
    critical = Color(0xFFFF6B66),
)

val LocalPvorgColors = staticCompositionLocalOf { DarkPvorg }

/** Fallback when dynamic color isn't available: a neutral scheme with a blue accent. */
private val FallbackLight = lightColorScheme(primary = Color(0xFF2B5BD7), secondary = Color(0xFF4D5C80))
private val FallbackDark = darkColorScheme(primary = Color(0xFFAFC6FF), secondary = Color(0xFFBAC6EA))

/** Tabular digits for counts, positions and timecodes (spec §8.4). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

@Composable
fun isDark(appearance: Appearance): Boolean = when (appearance) {
    Appearance.SYSTEM -> isSystemInDarkTheme()
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

@Composable
fun PvorgTheme(dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> FallbackDark
        else -> FallbackLight
    }
    CompositionLocalProvider(LocalPvorgColors provides if (dark) DarkPvorg else LightPvorg) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Everything drawn on the stage uses dark-appearance colors (spec §5). */
@Composable
fun StageTheme(content: @Composable () -> Unit) = PvorgTheme(dark = true) {
    // Without a Surface, content would keep the page's (possibly light-theme) content color.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
}
