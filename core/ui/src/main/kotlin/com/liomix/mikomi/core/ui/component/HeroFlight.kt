package com.liomix.mikomi.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.liomix.mikomi.core.model.TransitionStyle

/**
 * 封面飞行（Flutter `Hero` 的等价实现）。
 *
 * 为什么不直接用 Compose 的 `SharedTransitionLayout` + `Modifier.sharedElement`：
 * Compose 的共享元素是**画在页面内部**的，页面自带位移/缩放时会把飞行元素一起拖走、被页面边界裁掉，
 * 只能靠牺牲页面转场去迁就它。Flutter 的 Hero 则画在 Navigator 的 Overlay 上，与路由转场完全解耦。
 * 这里照 Flutter 的做法自己实现：起点/终点各自上报自身位置，飞行本体绘制在页面之上的 overlay 里。
 *
 * 性能设计（决定「顺不顺」的关键）：
 * 1. 图层尺寸固定为**起点尺寸**，整个飞行过程不再改尺寸。位置与大小全部交给
 *    [graphicsLayer] 的 `translation / scale` —— 这些是 RenderNode 属性，
 *    RenderThread 直接做矩阵变换（GPU 硬件加速），不触发 remeasure，也不重新光栅化整张图。
 *    早先每帧改 `Modifier.size(...)`：每帧重新测量 + 重新绘制 + 重新上传纹理，是掉帧的主因。
 * 2. 逐帧变化的量（进度、终点）只在 `graphicsLayer` 的 lambda 里读（deferred read），
 *    这类读取只失效**绘制层**，不会重组 Composable。
 * 3. 圆角用 `graphicsLayer.shape + clip` 插值，等价于 RenderNode 的 outline 裁剪，
 *    比每帧经 `Modifier.clip` 走 DrawScope 的 `clipPath` 便宜。
 */
private const val FLIGHT_DURATION_MILLIS = 300

/** 旋转样式转一整圈（度）。 */
private const val FULL_TURN_DEGREES = 360f

/** 平滑进出缓动（≈ easeInOut），旋转用它避免起止生硬。 */
private fun smoothInOut(t: Float): Float = t * t * (3f - 2f * t)

/** 线性插值（显式手写，避免 ui-geometry / ui-unit 里三个同名 `lerp` 重载在作用域内歧义）。 */
private fun mix(from: Float, to: Float, fraction: Float): Float =
    from + (to - from) * fraction

/** 一次飞行的参数快照。 */
@Immutable
internal data class HeroEndpoint(
    val bounds: Rect,
    val radius: Dp,
    val url: String?,
    val description: String?,
)

/**
 * 一次飞行的**固定参数**（起点、圆角、样式、图片）。
 *
 * 终点不在这里：终点在页面转场期间会被逐帧修正，单独放在
 * [HeroFlightController.liveTo] 里，只在绘制阶段读取。
 */
@Immutable
data class HeroFlight(
    /** 单次飞行的稳定标识：飞行途中终点会被逐帧修正，但动画进度不能因此重置。 */
    val id: Long,
    val key: Any,
    val url: String?,
    val description: String?,
    val from: Rect,
    val fromRadius: Dp,
    val toRadius: Dp,
    val style: TransitionStyle,
    /** 是否为返回时的反向飞行（终点是列表卡而不是详情封面）。 */
    val reverse: Boolean = false,
)

/**
 * 飞行控制器：记录两端位置、驱动一次飞行。
 *
 * 两端都要注册（列表卡 = 起点，详情封面 = 终点）；正向飞行在「点击那一刻」确定起点，
 * 等详情封面测量出来后起飞；返回时两端都已就绪，直接反向飞。
 */
@Stable
class HeroFlightController {

    private val sources = mutableMapOf<Any, HeroEndpoint>()
    private val targets = mutableMapOf<Any, HeroEndpoint>()

    /**
     * 当前飞行序号；`null` 表示没有飞行。
     *
     * 组合层只依赖它（起飞 / 结束各变一次）。因为飞行的**终点**每帧都在被修正，
     * 若把整个飞行对象做成 State，飞行层每帧都会重组；这里拆成
     * 「一次性快照 + 单独的终点 State」，让逐帧变化只走到绘制阶段。
     */
    var flightId by mutableStateOf<Long?>(null)
        private set

    /** 当前飞行的固定参数；与 [flightId] 同时生效。 */
    var flight: HeroFlight? = null
        private set

    /** 飞行终点（页面转场期间逐帧修正）；只在绘制阶段读取。 */
    private val liveTo = mutableStateOf(Rect.Zero)

    /** 供飞行层在 `graphicsLayer` 里读取的当前终点。 */
    val currentTo: Rect get() = liveTo.value

    /** 当前转场样式（由导航宿主同步），决定飞行内容变换。 */
    var style: TransitionStyle = TransitionStyle.Translate

    /** 起飞请求：起点已定，等终点测量。 */
    private var pendingKey: Any? = null

    /** 飞行序号：每次起飞自增，用于绑定动画进度。 */
    private var nextFlightId = 0L

    /** 最近一次正向飞行完成的 key，供返回时反向飞。 */
    private var lastFlewKey: Any? = null

    /** 列表卡（起点）上报自身位置。 */
    internal fun onSourceLaidOut(key: Any, endpoint: HeroEndpoint) {
        sources[key] = endpoint
        // 反向飞行时列表页也在动（页面缩放会让它整体位移），终点要持续跟随。
        val current = flight
        if (current != null && current.key == key && current.reverse) {
            liveTo.value = endpoint.bounds
        }
    }

    /** 详情封面（终点）上报自身位置；若正在等这个 key 就起飞。 */
    internal fun onTargetLaidOut(key: Any, endpoint: HeroEndpoint) {
        targets[key] = endpoint
        // 正向飞行途中目标还被页面转场推着走，持续跟随才能落到最终位置。
        val current = flight
        if (current != null && current.key == key && !current.reverse) {
            liveTo.value = endpoint.bounds
        }
        if (pendingKey != key) return
        val source = sources[key] ?: return
        pendingKey = null
        launch(
            key = key,
            url = endpoint.url ?: source.url,
            description = endpoint.description ?: source.description,
            from = source.bounds,
            fromRadius = source.radius,
            to = endpoint.bounds,
            toRadius = endpoint.radius,
            reverse = false,
        )
    }

    /** 列表项点击时调用：记住起点，等详情页封面测量完成后起飞。 */
    fun begin(key: Any) {
        if (sources[key] == null) return
        pendingKey = key
    }

    /** 返回时反向飞：详情封面 → 列表卡；两端都没记录时什么都不做。 */
    fun beginReverse(key: Any?) {
        val resolved = key ?: lastFlewKey ?: return
        val target = targets[resolved] ?: return
        val source = sources[resolved] ?: return
        launch(
            key = resolved,
            url = target.url ?: source.url,
            description = target.description ?: source.description,
            from = target.bounds,
            fromRadius = target.radius,
            to = source.bounds,
            toRadius = source.radius,
            reverse = true,
        )
    }

    /** 飞行结束：解除两端的隐藏，并记住 key 供返回使用。 */
    fun finish(id: Long) {
        if (flightId != id) return
        lastFlewKey = flight?.key
        flight = null
        flightId = null
    }

    /** 该 key 的封面此刻是否正由 overlay 绘制（页面内的副本应当隐藏）。 */
    fun isFlying(key: Any?): Boolean =
        key != null && flightId != null && flight?.key == key

    private fun launch(
        key: Any,
        url: String?,
        description: String?,
        from: Rect,
        fromRadius: Dp,
        to: Rect,
        toRadius: Dp,
        reverse: Boolean,
    ) {
        // 先把固定快照与终点写好，再改 flightId 触发一次重组：飞行层在重组时读到的是完整状态。
        flight = HeroFlight(
            id = ++nextFlightId,
            key = key,
            url = url,
            description = description,
            from = from,
            fromRadius = fromRadius,
            toRadius = toRadius,
            style = style,
            reverse = reverse,
        )
        liveTo.value = to
        flightId = flight?.id
    }
}

/** 当前飞行控制器；未提供时（预览等）飞行修饰符退化为原样。 */
val LocalHeroFlightController = compositionLocalOf<HeroFlightController?> { null }

/**
 * 标记为飞行起点（列表卡封面）。
 *
 * 必须作用在「最终视觉元素」上（`aspectRatio` + `clip` 之后），否则上报的是裁剪前的大布局。
 */
@Composable
fun Modifier.heroSource(
    key: Any?,
    url: String?,
    description: String?,
    radius: Dp,
): Modifier = heroEndpoint(key, url, description, radius, isTarget = false)

/** 标记为飞行终点（详情页封面）。 */
@Composable
fun Modifier.heroTarget(
    key: Any?,
    url: String?,
    description: String?,
    radius: Dp,
): Modifier = heroEndpoint(key, url, description, radius, isTarget = true)

/** 上报自身位置：起点只记录，终点在记录后触发起飞（或修正飞行终点）。 */
@Composable
private fun Modifier.heroEndpoint(
    key: Any?,
    url: String?,
    description: String?,
    radius: Dp,
    isTarget: Boolean,
): Modifier {
    val controller = LocalHeroFlightController.current ?: return this
    if (key == null) return this
    return this
        .onGloballyPositioned { coordinates ->
            val endpoint = HeroEndpoint(
                bounds = coordinates.boundsInRoot(),
                radius = radius,
                url = url,
                description = description,
            )
            if (isTarget) {
                controller.onTargetLaidOut(key, endpoint)
            } else {
                controller.onSourceLaidOut(key, endpoint)
            }
        }
        .hideWhileFlying(controller, key)
}

/**
 * 飞行期间隐藏页面内的副本。
 *
 * 用 `graphicsLayer` 而不是 `alpha` 修饰符：不参与重组，只失效绘制，且保留布局尺寸（终点还要靠它上报位置）。
 */
@Composable
private fun Modifier.hideWhileFlying(
    controller: HeroFlightController,
    key: Any,
): Modifier = graphicsLayer {
    alpha = if (controller.isFlying(key)) 0f else 1f
}

/**
 * 飞行层：绘制在页面之上（与 Flutter Hero 的 overlay 同构）。
 *
 * 放在根 `Box` 里、导航内容之后即可（子项默认左上对齐）：页面转场怎么缩/怎么淡都不影响它。
 */
@Composable
fun HeroFlightOverlay(
    controller: HeroFlightController,
    modifier: Modifier = Modifier,
) {
    val flightId = controller.flightId ?: return
    // 固定的那部分参数此处读一次即可：`flight` 不是 State，逐帧修正终点不会重组本函数。
    val flight = controller.flight ?: return

    val density = LocalDensity.current
    // 图层尺寸锁定为起点尺寸：飞行过程中尺寸恒定，缩放交给 GPU。
    val baseWidth = with(density) { flight.from.width.toDp() }
    val baseHeight = with(density) { flight.from.height.toDp() }
    if (baseWidth < 1.dp || baseHeight < 1.dp) return

    val progress = remember(flightId) { Animatable(0f) }
    LaunchedEffect(flightId) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = FLIGHT_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
        controller.finish(flightId)
    }

    val context = LocalContext.current
    val request = remember(flight.url, context) {
        ImageRequest.Builder(context)
            .data(flight.url)
            .memoryCacheKey(flight.url)
            .build()
    }

    Box(
        modifier = modifier
            .size(baseWidth, baseHeight)
            .graphicsLayer {
                // 逐帧量全部在这里读：只失效绘制层（RenderNode 属性更新），不触发重组。
                val fraction = progress.value
                val to = controller.currentTo
                val fromWidth = flight.from.width
                val fromHeight = flight.from.height
                val width = mix(fromWidth, to.width, fraction)
                val height = mix(fromHeight, to.height, fraction)
                val left = mix(flight.from.left, to.left, fraction)
                val top = mix(flight.from.top, to.top, fraction)

                // 旋转样式：飞行中从 0 放大到原尺寸（对齐 Dart 的 ScaleTransition）。
                val contentScale = if (flight.style == TransitionStyle.Rotate) fraction else 1f
                scaleX = if (fromWidth > 0f) width / fromWidth * contentScale else contentScale
                scaleY = if (fromHeight > 0f) height / fromHeight * contentScale else contentScale

                // transformOrigin 默认在图层中心：绕中心缩放后，把中心搬到目标矩形中心即可。
                translationX = left + width / 2f - fromWidth / 2f
                translationY = top + height / 2f - fromHeight / 2f

                rotationZ = if (flight.style == TransitionStyle.Rotate) {
                    FULL_TURN_DEGREES * smoothInOut(fraction)
                } else {
                    0f
                }

                // 圆角插值走 RenderNode 的 outline 裁剪，比每帧重建 clipPath 便宜。
                val radius = mix(flight.fromRadius.value, flight.toRadius.value, fraction)
                shape = RoundedCornerShape(radius.dp)
                clip = true
            },
    ) {
        AsyncImage(
            model = request,
            contentDescription = flight.description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}
