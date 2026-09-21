
package com.jaysay.coursetable.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jaysay.coursetable.data.preferences.ThemeAccent
import com.jaysay.coursetable.data.preferences.ThemeMode

/** 按强调色生成浅色方案；青绿保持既有品牌绿/橄榄次要色，其它颜色让次要色跟随强调色。 */
private fun lightColorsFor(accent: ThemeAccent): ColorScheme {
    val palette = accentPalette(accent)
    val brand = accent == ThemeAccent.TEAL
    return lightColorScheme(
    primary = palette.lightPrimary, onPrimary = Color.White,
    primaryContainer = palette.lightContainer, onPrimaryContainer = palette.lightOnContainer,
    secondary = if (brand) Secondary else palette.lightPrimary, onSecondary = Color.White,
    secondaryContainer = if (brand) SecondaryLight else palette.lightContainer,
    onSecondaryContainer = if (brand) SecondaryDark else palette.lightOnContainer,
    tertiary = if (brand) Tertiary else palette.lightPrimary, onTertiary = Color.White,
    tertiaryContainer = if (brand) TertiaryLight else palette.lightContainer,
    onTertiaryContainer = if (brand) TertiaryDark else palette.lightOnContainer,
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
    surfaceTint = Color.Transparent,
    // 中性底色不带色相：换主题色后灰底/描边不会残留一丝绿意。
    surfaceVariant = Color(0xFFEDEFF0), onSurfaceVariant = OnSurfaceVariant,
    background = Background, onBackground = OnSurface,
    outline = Color(0xFF7E8180), outlineVariant = Color(0xFFDBDDDC),
    error = Error
    )
}

private fun darkColorsFor(accent: ThemeAccent): ColorScheme {
    val palette = accentPalette(accent)
    val brand = accent == ThemeAccent.TEAL
    return darkColorScheme(
    primary = palette.darkPrimary, onPrimary = palette.darkOnPrimary,
    primaryContainer = palette.darkContainer, onPrimaryContainer = palette.darkOnContainer,
    secondary = if (brand) DarkSecondary else palette.darkPrimary,
    onSecondary = if (brand) Color(0xFF0A261B) else palette.darkOnPrimary,
    secondaryContainer = if (brand) DarkSecondaryLight else palette.darkContainer,
    onSecondaryContainer = if (brand) DarkSecondaryDark else palette.darkOnContainer,
    tertiary = if (brand) DarkTertiary else palette.darkPrimary,
    onTertiary = if (brand) Color(0xFF1A280D) else palette.darkOnPrimary,
    tertiaryContainer = if (brand) DarkTertiaryLight else palette.darkContainer,
    onTertiaryContainer = if (brand) DarkTertiaryDark else palette.darkOnContainer,
    surface = DarkSurface, onSurface = DarkOnSurface,
    surfaceTint = Color.Transparent,
    surfaceBright = DarkSurfaceVariant,
    surfaceDim = DarkBackground,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceVariant,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = DarkOnSurfaceVariant,
    background = DarkBackground, onBackground = DarkOnSurface,
    outline = Color(0xFF8A8F8D),
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFEF5350)
    )
}

/** 系统级“增强对比度”偏好在组合树的可达形式，供课程卡片等自定义绘制组件读取。 */
val LocalEnhancedContrast = compositionLocalOf { false }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JaySayTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeAccent: ThemeAccent = ThemeAccent.TEAL,
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
    val baseColors = remember(themeAccent, isDark) {
        if (isDark) darkColorsFor(themeAccent) else lightColorsFor(themeAccent)
    }
    val colors = if (highContrast) baseColors.copy(
        onSurfaceVariant = baseColors.onSurface,
        outline = baseColors.onSurface,
        outlineVariant = baseColors.onSurface.copy(alpha = 0.7f)
    ) else baseColors
    val view = LocalView.current
    MaterialTheme(colorScheme = colors, typography = Typography) {
        CompositionLocalProvider(LocalEnhancedContrast provides highContrast, LocalRippleConfiguration provides null) {
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
