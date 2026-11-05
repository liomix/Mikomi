package com.liomix.mikomi.core.ui.component.base

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 骨架屏基础块：带扫光动画的占位块。
 *
 * 只需要一块矩形时直接用；更复杂的骨架结构（头像 + 多行文字、卡片内嵌块等）
 * 用 [shimmer] 修饰符贴到任意布局上即可，两者共用同一套扫光实现。
 *
 * 视觉与动画参数全部对齐 Telegram 消息列表的加载骨架（`ChatActivity` 的
 * `updateSkeletonColors` / `updateSkeletonGradient`）：
 *
 * - **不是「灰块上扫一道白光」**，而是一整块半透明的墨色，纱窗式的亮带从中掠过 ——
 *   渐变窗口两端就是底色，中间 20% 才是亮带，所以带子边缘是 80dp 的柔化过渡，没有硬边。
 * - 渐变宽 200dp、色标 `[0, 0.4, 0.6, 1]`、`TileMode.Clamp`（窗口外自动回到底色）。
 * - 速度是「每 400ms 平移一个列表宽度」，而不是固定时长 —— 大屏上绝对速度快、相对观感一致。
 *
 * 帧开销与 Telegram 一致：Paint / shader / 矩阵在组合阶段建好，每帧只 `setLocalMatrix`，
 * 不重建 Shader、不分配临时对象。整屏多块骨架的时间线合并见 [SkeletonShimmer]。
 *
 * @param baseColor 占位底色；默认由主题推导成半透明墨色，见 [skeletonBaseColor]
 * @param sweepColor 扫光带颜色；默认比底色更亮一档
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color? = null,
    sweepColor: Color? = null,
) {
    Box(
        modifier = modifier.shimmer(
            shape = shape,
            baseColor = baseColor,
            sweepColor = sweepColor,
        ),
    )
}

/**
 * 骨架占位底色（默认档）。
 *
 * 给「图片还没画出来」的容器垫底用：与骨架块完全同色，所以骨架 → 图片的过渡里
 * 任何一帧都不会露出页面底色（也就是「空白块」）。见 `AnimeCoverImage`。
 */
@Composable
fun skeletonBaseColor(): Color = rememberSkeletonColors().base

/**
 * 一屏骨架共用的扫光参数。
 *
 * @property progress 扫光进度，只在绘制阶段读取
 * @property gradientWidth 渐变窗口宽度（px）
 * @property travel 一次循环的位移行程（px）
 */
private class ShimmerSweep(
    val progress: State<Float>,
    val gradientWidth: Float,
    val travel: Float,
)

/** 当前生效的共享扫光；为 null 时由每块骨架自建一条时间线。 */
private val LocalShimmerSweep = staticCompositionLocalOf<ShimmerSweep?> { null }

/**
 * 骨架屏容器：一屏内所有骨架共用**一条**扫光时间线。
 *
 * Telegram 的消息骨架只有一份 `skeletonGradient` 和一个 `skeletonTotalTranslation`，
 * 几十个气泡共用同一个位移量；这里照同样思路把时间线提到容器层。
 *
 * 为什么值得单拎出来：每条时间线都是「一条按帧唤醒的协程 + 每帧写一次状态」。
 * 12 张卡片骨架连同其文字块就是二十多条，同一帧里各自写入、各自失效、各自重绘；
 * 提到容器后整屏只剩一条，而且所有块的相位天然一致（不会因为组合先后而错开）。
 *
 * 只在骨架**真正显示**的那一小段 UI 上包这一层：骨架消失时容器一起离开组合，时间线随之停止，
 * 不会留下一条空转的无限动画。
 */
@Composable
fun SkeletonShimmer(content: @Composable () -> Unit) {
    val sweep = rememberShimmerSweep()
    CompositionLocalProvider(LocalShimmerSweep provides sweep, content = content)
}

/**
 * shimmer 扫光修饰符：可贴在任意布局上，用它扩展自定义骨架屏。
 *
 * 扫光进度在 [drawBehind] 内读取：状态变化只触发 Draw 阶段重绘，
 * 不经过组合阶段重组，长列表多块骨架同时播放也不会带来额外重组开销。
 */
@Composable
fun Modifier.shimmer(
    shape: Shape = RoundedCornerShape(8.dp),
    baseColor: Color? = null,
    sweepColor: Color? = null,
): Modifier {
    val colors = rememberSkeletonColors()
    val base = baseColor ?: colors.base
    val sweep = sweepColor ?: colors.sweep

    // 容器提供了共享时间线就复用它，否则自建一条（单块骨架的常见场景）。
    val shared = LocalShimmerSweep.current
    val spec = if (shared != null) shared else rememberShimmerSweep()
    val gradientWidth = spec.gradientWidth

    // 画笔 / shader / 矩阵只建一次：色标与渐变宽都和滚动位置无关，每帧要变的只有
    // shader 的 localMatrix。Telegram 也是这么做的（skeletonPaint + setLocalMatrix）；
    // 而 Compose 的 Brush 每次绘制都会重新生成 shader，十几块骨架就是每帧十几个对象。
    val sweepPaint = remember(base, sweep, gradientWidth) {
        SkeletonSweepPaint(base, sweep, gradientWidth)
    }

    return clip(shape).drawBehind {
        val x = -2f * gradientWidth + spec.progress.value * spec.travel
        drawIntoCanvas { canvas ->
            sweepPaint.draw(canvas.nativeCanvas, size.width, size.height, x)
        }
    }
}

/**
 * 建一份扫光参数。
 *
 * 周期由「每 400ms 平移一个列表宽」反推（列表宽这里用屏幕宽代替），而不是写死时长 ——
 * 大屏绝对速度快、观感一致。
 */
@Composable
private fun rememberShimmerSweep(): ShimmerSweep {
    val density = LocalDensity.current
    val screenWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }.coerceAtLeast(1f)
    val gradientWidth = with(density) { SweepGradientWidth.toPx() }
    // 行程：-2×渐变宽 → 2×列表宽。
    val travel = 2f * gradientWidth + 2f * screenWidthPx
    val cycleMillis = (travel / (screenWidthPx / SweepPerWidthMillis)).toInt().coerceAtLeast(1)
    val progress = rememberShimmerProgress(cycleMillis)
    return remember(progress, gradientWidth, travel) {
        ShimmerSweep(progress, gradientWidth, travel)
    }
}

/**
 * 扫光进度：按帧驱动，相位锚在**帧时钟的绝对时间**上。
 *
 * 为什么不用 `rememberInfiniteTransition`（两者算力开销相近，但这个更可控）：
 *
 * 1. **不会「卡住不动」**。无限动画的实例是 remember 出来的，而懒列表（`LazyVerticalGrid`）
 *    会把划出屏幕的 item 连同其 `remember` 一起放进复用池，重新进视口时只是**重新激活**，
 *    实例与状态都还在。由内部帧回调驱动的无限动画在「停用 → 复用 → 重新激活」这条路径上
 *    有可能不再注册帧回调，表现就是「块还在，扫光不动」。
 *    `LaunchedEffect` 在停用时被取消、激活时重新启动，是确定会重新跑起来的。
 * 2. **相位天然对齐**。进度直接由 `frameTimeNanos`（`CLOCK_MONOTONIC`）算出，
 *    任何时刻进组合的块，其位置都等于「当前绝对时间在一个周期里的位置」——
 *    骨架块与「骨架消失后接手的封面占位」是两棵互不相干的节点，也不会各自从头开始；
 *    一屏所有骨架的亮带永远对齐（Telegram 本来也是一份 gradient 全局共用）。
 *
 * 只在骨架显示期间存在：宿主离开组合，协程随之取消。
 */
@Composable
private fun rememberShimmerProgress(cycleMillis: Int): State<Float> {
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(cycleMillis) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val millis = frameTimeNanos / 1_000_000L
                progress.floatValue = (millis % cycleMillis).toFloat() / cycleMillis
            }
        }
    }
    return progress
}

/** 骨架的两种墨色：底色与扫光亮带。 */
private class SkeletonColors(val base: Color, val sweep: Color)

/**
 * 骨架配色：色相统一取 `onSurface`，深浅两套只是同一支笔的不同不透明度。
 *
 * Telegram 的底色是 `listSelector`（5.9% 黑）再抬到 alpha 22：浅色 8.6% 黑、夜间 9.4% 白；
 * 亮带在浅色里是更淡的 3.5%（等于「擦亮」），夜间是更亮的 12% 白。
 */
@Composable
private fun rememberSkeletonColors(): SkeletonColors {
    val ink = MaterialTheme.colorScheme.onSurface
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(ink, night) {
        SkeletonColors(
            base = ink.copy(alpha = if (night) NightBaseAlpha else DayBaseAlpha),
            sweep = ink.copy(alpha = if (night) NightSweepAlpha else DaySweepAlpha),
        )
    }
}

/**
 * 一块骨架的扫光画笔。
 *
 * 四个色标 `[0, .4, .6, 1]` 配 `TileMode.CLAMP`：渐变窗口外自动落回底色，
 * 所以整块只画一次 `drawRect`，不需要「先铺底色再叠亮带」那种两层叠加。
 */
private class SkeletonSweepPaint(
    base: Color,
    sweep: Color,
    gradientWidth: Float,
) {
    private val matrix = Matrix()
    private val shader = LinearGradient(
        0f,
        0f,
        gradientWidth,
        0f,
        intArrayOf(base.toArgb(), sweep.toArgb(), sweep.toArgb(), base.toArgb()),
        floatArrayOf(0f, 0.4f, 0.6f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.shader = this@SkeletonSweepPaint.shader
    }

    fun draw(canvas: Canvas, width: Float, height: Float, translateX: Float) {
        matrix.setTranslate(translateX, 0f)
        shader.setLocalMatrix(matrix)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

/** 渐变窗口宽度（Telegram `AndroidUtilities.dp(200)`）。 */
private val SweepGradientWidth = 200.dp

/** 扫光每平移一个列表宽所需的时间（Telegram `dt * width / 400`）。 */
private const val SweepPerWidthMillis = 400f

/** 浅色底色：Telegram `setAlpha(listSelector, 22)`。 */
private const val DayBaseAlpha = 22f / 255f

/** 浅色亮带：Telegram 把 listSelector 与 3/255 白混合后得到 alpha 9，这里取同量级。 */
private const val DaySweepAlpha = 9f / 255f

/** 夜间底色：Telegram `setAlpha(listSelector, 24)`。 */
private const val NightBaseAlpha = 24f / 255f

/** 夜间亮带：Telegram alpha 31 的白。 */
private const val NightSweepAlpha = 31f / 255f
