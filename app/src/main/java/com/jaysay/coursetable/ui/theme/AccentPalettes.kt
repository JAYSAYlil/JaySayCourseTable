package com.jaysay.coursetable.ui.theme

import androidx.compose.ui.graphics.Color
import com.jaysay.coursetable.data.preferences.ThemeAccent

/**
 * 一套强调色的 6 个关键色：浅色端的 primary／容器／容器文字，深色端的同一组。
 *
 * 数值按 Material 3 的色调思路手工标定（浅色端 primary 取 tone 40 一档、容器取 tone 90 一档，
 * 深色端 primary 取 tone 80、容器取 tone 30），保证白底与深色底上的对比度，
 * 而不是简单地把一个颜色调亮调暗——那样会同时丢掉饱和度和可读性。
 */
data class AccentPalette(
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color
)

/** 青绿是既有品牌色，数值与 v3.4.28 完全一致，保证默认观感不变。 */
private val TealPalette = AccentPalette(
    lightPrimary = Color(0xFF0F8F82), lightContainer = Color(0xFFCFF2EC), lightOnContainer = Color(0xFF0B6E64),
    darkPrimary = Color(0xFF3ADBC4), darkContainer = Color(0xFF12433D), darkOnContainer = Color(0xFF6FEADD)
)

fun accentPalette(accent: ThemeAccent): AccentPalette = when (accent) {
    ThemeAccent.TEAL -> TealPalette
    ThemeAccent.BLUE -> AccentPalette(
        lightPrimary = Color(0xFF1565C0), lightContainer = Color(0xFFD8E6FB), lightOnContainer = Color(0xFF0D47A1),
        darkPrimary = Color(0xFF8AB4F8), darkContainer = Color(0xFF14315C), darkOnContainer = Color(0xFFAECBFA)
    )
    ThemeAccent.INDIGO -> AccentPalette(
        lightPrimary = Color(0xFF3F51B5), lightContainer = Color(0xFFE0E3F7), lightOnContainer = Color(0xFF283593),
        darkPrimary = Color(0xFFA6B4FF), darkContainer = Color(0xFF232B5C), darkOnContainer = Color(0xFFC5CBFF)
    )
    ThemeAccent.VIOLET -> AccentPalette(
        lightPrimary = Color(0xFF7A4FBF), lightContainer = Color(0xFFEDE2FA), lightOnContainer = Color(0xFF552B8A),
        darkPrimary = Color(0xFFCFA9F5), darkContainer = Color(0xFF3A2456), darkOnContainer = Color(0xFFE3D0FB)
    )
    ThemeAccent.PINK -> AccentPalette(
        lightPrimary = Color(0xFFC2185B), lightContainer = Color(0xFFFBD9E6), lightOnContainer = Color(0xFF8E1144),
        darkPrimary = Color(0xFFFFA8C4), darkContainer = Color(0xFF54132F), darkOnContainer = Color(0xFFFFC9DA)
    )
    ThemeAccent.ORANGE -> AccentPalette(
        lightPrimary = Color(0xFFBF5B00), lightContainer = Color(0xFFFBDFC4), lightOnContainer = Color(0xFF8A3F00),
        darkPrimary = Color(0xFFFFB870), darkContainer = Color(0xFF4E2A00), darkOnContainer = Color(0xFFFFD3A3)
    )
    ThemeAccent.GREEN -> AccentPalette(
        lightPrimary = Color(0xFF2E7D32), lightContainer = Color(0xFFCDEFCE), lightOnContainer = Color(0xFF1B5E20),
        darkPrimary = Color(0xFF87D68C), darkContainer = Color(0xFF1B3A1E), darkOnContainer = Color(0xFFA9E5AC)
    )
    ThemeAccent.GRAPHITE -> AccentPalette(
        lightPrimary = Color(0xFF455A64), lightContainer = Color(0xFFD9E2E7), lightOnContainer = Color(0xFF2A3B44),
        darkPrimary = Color(0xFFA8C0CC), darkContainer = Color(0xFF23323A), darkOnContainer = Color(0xFFC6D8E1)
    )
}
