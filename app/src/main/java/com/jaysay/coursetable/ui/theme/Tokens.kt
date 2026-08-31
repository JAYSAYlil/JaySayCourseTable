
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

/**
 * 动效令牌（Apple "Designing Fluid Interfaces" 准则的 Compose 落地）：
 * - 交互与页面级弹簧一律**临界阻尼**（dampingRatio = 1，无过冲），快速跟随、可随时中断；
 * - [momentum] 用于需要轻微惯性的落点强调（damping ≈ 0.85）；
 * - 缓动补间只用于淡入淡出与颜色过渡；动画永远从当前值出发（Compose 弹簧天然支持中断续接）。
 */
object Motion {
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    const val DURATION_SHORT = 150
    const val DURATION_BASE = 250
    const val DURATION_LONG = 380

    /** 交互弹簧：临界阻尼、快速跟随（约 0.25s 收敛）。 */
    fun <T> interactive(): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = 500f)

    /** 页面级弹簧：临界阻尼、更从容（约 0.35s 收敛）。 */
    fun <T> page(): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = 300f)

    /** 动量弹簧：轻微惯性落点，用于日历条展开等强调场景。 */
    fun <T> momentum(): SpringSpec<T> =
        spring(dampingRatio = 0.85f, stiffness = 400f)

    /** 缓动补间：淡入淡出与颜色过渡专用。 */
    fun <T> eased(duration: Int = DURATION_BASE): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        tween(duration, easing = emphasized)
}

/** 按压反馈：按下瞬间缩小（不等抬手），抬起弹回；符合"反馈发生在按下时"准则。 */
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
