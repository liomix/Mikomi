package com.liomix.mikomi.feature.settings.component

import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.liomix.mikomi.core.designsystem.theme.MikomiMedia
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.ThemePalette
import com.liomix.mikomi.feature.settings.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 主题配色色卡（复刻 Telegram `ThemeSmallPreviewView`，去掉聊天气泡）。
 *
 * 三层结构，从下往上：
 * 1. **底色**：四色色斑（[ThemePalette.swatchGradient]）内缩 [SWATCH_INSET]、圆角 [SWATCH_CORNER]，
 *    最后压一圈半透明白描边交代边界。
 * 2. **选中描边**：圆角比色块大 2dp、线宽 2dp，250ms 里透明度 0→1、同时从离边 6dp 收到 1dp ——
 *    观感是「描边从外面收拢并浮现」，不是简单淡入。
 * 3. **表情**：底部居中的 Lottie 动画，选中期间常驻循环，点击时额外放大 2× 再缩回。
 *
 * 底色这层的 `clip` 只包住自己：表情要能放大 2× 且不被裁，所以它必须是外层 Box 的兄弟节点，
 * 选中描边也用 `drawBehind` 画在最外层、同样不参与裁剪。
 *
 * 这个组件会被 LazyRow 横滑，一屏能看到四五张，所以三处开销刻意避开：
 * - 选中动画的进度**不在组合阶段解包**，只在 `drawBehind` 里读 `State.value`：
 *   动画的每一帧只让这层重绘，不会重组整张卡（连带 Lottie 子树）；
 * - 四色色斑的 `Brush` 用 `drawWithCache` 缓存，尺寸与配色不变时不重建；
 * - 没有表情素材的配色根本不进入 Lottie 组合；循环只发生在**当前选中**的那一张上，
 *   未选中的卡停在首帧当静态图标 —— 配色是单选的，所以整个列表同时最多一个帧循环。
 *
 * @param palette 配色，决定色斑颜色与表情动画
 * @param selected 是否为当前配色
 * @param onClick 点击回调（表情动画由组件自己播，调用方只管切配色）
 */
@Composable
fun ThemeSwatch(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只留 State、不解包：选中进度由绘制阶段读取，250ms 动画期间零重组。
    // 曲线与时长和 Telegram 的 strokeAlphaAnimator 一致。
    val selection = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = SELECTION_MILLIS),
        label = "paletteSelection",
    )
    // 点击信号：每次 +1 就让表情重新弹一次（放大 → 停留 → 回缩）。
    // 注意它**不控制播放**：播放由「是否选中」决定，见 SwatchEmoji。
    var emojiPopToken by remember { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }
    val emojiRes = palette.emojiRes

    Box(
        modifier = modifier
            .size(width = SWATCH_WIDTH, height = SWATCH_HEIGHT)
            .drawBehind {
                val progress = selection.value
                if (progress <= 0f) return@drawBehind
                val stroke = SELECTED_STROKE.toPx()
                // 未选中时描边被推到离边 4dp（空白处），选中后收到 1dp；与透明度同步推进。
                val space = stroke / 2f + SELECTED_GAP.toPx() * (1f - progress)
                drawRoundRect(
                    color = palette.swatch.copy(alpha = progress),
                    topLeft = Offset(space, space),
                    size = Size(size.width - space * 2, size.height - space * 2),
                    cornerRadius = CornerRadius(SELECTED_CORNER.toPx()),
                    style = Stroke(width = stroke),
                )
            }
            // 关掉涟漪：色块本身是一整块色斑，涟漪铺满整块反而脏；点击反馈交给表情放大。
            // 用 selectable 而不是 clickable：配色是单选，读屏要能播报「已选中」。
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = {
                    emojiPopToken++
                    onClick()
                },
            )
            .semantics { contentDescription = palette.label },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(SWATCH_INSET)
                .clip(RoundedCornerShape(SWATCH_CORNER))
                // drawWithCache：这一层的画法只取决于尺寸和配色，两者不变就不重新算 Brush。
                .drawWithCache {
                    val colors = palette.swatchGradient
                    val base = colors.firstOrNull() ?: Color.Transparent
                    val spots = buildSpots(colors)
                    onDrawBehind {
                        // 主色铺满：色斑之间不能露出卡片底色，也保证「以主色为主」。
                        drawRect(color = base)
                        for (index in spots.indices) {
                            val spot = spots[index]
                            drawCircle(
                                brush = spot.brush,
                                radius = spot.radius,
                                center = spot.center,
                            )
                        }
                    }
                }
                .border(
                    width = SWATCH_INNER_BORDER,
                    color = SWATCH_INNER_BORDER_COLOR,
                    shape = RoundedCornerShape(SWATCH_CORNER),
                ),
        )
        if (emojiRes != null) {
            SwatchEmoji(
                rawRes = emojiRes,
                active = selected,
                popToken = emojiPopToken,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = EMOJI_BOTTOM_PADDING),
            )
        }
    }
}

/**
 * 把四色色斑的画笔预先算好，交给 `drawWithCache` 存着。
 *
 * Telegram 的算法（`image.cpp` / `generateGradient`）是「四个色心的距离四次方加权平均」，
 * 换算成本质相同的观感：主色铺底，其余三色各自从一角用径向渐变渗出来。
 * 不做逐像素加权是刻意的 —— 色块只有几十 dp，GPU 画三个径向渐变的结果肉眼无差，
 * 却省掉了位图生成与缓存。
 *
 * @param colors 顺序同 Telegram `colors[0..3]`，第 1 个是主色
 */
private fun CacheDrawScope.buildSpots(colors: List<Color>): Array<GradientSpot> {
    val radius = size.minDimension * SPOT_RADIUS_RATIO
    // 第 0 个颜色已经是底色，从它开始叠等于白画；只叠其余三个。
    return Array(maxOf(0, colors.size - 1)) { index ->
        val color = colors[index + 1]
        val spot = GradientSpots[index % GradientSpots.size]
        val center = Offset(spot.x * size.width, spot.y * size.height)
        GradientSpot(
            brush = Brush.radialGradient(
                0f to color.copy(alpha = SPOT_ALPHA_CENTER),
                SPOT_STOP_MID to color.copy(alpha = SPOT_ALPHA_MID),
                1f to color.copy(alpha = 0f),
                center = center,
                radius = radius,
            ),
            center = center,
            radius = radius,
        )
    }
}

/** 一个色斑：画笔、圆心、半径。预先算好，绘制时直接画。 */
private class GradientSpot(
    val brush: Brush,
    val center: Offset,
    val radius: Float,
)

/**
 * 色卡上的表情动画。
 *
 * **播放与缩放是两条独立的时间线**，这也是 Telegram 的做法（`ThemeSmallPreviewView`
 * 里 `RLottieDrawable.start()` 自驱帧循环，2.5s 后回缩的 runnable 只管 `scaleX/scaleY`）：
 *
 * - 播放：`active` 期间无限循环。素材首末帧闭合（实测 `diamond_blue` 首末帧 0% 像素差、
 *   `duck_orange` 0.5%），所以「播到 1 → 跳回 0」在接缝处看不出来。
 * - 缩放：每次 [popToken] 变化走一遍「放大 2× → 停留 → 回缩 1×」，中途再点会打断回缩、
 *   从当前倍数重新弹，等于自动续期。
 *
 * 之所以不像 Telegram 那样「点过的都继续跑」：配色是单选，`active` 挂在 `selected` 上，
 * 整个列表同时最多一个帧循环；滑出 LazyRow 可见范围时组合被回收，协程自动取消，动画自己停。
 *
 * @param active 是否选中（选中期间循环播放，否则停在首帧当静态图标）
 * @param popToken 每次点击 +1，触发一次放大回弹；0 表示还没点过
 */
@Composable
private fun SwatchEmoji(
    @RawRes rawRes: Int,
    active: Boolean,
    popToken: Int,
    modifier: Modifier = Modifier,
) {
    // LottieCompositionSpec 是普通 class（不是 data class），每次新建实例都会让
    // rememberLottieComposition 的 key 变化、重新走一遍加载 —— 所以按资源 id 缓存住。
    val spec = remember(rawRes) { LottieCompositionSpec.RawRes(rawRes) }
    val composition by rememberLottieComposition(spec)
    // 进度手写而不用 animateLottieCompositionAsState：需要「每轮从 0 开始、速率严格线性」，
    // 而 json 关键帧自带缓动，再叠一层缓动就跑偏了。
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val durationMillis = composition?.duration?.toInt()?.takeIf { it > 0 }

    // 时间线一：循环播放。没有任何「播完」的终点，协程随组合销毁而取消。
    LaunchedEffect(active, durationMillis) {
        if (!active || durationMillis == null) {
            // 取消选中时回到首帧，而不是停在循环被掐断的那一帧（Telegram 同样
            // stop() 之后 setCurrentFrame(0)）——未选中的卡都长一样。
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMillis, easing = MikomiMotion.Curve.Linear),
            )
        }
    }

    // 时间线二：缩放。与播放进度完全解耦 —— 回缩结束后动画照旧在跑，和 TG 一致。
    LaunchedEffect(popToken) {
        if (popToken == 0) return@LaunchedEffect
        scale.animateTo(
            targetValue = EMOJI_POP_SCALE,
            animationSpec = tween(durationMillis = EMOJI_POP_MILLIS, easing = OvershootEasing),
        )
        delay(EMOJI_HOLD_MILLIS)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = EMOJI_SETTLE_MILLIS, easing = SettleEasing),
        )
    }

    LottieAnimation(
        composition = composition,
        progress = { progress.value },
        modifier = modifier
            .size(EMOJI_SIZE)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
    )
}

/**
 * 各配色色卡上的表情动画。
 *
 * 蓝色是钻石、橙色是小黄鸭；粉色、绿色与紫色暂时没有 json，
 * 返回 `null` ＝ 色卡上不显示表情（不是漏了），也就不会创建任何 Lottie 节点。
 * when 写穷举而不是 `else`：以后加配色时编译器会在这里提醒补动画。
 *
 * 两份素材都是 512² 的画布（钻石 @60fps 2.0s、鸭子 @60fps 3.0s），组件统一按 [EMOJI_SIZE]
 * 的方框 + `Fit` 缩放（LottieAnimation 默认 `ContentScale.Fit`），不会变形或被裁。
 */
private val ThemePalette.emojiRes: Int?
    get() = when (this) {
        ThemePalette.Default -> R.raw.diamond_blue
        ThemePalette.Orange -> R.raw.duck_orange
        ThemePalette.Sakura, ThemePalette.Mint, ThemePalette.Purple -> null
    }

/**
 * Telegram 的四个色心（`image.cpp` 里 `positions` 的第 0 / 2 / 4 / 6 个，y 取反）。
 *
 * 归一化坐标，乘色卡宽高得到实际位置；四个点大致落在四角，所以色斑不会挤在中间。
 */
private val GradientSpots = listOf(
    Offset(0.80f, 0.90f),
    Offset(0.35f, 0.75f),
    Offset(0.20f, 0.10f),
    Offset(0.65f, 0.25f),
)

/** 色卡尺寸：宽高比 1 : 1.35，与 Telegram 的 77 × 104 同比例（这里放大到卡片里合适的尺寸）。 */
private val SWATCH_WIDTH = 92.dp
private val SWATCH_HEIGHT = 124.dp

/** 色斑相对色卡的内缩：留出的这一圈同时是选中描边的跑道（Telegram `INNER_RECT_SPACE`）。 */
private val SWATCH_INSET = 4.dp

/** 色斑圆角（Telegram `INNER_RADIUS` 6dp）。 */
private val SWATCH_CORNER = 6.dp

/** 选中描边圆角，比色卡大 2dp，描边落在色斑外侧（Telegram `STROKE_RADIUS` 8dp）。 */
private val SELECTED_CORNER = 8.dp

/** 选中描边线宽（Telegram `strokePaint` 2dp）。 */
private val SELECTED_STROKE = 2.dp

/** 未选中时描边额外退让的距离；选中后归零，形成「从外面收拢」的观感。 */
private val SELECTED_GAP = 4.dp

/** 选中描边的完整过渡时长（Telegram 250ms）。 */
private const val SELECTION_MILLIS = 250

/**
 * 色斑内描边：12.5% 的白（Telegram `0x20E3E3E3`）。
 *
 * 只用来交代边界 —— 色斑压到浅底上时边缘容易糊掉；正常观感下不该被注意到。
 */
private val SWATCH_INNER_BORDER = 1.dp
private val SWATCH_INNER_BORDER_COLOR = MikomiMedia.outline

/** 径向色斑的半径（相对色卡短边）：太小色心会缩成一个点，太大又整片糊成单色。 */
private const val SPOT_RADIUS_RATIO = 0.72f

/** 色斑中心的不透明度：接近 1 才看得出这一角是另一个颜色。 */
private const val SPOT_ALPHA_CENTER = 0.95f

/** 衰减中点的位置与其不透明度，控制「渗出来」的柔和程度。 */
private const val SPOT_STOP_MID = 0.6f
private const val SPOT_ALPHA_MID = 0.45f

/** 表情尺寸与底部留白（Telegram 28dp + 12dp，按色卡略放大）。 */
private val EMOJI_SIZE = 30.dp
private val EMOJI_BOTTOM_PADDING = 12.dp

/** 点击后表情放大的倍数（Telegram 2×）。 */
private const val EMOJI_POP_SCALE = 2f

/** 放大时长（Telegram 300ms overshoot）。 */
private const val EMOJI_POP_MILLIS = 300

/** 放大后到开始回缩之间的停留：Telegram 在点击后 2.5s 回缩，减去放大花掉的 300ms。 */
private const val EMOJI_HOLD_MILLIS = 2_200L

/** 回缩时长（Telegram 150ms）。 */
private const val EMOJI_SETTLE_MILLIS = 150

/**
 * 放大的插值：等价于 Android `OvershootInterpolator`（tension 2）。
 *
 * 终点会冲过 2× 一点再落回，才有「弹一下」的手感；用默认的 ease 曲线只会看起来变大。
 */
private val OvershootEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** 回缩的插值：`CubicBezierInterpolator.DEFAULT`（0.4, 0, 0.2, 1），Telegram 原值。 */
private val SettleEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
