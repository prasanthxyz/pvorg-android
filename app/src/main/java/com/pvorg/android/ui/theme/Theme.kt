package com.pvorg.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PvorgDarkScheme = darkColorScheme(
    primary = Color(0xFFBA1A1A),
    secondary = Color(0xFF9CCBFF),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF202020),
)

@Composable
fun PvorgTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PvorgDarkScheme,
        content = content,
    )
}
