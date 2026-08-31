package com.jaysay.coursetable.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 手动 Hero 转场（container transform 的 overlay 实现）。
 *
 * 与 SharedTransitionLayout 的根本区别：bounds 只在「点击瞬间」从注册表快照，
 * 动画期间不再跟踪任何真实元素——滚动、翻页、页面进出都与飞行矩形无关，
 * 从机制上消除 sharedBounds 在 Pager/滚动容器中「不触发 / 越界 / 消失」的问题。
 *
 * 流程：
 * 1. 课程卡在 onGloballyPositioned 中持续把「可见卡片」的 root bounds 与底色
 *    写入注册表（视口外的同 series 卡片——例如 Pager 相邻周页——不覆盖）；
 * 2. 点击卡片时 [beginForward] 快照当前位置发起正向飞行，随后正常导航进详情；
 * 3. 详情头部完成测量后回写 bounds，overlay 拿到终点即用临界阻尼弹簧飞行；
 * 4. 离开详情页时 [onLeaveDetail] 以保存的快照发起反向飞行（卡片位置取注册表
 *    当前值，回退到快照值），完毕后清空全部状态。
 */
object HeroRegistry {

    /** 课程卡 bounds（root 坐标）与底色，由可见卡片在布局回调中持续更新。 */
    internal val cardBounds = HashMap<String, Rect>()
    internal val cardColors = HashMap<String, Color>()

    /** 详情头部 bounds；离开详情页时置空，等待下一次测量。 */
    var headerBounds by mutableStateOf<Rect?>(null)
        internal set

    /** 当前飞行请求；null 时 overlay 不绘制。 */
    var request by mutableStateOf<HeroRequest?>(null)
        internal set

    /** 最近一次正向飞行的快照，供返回时反向配对。 */
    private var lastSpec: HeroRequest? = null

    class HeroRequest(
        val key: String,
        val color: Color,
        val fromCard: Rect,
        /** 正向飞行的终点；详情头部测量后填充。 */
        var toHeader: Rect?,
        val forward: Boolean
    )

    /** 课程卡点击时调用：快照当前可见位置发起正向飞行。 */
    fun beginForward(key: String) {
        val rect = cardBounds[key] ?: return
        val color = cardColors[key] ?: return
        val spec = HeroRequest(key, color, rect, toHeader = null, forward = true)
        lastSpec = spec
        request = spec
    }

    /** 离开详情页时调用：发起反向飞行并重置头部测量；无配对快照则无动作。 */
    fun onLeaveDetail() {
        val liveHeader = headerBounds
        headerBounds = null
        val spec = lastSpec ?: return
        lastSpec = null
        val card = cardBounds[spec.key] ?: spec.fromCard
        val header = liveHeader ?: spec.toHeader ?: return
        request = HeroRequest(spec.key, spec.color, card, header, forward = false)
    }
}

/** 飞行矩形两端的圆角（dp）：起点与课程卡一致，终点与详情头部一致。 */
private val HERO_RADIUS_START = 12.dp
private val HERO_RADIUS_END = 18.dp

/** 飞行矩形的底色不透明度：从接近卡片的实色过渡到详情头部的淡染色。 */
private const val HERO_ALPHA_START = 0.92f
private const val HERO_ALPHA_END = 0.20f

/** 起终点各 20% 行程内淡入淡出，让矩形与真实卡片/头部无缝交接。 */
private fun edgeFade(progress: Float): Float =
    minOf(1f, progress * 5f, (1f - progress) * 5f)

/**
 * Hero 飞行 overlay：铺满根布局、只绘制不拦截输入。
 * 挂在 MainActivity 根 Box 内（AnimatedContent 之后、SnackbarHost 之前）。
 */
@Composable
fun HeroOverlay() {
    val request = HeroRegistry.request ?: return
    val progress = remember(request) { Animatable(if (request.forward) 0f else 1f) }

    LaunchedEffect(request) {
        if (request.forward) {
            // 等待详情头部完成测量；超时放弃（导航本身不受影响）。
            val header = withTimeoutOrNull(600) {
                snapshotFlow { HeroRegistry.headerBounds }.filterNotNull().first()
            }
            if (header == null) {
                HeroRegistry.request = null
                return@LaunchedEffect
            }
            request.toHeader = header
        }
        progress.animateTo(
            targetValue = if (request.forward) 1f else 0f,
            // 临界阻尼弹簧：无过冲、可中断，与全应用动效体系一致。
            animationSpec = spring(dampingRatio = 1f, stiffness = 380f)
        )
        HeroRegistry.request = null
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val target = request.toHeader ?: return@Canvas
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        val left = lerp(request.fromCard.left, target.left, p)
        val top = lerp(request.fromCard.top, target.top, p)
        val right = lerp(request.fromCard.right, target.right, p)
        val bottom = lerp(request.fromCard.bottom, target.bottom, p)
        val radius = lerp(HERO_RADIUS_START.toPx(), HERO_RADIUS_END.toPx(), p)
        val alpha = lerp(HERO_ALPHA_START, HERO_ALPHA_END, p) * edgeFade(p)
        drawRoundRect(
            color = request.color.copy(alpha = alpha),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(radius, radius)
        )
    }
}
