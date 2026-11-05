package com.liomix.mikomi.core.ui.component.base

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import kotlin.math.roundToInt

/**
 * 轨道尺寸（含那圈透明边）。
 *
 * CSS 里 `.slider` 是 `60 × 30` 外加 `border: 4px solid transparent`，按 border-box 铺满后
 * 看得见的轨道是 `68 × 38`。这里**等比缩到 37 × 20** —— 37 × 20 是 Telegram 设置项里
 * `Switch` 的布局框（`LayoutHelper.createFrame(37, 20, …)`，轨道 31 × 14、拨片 20dp 圆）。
 * 只压高度不改宽度的话，拨片会变成一根 30 × 12 的细条，所以宽高一起缩，保持 CSS 的 2:1。
 * 三个数一一对应：可见尺寸 = 内容尺寸 + 2 × [SwitchInset]。
 */
private val TrackWidth = 37.dp
private val TrackHeight = 20.dp

/** 那圈「透明边框」：轨道内壁到拨片之间的留白（CSS 的 4dp 按比例缩到 2dp）。 */
private val SwitchInset = 2.dp

/** 拨片可活动的窗口（33 × 16），也就是拨片自身的尺寸。 */
private val ThumbWidth = TrackWidth - SwitchInset * 2
private val ThumbHeight = TrackHeight - SwitchInset * 2

/** 拨片的行程：正好半个窗口，关时贴左、开时贴右。 */
private val ThumbTravel = ThumbWidth / 2

/**
 * 独立开关的点击槽高度。
 *
 * 轨道只有 20dp，远低于 48dp 的最小触控目标；但它又不能直接撑高 —— 那样轨道也要跟着变形。
 * 所以点击槽做到 48dp、轨道 20dp 居中：手指好点，画面还是缩过的 CSS 比例。
 * 塞在开关行里（`onCheckedChange = null`）时不用这个尺寸，整行本身就是点击目标。
 */
private val TouchTargetHeight = MikomiSizes.TouchTarget

/** 圆角取满 = CSS 的 `border-radius: 20px`（大于半高时会被浏览器夹到胶囊形）。 */
private val SwitchShape: Shape = RoundedCornerShape(percent = 50)

/** CSS 的 `transition: .3s`，缓动也是 `ease`（不是 Material 的 FastOutSlowIn）。 */
private const val SwitchAnimMillis = MikomiMotion.Duration.Long
private val SwitchEasing: Easing = MikomiMotion.Curve.Press

/** 拨片的投影：CSS `0 0 10px 3px rgb(0 0 0 / 25%)`，随尺寸一起减半。 */
private val ThumbShadowElevation = 2.dp

/** 轨道内阴影的模糊半径：CSS `inset 0 0 10px`，随尺寸一起减半（20dp 高的轨道上 10dp 会糊满）。 */
private val InnerShadowBlur = 5.dp

/**
 * 轨道内阴影的强度。
 *
 * CSS 写的是 25% 黑，但那是模糊后的**峰值**：10px 的模糊会把边缘那圈的 alpha 摊薄到一半左右，
 * 落在屏幕上大约 12%。这里直接按摊薄后的值给，横竖两道渐变在角上叠一次刚好回到 25% 附近。
 */
private val InnerShadowColor = Color.Black.copy(alpha = 0.13f)

/** 禁用态的整块不透明度（Material 的 38% 在这里偏虚，拨片本来就浅，取 45% 才看得出「灰下去」。 */
private const val DisabledAlpha = 0.45f

/**
 * 胶囊开关。
 *
 * 造型来自一段 CSS（`.slider` + `::before`）：**拨片不是圆形拇指，而是一整块和轨道等宽的胶囊**，
 * 靠轨道 `overflow: hidden` 只露出其中一半。因此关的时候拨片是「左边齐、右边圆」，
 * 开的时候翻过来 —— 换成一个圆形拇指就没有这个形状特征了，那是 Material / Telegram 的开关。
 *
 * 三个位置（CSS 的 `translateX`，按比例缩到 ±[ThumbTravel]）：
 * - 关 → 贴左，露出左半；
 * - 按住 → 居中，整块盖住轨道（CSS 的 `:active`，也是这个组件唯一的按压反馈；
 *   没有水波纹，因为拨片自己会「张开」）；
 * - 开 → 贴右，露出右半。
 *
 * 颜色全部走主题，不写死 CSS 里的 `#2196F3` / `lightgray` / `#fff`：
 * - 轨道关 = [MaterialTheme.colorScheme.surfaceContainerHighest]（≈ CSS 的 lightgray，
 *   日夜两套主题下都能和页面底色分出边界）；
 * - 轨道开 = `primary`，跟着用户的配色预设走；
 * - 拨片 = 近白。日间取 `surfaceContainerLowest`（纯白，与 CSS 的 `#fff` 一致），
 *   **夜间不能也用它** —— 夜间的 `surfaceContainerLowest` 是 #0D0D0D，拨片会和轨道糊成一块，
 *   所以夜间改用 `onSurface`（近白）。
 *
 * 尺寸按 CSS 的比例缩到 37 × 20（Telegram 开关的布局框）。要改大小请改文件顶部的常量，
 * 别在调用点套缩放 —— 拨片靠「等宽 + 平移 + 裁切」成立，缩放会把圆角和行程的比例一起带歪。
 *
 * 两种用法：
 * - **独立开关**：传 `onCheckedChange`。点击槽会自动补到 48dp 高（轨道仍 20dp 居中），
 *   满足最小触控目标；这时别把它放进一个自己也 `toggleable` 的行里，否则两个手势会打架。
 * - **开关行里的指示器**：`onCheckedChange = null`，手势交给整行。此时组件**一个手势都不挂**，
 *   点在这一块上会直接落到行上 —— 这一点很关键：M3 的只读开关会挂一个 `enabled = false` 的
 *   `toggleable`，在部分版本上它仍会吃掉触摸，表现就是「点开关没反应，只能点文字」。
 *   想把按压反馈也带过去，就把行的 `interactionSource` 传进来（见 [interactionSource]）。
 *
 * @param checked 当前状态；组件不自己存开关态
 * @param onCheckedChange 传了就自己接点击（独立开关）；传 null 表示手势由外层行负责，
 *   组件退化成纯视觉、不挂任何手势
 * @param enabled 置灰；自己接点击时（`onCheckedChange != null`）同时吞掉点击并被无障碍报成「已禁用」
 * @param interactionSource 手势不在自己身上时（开关行），把行的按压源传进来，
 *   拨片才会跟着「张开」；不传则自建一个
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    // 外部给了就用外部的：开关行里手势握在行上，按压状态得从行那边传过来，
    // 否则按在开关这一块上拨片不会「张开」。
    val ownSource = remember { MutableInteractionSource() }
    val pressSource = interactionSource ?: ownSource
    val pressed by pressSource.collectIsPressedAsState()
    val colors = rememberSwitchColors()

    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.trackOn else colors.trackOff,
        animationSpec = tween(SwitchAnimMillis, easing = SwitchEasing),
        label = "switchTrackColor",
    )

    // 0 = 贴左（关），0.5 = 居中（按住），1 = 贴右（开）。
    // 用「位置」而不是「开关态 + 按压态」两个动画：按住时从任何一端滑到中间都是同一条插值，
    // 松开后再从中间回到目标端，CSS 的两个 transition 叠在一起就是这个效果。
    val anchor = when {
        pressed -> 0.5f
        checked -> 1f
        else -> 0f
    }
    val progress by animateFloatAsState(
        targetValue = anchor,
        animationSpec = tween(SwitchAnimMillis, easing = SwitchEasing),
        label = "switchThumbProgress",
    )
    val travelPx = with(LocalDensity.current) { ThumbTravel.toPx() }
    // -行程 .. +行程：progress 0.5 时正好停在窗口正中（拨片盖满整条轨道）。
    val thumbOffsetPx = ((progress - 0.5f) * 2f * travelPx)

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else DisabledAlpha)
            .then(
                if (onCheckedChange == null) {
                    // 纯视觉：**一个手势都不挂**，点在这一块上会直接穿透到外层行。
                    Modifier.size(TrackWidth, TrackHeight)
                } else {
                    // 独立开关：点击槽补到 48dp 高（轨道 38dp 居中），够最小触控目标。
                    Modifier
                        .size(width = TrackWidth, height = TouchTargetHeight)
                        .toggleable(
                            value = checked,
                            onValueChange = { onCheckedChange(it) },
                            enabled = enabled,
                            role = Role.Switch,
                            interactionSource = pressSource,
                            // 不要水波纹：按压反馈由拨片的「张开」给出，再叠一层涟漪会互相打架。
                            indication = null,
                        )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = TrackWidth, height = TrackHeight)
                .clip(SwitchShape)
                .background(trackColor)
                // 内阴影必须夹在「底色」和「拨片」之间：`drawBehind` 画的正是子项之前那一刻，
                // 所以阴影盖得住底色、又压不到拨片上（CSS 的 inset shadow 也是这样被 ::before 盖住的）。
                .drawBehind { drawTrackInnerShadow() },
        ) {
            // 活动窗口：拨片被裁在这里面，多出来的那半块看不见。
            Box(
                modifier = Modifier
                    .padding(SwitchInset)
                    .clip(SwitchShape),
            ) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                        .size(width = ThumbWidth, height = ThumbHeight)
                        // 投影是整块拨片的：平移出去的那半边被窗口裁掉，所以只在露出来的那一侧看到影子 ——
                        // 这正是 CSS 里 `overflow: hidden` 裁掉 `box-shadow` 的结果，也是这个造型的「厚度」来源。
                        .shadow(
                            elevation = ThumbShadowElevation,
                            shape = SwitchShape,
                            clip = false,
                            ambientColor = Color.Black,
                            spotColor = Color.Black,
                        )
                        .background(colors.thumb, SwitchShape),
                )
            }
        }
    }
}

/**
 * 轨道内阴影：CSS `box-shadow: inset 0 0 10px rgba(0,0,0,.25)`。
 *
 * 不真的做一次模糊：Compose 里要对一个形状做「内阴影」要么离屏渲染一遍再反转 alpha，
 * 要么手写带 `BlurMaskFilter` 的 nativeCanvas（低版本会被硬件加速吞掉）。
 * 这里用两条交叉的线性渐变近似 —— 上下一道、左右一道，各自从边缘往内 [InnerShadowBlur] 化到透明。
 * 拨片是圆角胶囊、整条轨道才 37 × 20，两者在屏幕上的差别看不出来，却省掉一个离屏图层。
 */
private fun DrawScope.drawTrackInnerShadow() {
    val fadeY = (InnerShadowBlur.toPx() / size.height).coerceIn(0f, 0.5f)
    val fadeX = (InnerShadowBlur.toPx() / size.width).coerceIn(0f, 0.5f)
    drawRect(
        brush = Brush.verticalGradient(
            0f to InnerShadowColor,
            fadeY to Color.Transparent,
            1f - fadeY to Color.Transparent,
            1f to InnerShadowColor,
        ),
    )
    drawRect(
        brush = Brush.horizontalGradient(
            0f to InnerShadowColor,
            fadeX to Color.Transparent,
            1f - fadeX to Color.Transparent,
            1f to InnerShadowColor,
        ),
    )
}

/** 开关的三处用色。 */
private class SwitchColors(
    val trackOff: Color,
    val trackOn: Color,
    val thumb: Color,
)

/**
 * 从主题推颜色。
 *
 * 判断日夜读的是 `background` 的亮度而不是 `isSystemInDarkTheme()`：App 的夜间模式是用户手动切的，
 * 系统可能仍是日间，只有色板才反映「现在到底画在哪套底色上」。
 */
@Composable
private fun rememberSwitchColors(): SwitchColors {
    val scheme = MaterialTheme.colorScheme
    val night = scheme.background.luminance() < 0.5f
    return remember(scheme, night) {
        SwitchColors(
            trackOff = scheme.surfaceContainerHighest,
            trackOn = scheme.primary,
            thumb = if (night) scheme.onSurface else scheme.surfaceContainerLowest,
        )
    }
}
