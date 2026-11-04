package com.liomix.mikomi.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
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
 * 不重建 Shader、不分配临时对象。
 *
 * @param baseColor 占位底色；默认由主题推导成半透明墨色
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
    val ink = MaterialTheme.colorScheme.onSurface
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Telegram 的底色是 listSelector（5.9% 黑）再抬到 alpha 22：浅色 8.6% 黑、夜间 9.4% 白；
    // 亮带在浅色里是更淡的 3.5%（等于「擦亮」），夜间是更亮的 12% 白。
    // 色相统一取 onSurface —— 深浅两套都只是同一支笔的不同不透明度。
    val base = baseColor ?: ink.copy(alpha = if (night) NightBaseAlpha else DayBaseAlpha)
    val sweep = sweepColor ?: ink.copy(alpha = if (night) NightSweepAlpha else DaySweepAlpha)

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    // 位移行程：-2×渐变宽 → 2×列表宽（列表宽这里用屏幕宽代替）。
    val gradientWidth = with(density) { SweepGradientWidth.toPx() }
    val travelWidth = with(density) { screenWidthDp.toPx() }.coerceAtLeast(1f)
    val travel = 2f * gradientWidth + 2f * travelWidth
    // 周期由「每 400ms 平移一个列表宽」反推，而不是写死时长。
    val cycleMillis = (travel / (travelWidth / SweepPerWidthMillis)).toInt().coerceAtLeast(1)
    val progress = rememberShimmerProgress(cycleMillis)

    // 画笔 / shader / 矩阵只建一次：色标与渐变宽都和滚动位置无关，每帧要变的只有
    // shader 的 localMatrix。Telegram 也是这么做的（skeletonPaint + setLocalMatrix）；
    // 而 Compose 的 Brush 每次绘制都会重新生成 shader，十几块骨架就是每帧十几个对象。
    val sweepPaint = remember(base, sweep, gradientWidth) {
        SkeletonSweepPaint(base, sweep, gradientWidth)
    }

    return clip(shape).drawBehind {
        val x = -2f * gradientWidth + progress.value * travel
        drawIntoCanvas { canvas ->
            sweepPaint.draw(canvas.nativeCanvas, size.width, size.height, x)
        }
    }
}

/** 无限循环的扫光进度；读取方在 Draw 阶段取值，保持阶段延迟。 */
@Composable
private fun rememberShimmerProgress(cycleMillis: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonProgress",
    )
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
    private val matrix = android.graphics.Matrix()
    private val shader = android.graphics.LinearGradient(
        0f,
        0f,
        gradientWidth,
        0f,
        intArrayOf(base.toArgb(), sweep.toArgb(), sweep.toArgb(), base.toArgb()),
        floatArrayOf(0f, 0.4f, 0.6f, 1f),
        android.graphics.Shader.TileMode.CLAMP,
    )
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.shader = this@SkeletonSweepPaint.shader
    }

    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, translateX: Float) {
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
