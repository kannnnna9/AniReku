package com.anireku.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0B0B10)
private val InkElevated = Color(0xFF15151E)
private val Sakura = Color(0xFFFF6B9A)
private val SakuraDeep = Color(0xFFB23A63)
private val Sky = Color(0xFF7AC7FF)

private val DarkColors = darkColorScheme(
    primary = Sakura,
    onPrimary = Color(0xFF2A0713),
    primaryContainer = SakuraDeep,
    onPrimaryContainer = Color(0xFFFFE3EC),
    secondary = Sky,
    background = Ink,
    onBackground = Color(0xFFEDEDF2),
    surface = InkElevated,
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF23232F),
    onSurfaceVariant = Color(0xFFB9B9C6),
)

private val LightColors = lightColorScheme(
    primary = SakuraDeep,
    onPrimary = Color.White,
    secondary = Color(0xFF1B6FA8),
    background = Color(0xFFFAF7F9),
    surface = Color.White,
)

@Composable
fun AniRekuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
