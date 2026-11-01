package com.liomix.mikomi.feature.settings.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.feature.settings.R
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 切换行上报给宿主的图标状态。
 *
 * [progress] / [tint] 是**绘制时**才求值的 lambda（内部读 Animatable 与动画状态），
 * 这样揭示层能跟图标保持同一进度，又不必每帧触发一次重组。
 */
internal class NightModeIconState(
    /** 图标在根窗口中的矩形：中心即揭示原点，最小边即图标直径。 */
    val boundsInRoot: Rect,
    /** 太阳 → 月牙的形变进度（0 = 太阳，1 = 月牙）。 */
    val progress: () -> Float,
    /** 图标当前颜色（揭示结束时在旧 / 新主题色之间过渡）。 */
    val tint: () -> Color,
)

/**
 * 日 / 夜切换控制器：由 [DarkModeRevealHost] 通过 CompositionLocal 提供，
 * [BAndWSwitch] 直接取用，调用方无需把回调层层透传。
 */
@Stable
class DarkModeToggleController internal constructor(
    /** 触发一次带圆形揭示动画的模式切换。 */
    internal val toggle: () -> Unit,
    /** 上报图标位置 / 尺寸 / 形变进度，供揭示层复用。 */
    internal val updateIcon: (NightModeIconState) -> Unit,
)

internal val LocalDarkModeToggleController =
    staticCompositionLocalOf<DarkModeToggleController?> { null }

/**
 * 日 / 夜切换宿主：包住页面内容，负责圆形揭示动画的全部逻辑。
 *
 * 复刻 Telegram 聊天设置页的那套动效：
 * - 点击瞬间用 [PixelCopy] 把**当前界面**（旧主题）整块抓成位图，盖在新界面之上；
 * - 日 → 夜：位图从图标中心被圆形挖开，新界面由内向外铺满（400ms，easeInOutQuad）；
 * - 夜 → 日：位图收成一个越缩越小的圆，新界面由外向内接管；
 * - 抓不到位图时退化为「瞬时切换」，不留下半透明遮罩。
 *
 * 为什么不用 `GraphicsLayer.toImageBitmap()`：它把 Compose 的 RenderNode 往软件 Canvas 上重放，
 * 在多数机型上得到的是全透明位图（尺寸正常、内容为空），表现为「主题直接跳变、没有揭示动画」。
 * [PixelCopy] 回读的是窗口 GPU 合成后的真实像素，因而与屏幕上看到的一帧逐像素一致。
 */
@Composable
fun DarkModeRevealHost(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentIsDark by rememberUpdatedState(isDarkMode)

    // 0 = 圆还没张开（整屏仍是旧画面），1 = 揭示完成（整屏都是新画面）。
    val reveal = remember { Animatable(0f) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var revealToDark by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var hostLeftInRoot by remember { mutableStateOf(Offset.Zero) }
    var iconCenterInRoot by remember { mutableStateOf(Offset.Zero) }
    var iconUnit by remember { mutableStateOf(0f) }
    var iconProgress by remember { mutableStateOf<() -> Float>({ 0f }) }
    var iconTint by remember { mutableStateOf<() -> Color>({ Color.Unspecified }) }

    val toggle: () -> Unit = {
        // 抓帧是异步的，用 busy 同步上锁，避免连点时抓到同一帧或切两次主题。
        if (!busy) {
            busy = true
            val targetDark = !currentIsDark
            scope.launch {
                try {
                    val region = hostRegionInWindow(view, hostLeftInRoot, hostSize)
                    val shot = region?.let { captureWindowRegion(view, it) }
                    if (shot == null) {
                        // 拿不到旧画面（极老版本 / 抓帧失败）：直接切，不留遮罩。
                        onToggleDarkMode(targetDark)
                        return@launch
                    }
                    snapshot = shot
                    revealToDark = targetDark
                    reveal.snapTo(0f)
                    // 顺序很关键：先让遮罩按旧画面铺满两帧，再切主题。
                    // 这样主题换掉的那一帧已经被旧画面盖住，不会闪出新界面。
                    withFrameNanos { }
                    withFrameNanos { }
                    onToggleDarkMode(targetDark)
                    awaitThemeChange(target = targetDark, current = { currentIsDark })
                    reveal.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = REVEAL_DURATION_MILLIS,
                            easing = RevealEasing,
                        ),
                    )
                } finally {
                    snapshot = null
                    busy = false
                }
            }
        }
    }

    val latestToggle by rememberUpdatedState(toggle)
    val controller = remember {
        DarkModeToggleController(
            toggle = { latestToggle() },
            updateIcon = { icon ->
                iconCenterInRoot = icon.boundsInRoot.center
                iconUnit = icon.boundsInRoot.minDimension
                iconProgress = icon.progress
                iconTint = icon.tint
            },
        )
    }

    CompositionLocalProvider(LocalDarkModeToggleController provides controller) {
        Box(
            modifier = modifier
                .onGloballyPositioned { hostLeftInRoot = it.boundsInRoot().topLeft }
                .onSizeChanged { hostSize = it },
        ) {
            content()

            val shot = snapshot
            if (shot != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithContent {
                            val origin = iconCenterInRoot - hostLeftInRoot
                            val maxRadius = maxRevealRadius(size.width, size.height, origin)
                            if (maxRadius <= 0f) return@drawWithContent

                            val progress = reveal.value
                            // 日 → 夜：圆从 0 长大，圆外仍是旧画面；夜 → 日：旧画面收成一个
                            // 越来越小的圆，新界面从四周接管（与 Telegram 两个方向一致）。
                            val radius = if (revealToDark) {
                                maxRadius * progress
                            } else {
                                maxRadius * (1f - progress)
                            }
                            if (radius <= MIN_VISIBLE_RADIUS) {
                                if (revealToDark) drawImage(shot)
                                return@drawWithContent
                            }

                            val circle = Rect(center = origin, radius = radius)
                            if (revealToDark) {
                                // 日 → 夜：新画面从图标向外发散 —— 旧画面挖掉一个正圆（EvenOdd），
                                // 圆内露出新界面，圆外仍是旧界面。
                                val hole = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(Rect(Offset.Zero, size))
                                    addOval(circle)
                                }
                                clipPath(hole) { drawImage(shot) }
                            } else {
                                // 夜 → 日：旧画面收成一个越缩越小的圆，最后缩回图标那一点。
                                clipPath(Path().apply { addOval(circle) }) { drawImage(shot) }
                                // 旧画面此时压在真图标上面，补画一枚同进度、同色的图标，
                                // 让太阳在「旧画面缩回图标」的全过程中都看得见（Telegram 叠的是 48dp 图标视图）。
                                drawSunMoonLayers(
                                    progress = iconProgress(),
                                    tint = iconTint(),
                                    center = origin,
                                    unit = iconUnit,
                                )
                            }
                        },
                )
            }
        }
    }
}

/**
 * 抓取 [view] 所在窗口的一块区域（窗口坐标），作为揭示动画的「旧画面」。
 *
 * 只读窗口表面、不改动界面：调用后屏幕内容保持原样，回调里拿到的就是刚才显示的那一帧。
 * 失败（版本过低 / 窗口不可读）返回 `null`，调用方退化为瞬时切换。
 */
private suspend fun captureWindowRegion(view: View, region: Rect): ImageBitmap? {
    // PixelCopy 的 Window 重载从 Android 8.0 才有。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val activity = view.context.findActivity() ?: return null
    val window = activity.window
    val decor = window.decorView
    val width = decor.width
    val height = decor.height
    if (width <= 0 || height <= 0) return null

    val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val result = suspendCancellableCoroutine { continuation ->
        PixelCopy.request(
            window,
            full,
            { code -> if (continuation.isActive) continuation.resume(code) },
            Handler(Looper.getMainLooper()),
        )
    }
    if (result != PixelCopy.SUCCESS) {
        Log.w(REVEAL_LOG_TAG, "PixelCopy failed: code=$result")
        full.recycle()
        return null
    }

    // 只保留宿主区域，遮罩按 (0, 0) 贴回即可，省掉绘制时的坐标换算。
    val left = region.left.roundToInt().coerceIn(0, full.width - 1)
    val top = region.top.roundToInt().coerceIn(0, full.height - 1)
    val cropWidth = region.width.roundToInt().coerceIn(1, full.width - left)
    val cropHeight = region.height.roundToInt().coerceIn(1, full.height - top)
    val cropped = Bitmap.createBitmap(full, left, top, cropWidth, cropHeight)
    if (cropped !== full) full.recycle()
    return cropped.asImageBitmap()
}

/** 宿主（整页内容）在窗口中的矩形：Compose 的 root 坐标 + ComposeView 在窗口内的偏移。 */
private fun hostRegionInWindow(view: View, leftInRoot: Offset, size: IntSize): Rect? {
    if (size.width <= 0 || size.height <= 0) return null
    val location = IntArray(2)
    view.getLocationInWindow(location)
    return Rect(
        offset = Offset(leftInRoot.x + location[0], leftInRoot.y + location[1]),
        size = Size(size.width.toFloat(), size.height.toFloat()),
    )
}

/** 从 [Context] 往上找宿主 Activity（Compose 的 context 可能是包装过的）。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 日 / 夜切换行（复刻 Telegram 聊天设置页的「Night Mode」一行）。
 *
 * 布局取自 Telegram `TextCell`：行高 50dp、图标 28dp 且左边距 21dp、
 * 文字从 58dp 起、整行蓝色。图标是自绘的「太阳 → 月牙」变形动画
 * （36 帧 / 600ms，与 Telegram 的 `sun_outline.json` 同一时间轴）：
 * 8 条射线先微微弹出再缩回圆内，圆被一个滑入的圆咬成月牙。
 *
 * @param onClick 未包在 [DarkModeRevealHost] 内时的兜底点击（可选）。
 */
@Composable
fun BAndWSwitch(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val controller = LocalDarkModeToggleController.current

    // 0 = 太阳（日间），1 = 月牙（夜间）。切换时从当前进度继续，不回跳。
    val morph = remember { Animatable(if (isDarkMode) 1f else 0f) }
    LaunchedEffect(isDarkMode) {
        morph.animateTo(
            targetValue = if (isDarkMode) 1f else 0f,
            animationSpec = tween(
                durationMillis = SUN_MOON_DURATION_MILLIS,
                easing = MorphEasing,
            ),
        )
    }

    // Telegram 在揭示结束后用 350ms 把图标色从旧主题色混到新主题色。
    val tint by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = ICON_TINT_MILLIS),
        label = "nightModeIconTint",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ToggleRowHeight)
            .clickable {
                controller?.toggle?.invoke() ?: onClick?.invoke()
            }
            .padding(start = ToggleRowStartPadding, end = ToggleRowEndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SunMoonMorphIcon(
            progress = morph.value,
            tint = tint,
            modifier = Modifier
                .size(IconSize)
                .onGloballyPositioned { coordinates ->
                    controller?.updateIcon(
                        NightModeIconState(
                            boundsInRoot = coordinates.boundsInRoot(),
                            progress = { morph.value },
                            tint = { tint },
                        ),
                    )
                },
        )
        Spacer(modifier = Modifier.width(IconToTextGap))
        Text(
            text = stringResource(
                if (isDarkMode) R.string.theme_day_mode else R.string.theme_night_mode,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}

/**
 * 太阳 ↔ 月牙变形图标（自绘）。
 *
 * 时间轴对齐 Telegram 的 `sun_outline.json`（36 帧 @60fps）：
 * - 0 → 14%：射线由 1 鼓到 1.2 倍；
 * - 14% → 36%：射线缩回圆内；
 * - 8% → 55%：一个圆从右侧滑进来，把圆盘咬成月牙，同时整体轻微缩放到 93% 再回弹；
 * - 成形过程中整体转 -38°，让月牙缺口落在右上，符合常见的月亮形态。
 */
@Composable
private fun SunMoonMorphIcon(
    progress: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (size.minDimension <= 0f) return@Canvas
        drawSunMoonLayers(
            progress = progress,
            tint = tint,
            center = Offset(size.width / 2f, size.height / 2f),
            unit = size.minDimension,
        )
    }
}

/**
 * 把「太阳 → 月牙」画到任意画布上：图标本体与揭示层（夜 → 日时盖在旧画面之上的那枚图标）
 * 共用同一份绘制指令，保证两处形状/进度完全一致。
 *
 * @param center 图标中心
 * @param unit 图标边长（所有尺寸都是它的倍数）
 */
private fun DrawScope.drawSunMoonLayers(
    progress: Float,
    tint: Color,
    center: Offset,
    unit: Float,
) {
    if (unit <= 0f) return

    val bodyRadius = unit * BODY_RADIUS_RATIO
    val outline = Stroke(
        width = unit * STROKE_RATIO,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )

    val moon = smoothStep(0.08f, 0.55f, progress)
    val rayGrow = smoothStep(0f, 0.14f, progress)
    val rayShrink = smoothStep(0.14f, 0.36f, progress)
    val rayExtent = (1f + RAY_GROW_BONUS * rayGrow) * (1f - rayShrink)
    // 先缩到 93% 再回弹：Lottie 里月牙层的 scale 关键帧就是这个节奏。
    val bodyScale = 1f - BODY_SHRINK * sin(moon * Math.PI.toFloat())

    // 月牙比圆盘画得大：太阳的视觉尺寸有一圈射线撑着（外沿约 0.79×边长），
    // 月牙却只有被咬剩下的那瓣，同半径下看起来只有太阳的一半。放大到 [MOON_RADIUS_BOOST] 倍
    // 让两者的「视觉重量」相称（圆盘直径 0.40×边长 → 月牙 0.64×边长）。
    val moonRadius = bodyRadius * (1f + MOON_RADIUS_BOOST * moon)

    rotate(degrees = MOON_ROTATION_DEGREES * moon, pivot = center) {
        scale(scaleX = bodyScale, scaleY = bodyScale, pivot = center) {
            if (rayExtent > RAY_MIN_EXTENT) {
                val rayStart = bodyRadius * RAY_INNER_RATIO
                val rayEnd = rayStart + bodyRadius * RAY_LENGTH_RATIO * rayExtent
                repeat(RAY_COUNT) { index ->
                    val angle = Math.toRadians((index * (360 / RAY_COUNT)).toDouble())
                    val cosA = cos(angle).toFloat()
                    val sinA = sin(angle).toFloat()
                    drawLine(
                        color = tint,
                        start = Offset(
                            x = center.x + cosA * rayStart,
                            y = center.y + sinA * rayStart,
                        ),
                        end = Offset(
                            x = center.x + cosA * rayEnd,
                            y = center.y + sinA * rayEnd,
                        ),
                        strokeWidth = outline.width,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // 圆盘 → 月牙：咬口圆从右外侧滑进来（moon = 0 时与圆盘不相交）。
            val body = Path().apply { addOval(Rect(center = center, radius = moonRadius)) }
            val bite = Path().apply {
                addOval(
                    Rect(
                        center = Offset(
                            x = center.x + moonRadius * mix(BITE_START, BITE_END, moon),
                            y = center.y,
                        ),
                        radius = moonRadius * BITE_RADIUS_RATIO,
                    ),
                )
            }
            val crescent = if (moon > 0.001f) {
                Path.combine(PathOperation.Difference, body, bite)
            } else {
                body
            }
            drawPath(path = crescent, color = tint, style = outline)
        }
    }
}

/** 等主题真正切换（DataStore 写入是异步的），避免揭示到一半露出旧主题。 */
private suspend fun awaitThemeChange(
    target: Boolean,
    current: () -> Boolean,
) {
    withTimeoutOrNull(THEME_CHANGE_TIMEOUT_MILLIS) {
        snapshotFlow(current).first { it == target }
    }
}

/** 从原点扩散到最远角所需的半径。 */
private fun maxRevealRadius(width: Float, height: Float, origin: Offset): Float {
    if (width <= 0f || height <= 0f) return 0f
    val dx = maxOf(origin.x, width - origin.x)
    val dy = maxOf(origin.y, height - origin.y)
    return hypot(dx, dy)
}

/** 平滑插值（smoothstep），用于把线性进度映射成自然的形变节奏。 */
private fun smoothStep(start: Float, end: Float, value: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 线性插值。 */
private fun mix(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

/** 揭示动画的日志标签。 */
private const val REVEAL_LOG_TAG = "NightModeReveal"

/** 切换行的图标直径（Telegram 的 `dp(28)`）。 */
private val IconSize = 28.dp

/** 图标左边距（Telegram `imageLeft = 21`）。 */
private val ToggleRowStartPadding = 21.dp

/** 行尾留白。 */
private val ToggleRowEndPadding = 20.dp

/** 行高（Telegram `TextCell.heightDp = 50`）。 */
private val ToggleRowHeight = 50.dp

/** 图标与文字的间距：21 + 28 + 9 = 58，对齐 Telegram 的 `offsetFromImage`。 */
private val IconToTextGap = 9.dp

/**
 * 图标图形的基准半径（占控件边长的比例）。
 *
 * 太阳外沿 = 0.20 × (1 + 1.45 + 0.52) ≈ 控件边长的 79%，
 * 在 28dp 行内留出呼吸空间（原先 0.24 会顶满整个方框，视觉上偏大）。
 */
private const val BODY_RADIUS_RATIO = 0.20f

/** 描边宽度占比（28dp 图标下约 2.1dp）。 */
private const val STROKE_RATIO = 0.075f

/**
 * 月牙相对圆盘的放大倍数（moon = 1 时生效）。
 *
 * 太阳的外沿被射线撑到 0.79×边长，月牙只是一瓣被咬剩的主体，同半径下视觉上小一半，
 * 因此单独放大到 1.6 倍：月牙直径 0.64×边长（约 17.9dp），与太阳（约 22dp）相称。
 */
private const val MOON_RADIUS_BOOST = 0.6f

/** 射线数量：8 条，每 45° 一条。 */
private const val RAY_COUNT = 8

/** 射线的内端半径（相对圆盘半径）。 */
private const val RAY_INNER_RATIO = 1.45f

/** 射线长度（相对圆盘半径）。 */
private const val RAY_LENGTH_RATIO = 0.52f

/** 射线鼓起时的额外长度（1 → 1.2 倍）。 */
private const val RAY_GROW_BONUS = 0.2f

/** 射线短于这个比例就不再画，避免留下一个圆点。 */
private const val RAY_MIN_EXTENT = 0.02f

/** 月牙成形时整体收缩的幅度。 */
private const val BODY_SHRINK = 0.07f

/** 咬口圆的半径（相对圆盘半径）。 */
private const val BITE_RADIUS_RATIO = 0.86f

/** 咬口圆起止位置：从圆盘右外侧滑到略微重叠。 */
private const val BITE_START = 2.4f
private const val BITE_END = 0.72f

/** 月牙成形时的旋转角度：缺口朝右上。 */
private const val MOON_ROTATION_DEGREES = -38f

/** 太阳 ↔ 月牙的形变时长：36 帧 @60fps。 */
private const val SUN_MOON_DURATION_MILLIS = 600

/** 图标颜色的过渡时长（Telegram 揭示结束后 350ms 混色）。 */
private const val ICON_TINT_MILLIS = 350

/** 揭示时长。 */
private const val REVEAL_DURATION_MILLIS = 400

/** 圆半径小于该值（px）时认为「圆还没出现」，直接跳过裁剪。 */
private const val MIN_VISIBLE_RADIUS = 0.5f

/** 揭示缓动：easeInOutQuad，与 Telegram 的 `Easings.easeInOutQuad` 一致。 */
private val RevealEasing = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

/** 形变缓动：起步快、收尾稳，接近 Lottie 关键帧的观感。 */
private val MorphEasing = CubicBezierEasing(0.3f, 0f, 0.25f, 1f)

/** 等待主题切换的超时（超出后继续，避免停在遮罩上）。 */
private const val THEME_CHANGE_TIMEOUT_MILLIS = 250L
