
package com.jaysay.coursetable.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jaysay.coursetable.data.preferences.ThemeMode

private val LightColors = lightColorScheme(
    primary = Primary, onPrimary = Color.White,
    primaryContainer = PrimaryLight, onPrimaryContainer = PrimaryDark,
    secondary = Secondary, onSecondary = Color.White,
    secondaryContainer = SecondaryLight, onSecondaryContainer = SecondaryDark,
    tertiary = Tertiary, onTertiary = Color.White,
    tertiaryContainer = TertiaryLight, onTertiaryContainer = TertiaryDark,
    surface = Surface, onSurface = OnSurface,
    // Material 3 未显式指定时会回退到默认紫粉色调；所有页面、菜单、弹层的
    // 浅色基底在这里统一为纯白，层级由描边、阴影和显式 surfaceVariant 表达。
    surfaceBright = Surface,
    surfaceDim = Surface,
    surfaceContainerLowest = Surface,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface,
    surfaceContainerHighest = Surface,
    surfaceTint = Primary,
    surfaceVariant = Color(0xFFECF0EF), onSurfaceVariant = OnSurfaceVariant,
    background = Background, onBackground = OnSurface,
    outline = Color(0xFF7C8280), outlineVariant = Color(0xFFD8DEDC),
    error = Error
)

private val DarkColors = darkColorScheme(    primary = DarkPrimary, onPrimary = Color(0xFF00332C),
    primaryContainer = DarkPrimaryLight, onPrimaryContainer = DarkPrimaryDark,
    secondary = DarkSecondary, onSecondary = Color(0xFF0A261B),
    secondaryContainer = DarkSecondaryLight, onSecondaryContainer = DarkSecondaryDark,
    tertiary = DarkTertiary, onTertiary = Color(0xFF1A280D),
    tertiaryContainer = DarkTertiaryLight, onTertiaryContainer = DarkTertiaryDark,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    background = DarkBackground, onBackground = DarkOnSurface,
    outline = Color(0xFF8A8F8D),
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFEF5350)
)

/** 系统级“增强对比度”偏好在组合树的可达形式，供课程卡片等自定义绘制组件读取。 */
val LocalEnhancedContrast = compositionLocalOf { false }

@Composable
fun JaySayTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    highContrast: Boolean = false,
    transparentSystemBars: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val baseColors = if (isDark) DarkColors else LightColors
    val colors = if (highContrast) baseColors.copy(
        onSurfaceVariant = baseColors.onSurface,
        outline = baseColors.onSurface,
        outlineVariant = baseColors.onSurface.copy(alpha = 0.7f)
    ) else baseColors
    val view = LocalView.current
    MaterialTheme(colorScheme = colors, typography = Typography) {
        CompositionLocalProvider(LocalEnhancedContrast provides highContrast) {
            // 页面交叉淡化时始终有与当前主题一致的底色，深色模式不会透出窗口默认白色。
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    // targetSdk 35 强制 edge-to-edge：系统栏始终透明，
                    // 栏位颜色由 decorView 底色与页面 Compose 图层决定（自定义背景绘制在系统栏之后）。
                    // 保留统一 edge-to-edge 坐标系，避免系统避让与 Compose Insets 重复叠加。
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.decorView.setBackgroundColor(colors.background.toArgb())
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
            }
            content()
        }
    }
}
