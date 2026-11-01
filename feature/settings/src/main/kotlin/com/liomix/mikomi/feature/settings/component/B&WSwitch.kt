package com.liomix.mikomi.feature.settings.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.liomix.mikomi.feature.settings.R
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 切换行上报给宿主的图标状态。
 *
 * [draw] 是**绘制时**才执行的 lambda（内部读动画进度与颜色），
 * 这样揭示层能把同一枚图标重画到旧画面之上，且始终与本体逐帧一致。
 */
internal class NightModeIconState(
    /** 图标在根窗口中的矩形：中心即揭示原点，最小边即图标直径。 */
    val boundsInRoot: Rect,
    /** 把图标画到任意 [DrawScope]：参数为画布、图标中心、图标边长。 */
    val draw: (DrawScope, Offset, Float) -> Unit,
)

/**
 * 日 / 夜切换控制器：由 [DarkModeRevealHost] 通过 CompositionLocal 提供，
 * [BAndWSwitch] 直接取用，调用方无需把回调层层透传。
 */
@Stable
class DarkModeToggleController internal constructor(
    /** 触发一次带圆形揭示动画的模式切换。 */
    internal val toggle: () -> Unit,
    /** 上报图标位置 / 尺寸 / 绘制指令，供揭示层复用。 */
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
    var iconDraw by remember { mutableStateOf<(DrawScope, Offset, Float) -> Unit>({ _, _, _ -> }) }

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
                iconDraw = icon.draw
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
                                iconDraw(this, origin, iconUnit)
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
 * 布局取自 Telegram `TextCell`：行高 50dp、图标 28dp 且左边距 21dp、文字从 58dp 起、整行蓝色。
 * 图标直接播放 Telegram 的 `sun_outline.json`（36 帧 @60fps，600ms）：
 * 月牙 + 两颗星 → 形变成圆盘 → 8 条射线淡入并转正，进度线性推进即可，
 * 帧间缓动由 Lottie 关键帧自带的贝塞尔控制点负责，与 Telegram 逐帧一致。
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

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.sun_outline))
    val renderer = remember(composition) { composition?.let(::LottieIconRenderer) }

    LaunchedEffect(composition) {
        Log.w(
            REVEAL_LOG_TAG,
            "lottie composition: loaded=${composition != null} " +
                "duration=${composition?.duration} frames=${composition?.startFrame}..${composition?.endFrame}",
        )
    }

    // Lottie 首帧是月牙（夜），末帧是太阳（日），与 isDarkMode 相反。
    // 时长直接取合成时长（600ms），保证与 Telegram 的播放速度一致。
    val durationMillis = remember(composition) {
        composition?.duration?.toInt()?.takeIf { it > 0 } ?: DEFAULT_ICON_MORPH_MILLIS
    }
    val frame by animateFloatAsState(
        targetValue = if (isDarkMode) 0f else 1f,
        animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
        label = "nightModeLottieProgress",
    )
    // 揭示层在布局回调里捕获 lambda，颜色必须通过 State 读取才不会滞留在旧值。
    val tintState = rememberUpdatedState(MaterialTheme.colorScheme.primary)

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
        LottieAnimation(
            composition = composition,
            progress = { frame },
            modifier = Modifier
                .size(IconSize)
                // json 里的描边是纯白，必须自己上色：离屏后盖一层 tint（SrcIn 只保留 alpha）。
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(color = tint, blendMode = BlendMode.SrcIn)
                }
                .onGloballyPositioned { coordinates ->
                    controller?.updateIcon(
                        NightModeIconState(
                            boundsInRoot = coordinates.boundsInRoot(),
                            draw = { scope, center, unit ->
                                // 绘制时读 State，进度与颜色永远是最新的那一帧。
                                renderer?.drawIcon(
                                    scope = scope,
                                    center = center,
                                    unit = unit,
                                    progress = frame,
                                    tint = tintState.value,
                                )
                            },
                        ),
                    )
                },
            contentScale = ContentScale.Fit,
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
 * 把 Lottie 合成的某一帧画到 [scope] 上。
 *
 * 描边色在 json 里是纯白，因此统一用一层 `ColorFilter.tint` 染色：
 * 这样 Lottie 内部走硬件渲染也能正确上色（`LottieDrawable` 自带的 colorFilter 只在软件渲染下生效）。
 */
@Stable
private class LottieIconRenderer(composition: LottieComposition) {

    // 必须走软件渲染：默认的硬件模式会把内容录进 RenderNode，
    // 手动 draw() 时进度变化不会让 RenderNode 重新录制，画出来就是空白。
    private val drawable = LottieDrawable().apply {
        setRenderMode(RenderMode.SOFTWARE)
        setComposition(composition)
    }

    /** 整层上色用的画笔：SrcIn 只保留离屏图层的 alpha，颜色换成主题色。 */
    private val tintRectPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    private var logged = false

    fun drawIcon(
        scope: DrawScope,
        center: Offset,
        unit: Float,
        progress: Float,
        tint: Color,
    ) {
        if (unit <= 0f) return
        val side = unit.roundToInt()
        if (side <= 0) return

        val left = center.x - unit / 2f
        val top = center.y - unit / 2f

        drawable.setBounds(0, 0, side, side)
        drawable.progress = progress.coerceIn(0f, 1f)

        if (!logged) {
            logged = true
            Log.w(REVEAL_LOG_TAG, "lottie draw: side=$side progress=$progress")
        }

        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(left, top)
            // 离屏一层，画完 Lottie 后整层按 tint 的 alpha 上色（json 描边是纯白）。
            val layer = native.saveLayer(0f, 0f, unit, unit, null)
            drawable.draw(native)
            tintRectPaint.color = tint.toArgb()
            native.drawRect(0f, 0f, unit, unit, tintRectPaint)
            native.restoreToCount(layer)
            native.restore()
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
 * 合成加载完成前的兜底形变时长。
 *
 * `sun_outline.json` 是 36 帧 @60fps = 600ms，这里只是 composition 还没解析好时的占位值。
 */
private const val DEFAULT_ICON_MORPH_MILLIS = 600

/** 图标颜色的过渡时长（Telegram 揭示结束后 350ms 混色）。 */
private const val ICON_TINT_MILLIS = 350

/** 揭示时长。 */
private const val REVEAL_DURATION_MILLIS = 400

/** 圆半径小于该值（px）时认为「圆还没出现」，直接跳过裁剪。 */
private const val MIN_VISIBLE_RADIUS = 0.5f

/** 揭示缓动：easeInOutQuad，与 Telegram 的 `Easings.easeInOutQuad` 一致。 */
private val RevealEasing = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

/** 等待主题切换的超时（超出后继续，避免停在遮罩上）。 */
private const val THEME_CHANGE_TIMEOUT_MILLIS = 250L
