@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.jaysay.coursetable.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * 共享元素转场的两个作用域，由 MainActivity 的 SharedTransitionLayout + AnimatedContent 提供。
 * 采用 CompositionLocal 穿线，避免把作用域参数层层下传到深层卡片。
 * 任一为 null（非转场上下文）时，使用方应退化为普通修饰符。
 */
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

val LocalNavAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    compositionLocalOf { null }
