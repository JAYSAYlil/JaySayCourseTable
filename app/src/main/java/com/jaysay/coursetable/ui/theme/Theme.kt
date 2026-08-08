
package com.jaysay.coursetable.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jaysay.coursetable.data.preferences.ThemeMode

private val LightColors = lightColorScheme(
    primary = Primary, onPrimary = Color.White,
    primaryContainer = PrimaryLight, onPrimaryContainer = PrimaryDark,
    surface = Surface, onSurface = OnSurface,
    surfaceVariant = Color(0xFFF5F5F5), onSurfaceVariant = OnSurfaceVariant,
    background = Background, onBackground = OnSurface,
    error = Error
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary, onPrimary = Color(0xFF1A1A1A),
    primaryContainer = DarkPrimaryLight, onPrimaryContainer = DarkPrimaryDark,
    secondary = Color(0xFF80CBC4),
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    background = DarkBackground, onBackground = DarkOnSurface,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFEF5350)
)

@Composable
fun JaySayTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val colors = if (isDark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}
