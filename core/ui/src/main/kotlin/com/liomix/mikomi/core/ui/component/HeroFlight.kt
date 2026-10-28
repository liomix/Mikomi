package com.liomix.mikomi.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.liomix.mikomi.core.model.TransitionStyle
import kotlin.math.roundToInt

/**
 * 封面飞行（Flutter `Hero` 的等价实现）。
 *
 * 为什么不直接用 Compose 的 `SharedTransitionLayout` + `Modifier.sharedElement`：
 * Compose 的共享元素是**画在页面内部**的，页面自带位移/缩放时会把飞行元素一起拖走、被页面边界裁掉，
 * 只能靠牺牲页面转场来迁就它。Flutter 的 Hero 则画在 Navigator 的 Overlay 上，与路由转场完全解耦。
 * 这里照 Flutter 的做法自己实现：起点/终点各自上报自身位置，飞行本体绘制在页面之上的 overlay 里。
 *
 * 文案对齐 Dart 版 `AnimationProvider.buildHeroFlightShuttle`：
 * 标准 = 原样飞行、淡入淡出 = 飞行中淡入、缩放 = 0.8 → 1 放大。
 */
private const val FLIGHT_DURATION_MILLIS = 300

/** 旋转样式转一整圈（度）。 */
private const val FULL_TURN_DEGREES = 360f

/** 平滑进出缓动（≈ easeInOut），旋转用它避免起止生硬。 */
private fun smoothInOut(t: Float): Float = t * t * (3f - 2f * t)

/** 一次飞行的参数快照。 */
@Immutable
internal data class HeroEndpoint(
    val bounds: Rect,
    val radius: Dp,
    val url: String?,
    val description: String?,
)

/** 正在进行的飞行。 */
@Immutable
data class HeroFlight(
    /** 单次飞行的稳定标识：飞行途中终点会被逐帧修正，但动画进度不能因此重置。 */
    val id: Long,
    val key: Any,
    val url: String?,
    val description: String?,
    val from: Rect,
    val to: Rect,
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

    /** 当前飞行；`null` 表示没有飞行。 */
    var flight by mutableStateOf<HeroFlight?>(null)
        private set

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
        // 反向飞行时列表页也在滑回原位，同样要持续跟随。
        val current = flight
        if (current != null && current.key == key && current.reverse) {
            flight = current.copy(to = endpoint.bounds, toRadius = endpoint.radius)
        }
    }

    /** 详情封面（终点）上报自身位置；若正在等这个 key 就起飞。 */
    internal fun onTargetLaidOut(key: Any, endpoint: HeroEndpoint) {
        targets[key] = endpoint
        // 飞行途中目标还在被页面转场推着走（滑入/缩放），持续跟随才能落到最终位置。
        val current = flight
        if (current != null && current.key == key && !current.reverse) {
            flight = current.copy(to = endpoint.bounds, toRadius = endpoint.radius)
        }
        if (pendingKey != key) return
        val source = sources[key] ?: return
        pendingKey = null
        flight = HeroFlight(
            id = ++nextFlightId,
            key = key,
            url = endpoint.url ?: source.url,
            description = endpoint.description ?: source.description,
            from = source.bounds,
            to = endpoint.bounds,
            fromRadius = source.radius,
            toRadius = endpoint.radius,
            style = style,
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
        flight = HeroFlight(
            id = ++nextFlightId,
            key = resolved,
            url = target.url ?: source.url,
            description = target.description ?: source.description,
            from = target.bounds,
            to = source.bounds,
            fromRadius = target.radius,
            toRadius = source.radius,
            style = style,
            reverse = true,
        )
    }

    /** 飞行结束：解除两端的隐藏，并记住 key 供返回使用。 */
    fun finish(id: Long) {
        val current = flight ?: return
        if (current.id != id) return
        lastFlewKey = current.key
        flight = null
    }

    /** 该 key 的封面此刻是否正由 overlay 绘制（页面内的副本应当隐藏）。 */
    fun isFlying(key: Any?): Boolean = key != null && flight?.key == key
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
 * 放在根容器里、导航内容之上即可：页面转场怎么滑/怎么缩都不会影响它。
 */
@Composable
fun HeroFlightOverlay(
    controller: HeroFlightController,
    modifier: Modifier = Modifier,
) {
    val flight = controller.flight ?: return
    // 用飞行 id 而不是 flight 本身作 key：飞行途中终点会被逐帧修正（copy 出新实例），
    // 若以实例为 key，动画进度每帧都会被重置。
    val progress = remember(flight.id) { Animatable(0f) }
    LaunchedEffect(flight.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = FLIGHT_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
        )
        controller.finish(flight.id)
    }

    val fraction = progress.value
    val bounds = lerp(flight.from, flight.to, fraction)
    // 内容变换只在「旋转」时生效：转一圈并从 0 放大到原始尺寸
    //（Dart 的 RotationTransition + ScaleTransition(scale: animation)）；「平移」原样飞行。
    val contentScale = if (flight.style == TransitionStyle.Rotate) fraction else 1f
    val contentRotation = if (flight.style == TransitionStyle.Rotate) {
        FULL_TURN_DEGREES * smoothInOut(fraction)
    } else {
        0f
    }
    val density = LocalDensity.current
    val context = LocalContext.current
    val request = remember(flight.url, context) {
        ImageRequest.Builder(context)
            .data(flight.url)
            .memoryCacheKey(flight.url)
            .build()
    }

    // 注意：这里绝不能带 fillMaxSize——外层限定尺寸会把下面的 size() 压回整屏，
    // 海报就会变成「永远整屏大小、只跟 offset 移动」。
    Box(
        modifier = modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(
                width = with(density) { bounds.width.toDp() },
                height = with(density) { bounds.height.toDp() },
            )
            .graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
                rotationZ = contentRotation
            }
            .clip(RoundedCornerShape(lerp(flight.fromRadius, flight.toRadius, fraction))),
    ) {
        AsyncImage(
            model = request,
            contentDescription = flight.description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}
