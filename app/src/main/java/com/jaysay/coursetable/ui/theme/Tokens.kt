
package com.jaysay.coursetable.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/** 统一形状体系：控件 12、卡片 18、面板 20、抽屉/弹窗 28。 */
object AppShapes {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(18.dp)
    val panel = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(28.dp)
    val input = RoundedCornerShape(14.dp)
}

/** 间距刻度：4 的倍数，页面边距 20，卡片内边距 16。 */
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val screenH = 20.dp
    val cardInner = 16.dp
}

/** 动效令牌：弹簧用于交互跟随，缓动用于页面级转场。 */
object Motion {
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    const val DURATION_SHORT = 150
    const val DURATION_BASE = 250
    const val DURATION_LONG = 380

    /** 交互弹簧：快速跟随、轻微过冲。 */
    fun <T> interactive(): SpringSpec<T> =
        spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    /** 页面级弹簧：更稳重，几乎无过冲。 */
    fun <T> page(): SpringSpec<T> =
        spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMedium)

    /** 缓动补间：用于不能弹簧化的场景（颜色、共享元素淡入等）。 */
    fun <T> eased(duration: Int = DURATION_BASE): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        tween(duration, easing = emphasized)
}

/** 按压反馈：按住缩至 0.97，抬起弹回。 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale"
    )
    this.scale(scale)
}
