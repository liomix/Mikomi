package com.liomix.mikomi.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.liomix.mikomi.core.designsystem.spec.MikomiNavBarSpec
import com.liomix.mikomi.core.designsystem.spec.MikomiRailSpec
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiPillShape
import com.liomix.mikomi.core.designsystem.theme.MikomiTextStyles
import com.liomix.mikomi.core.ui.component.base.MikomiIcon
import kotlin.math.roundToInt

/**
 * 本 App 的悬浮式底部导航栏 —— **不依赖 `core:ui` 的通用 Tab 组件**。
 *
 * 规格对照 Telegram Android（1080×2400 @3x 实测，已换算为 dp）：
 *
 * | 项 | 值 | 来源（px） |
 * |---|---|---|
 * | 栏体高度 | 56dp | 2208..2376 = 168px |
 * | 栏体左右外边距 | 16dp | 48px |
 * | 栏体圆角 | 28dp | 拟合半径 84px |
 * | 栏体内边距 | 4dp | 12px |
 * | 单项高度 | 48dp | 2220..2364 = 144px |
 * | 单项圆角 | 24dp（= 高度一半，胶囊） | 逐行拟合 |
 * | 单项左右内边距 | 24dp | 标签两侧各 70.5px |
 * | 图标 | 24dp | 72px |
 * | 标签 | 12sp | 单字步进 35.3px |
 * | 图标与标签间距 | 3dp | 2304→2315 = 11px |
 *
 * 两个关键几何结论：
 * 1. **单项宽度由文字长度决定，不是等分**（实测 213 / 249 / 213 / 285px），整组居中；
 * 2. 选中胶囊与单项**完全重合**（内边距在 `selectable` 内侧），所以看到多大的胶囊就能点多大。
 *
 * 与 Telegram 的有意差异：
 * - 颜色跟本项目主题走（`surfaceContainer` / `primary` / `onSurfaceVariant`），不搬它的蓝黑配色；
 * - 不投影。栏体会浮在巡礼页的浅色地图上，投影在纯色背景里扩散成一圈灰边；
 *   浮起感由栏体着色与页面留白给出。
 */
@Composable
fun MikomiNavigationBar(
    items: List<MikomiNavItem>,
    selectedKey: Any?,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    // 每项的实测矩形，胶囊只依赖它 —— 不按等分宽度推算，文字长度不一也能贴合。
    val bounds = remember { mutableStateListOf<Rect>() }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MikomiNavBarSpec.CornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(modifier = Modifier.padding(MikomiNavBarSpec.InnerPadding)) {
            NavBarIndicator(
                bounds = bounds,
                selectedIndex = selectedIndex,
                color = MaterialTheme.colorScheme.primary.copy(alpha = MikomiNavBarSpec.SelectedContainerAlpha),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    NavItem(
                        item = item,
                        index = index,
                        selected = index == selectedIndex,
                        bounds = bounds,
                        onClick = { onSelect(item.key) },
                    )
                }
            }
        }
    }
}

/**
 * 侧边导航栏（rail）—— 平板形态，视觉是底栏那条胶囊**竖过来**。
 *
 * 与 [MikomiNavigationBar] 共用三样东西，这是刻意的：条目内容（图标上、标签下）、
 * 选中胶囊指示器（只吃 `Rect`，与方向无关）、选中色的过渡时长。所以两处形态切换时，
 * 只有「排列方向」和「条目宽度统一」两点不同，不会分裂成两套手感。
 *
 * 条目宽度统一 64dp：横向时宽度跟文案走（见 [MikomiNavBarSpec] 的说明）是为了胶囊
 * 刚好包住文字；竖过来之后若还让每个条目自己定宽，三条胶囊会左右参差，
 * 看着像没对齐。代价是短标签（「首页」）两侧留白多一些 —— 竖栏本来就该是规整的。
 *
 * @param modifier 由壳给出宽度（[MikomiRailSpec.Width] 占位）与垂直居中。
 */
@Composable
fun MikomiNavigationRail(
    items: List<MikomiNavItem>,
    selectedKey: Any?,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val bounds = remember { mutableStateListOf<Rect>() }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MikomiRailSpec.CornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(modifier = Modifier.padding(MikomiRailSpec.InnerPadding)) {
            NavBarIndicator(
                bounds = bounds,
                selectedIndex = selectedIndex,
                color = MaterialTheme.colorScheme.primary.copy(
                    alpha = MikomiNavBarSpec.SelectedContainerAlpha,
                ),
            )
            Column(modifier = Modifier.selectableGroup()) {
                items.forEachIndexed { index, item ->
                    NavItem(
                        item = item,
                        index = index,
                        selected = index == selectedIndex,
                        bounds = bounds,
                        width = MikomiRailSpec.ItemWidth,
                        horizontalPadding = MikomiRailSpec.ItemHorizontalPadding,
                        onClick = { onSelect(item.key) },
                    )
                }
            }
        }
    }
}

/** 导航栏条目模型。[key] 为任意稳定标识（通常是路由对象），组件本身不感知导航框架。 */
data class MikomiNavItem(
    val key: Any,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val label: String,
)

/** 胶囊滑动时长。Telegram 的实际曲线没测，这里用通用减速曲线。 */
private const val IndicatorMillis = MikomiMotion.Duration.Short

/**
 * 单个条目：图标在上、文字在下，整块可点。**横竖两种形态共用这一份**。
 *
 * **内边距在 `selectable` 内侧**：触控范围 == 胶囊矩形 == 实测矩形，不会出现
 * 「看到的胶囊比能点的范围小」。也因此 `onGloballyPositioned` 挂在修饰链最外层。
 *
 * @param width 条目固定宽度；`null` 表示由内容（文案长度）决定，这是横栏的规则。
 *   竖栏必须传固定值，否则三条胶囊左右参差（见 [MikomiNavigationRail]）。
 * @param horizontalPadding 条目内部左右留白。横栏 24dp 是为了胶囊两端留出圆头；
 *   竖栏只有 4dp，因为 64dp 的条目要正好容下四个汉字。
 */
@Composable
private fun NavItem(
    item: MikomiNavItem,
    index: Int,
    selected: Boolean,
    bounds: MutableList<Rect>,
    onClick: () -> Unit,
    width: Dp? = null,
    horizontalPadding: Dp = MikomiNavBarSpec.ItemHorizontalPadding,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = MikomiNavBarSpec.ContentColorMillis),
        label = "navBarItemColor",
    )

    Column(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                val rect = Rect(coordinates.positionInParent(), coordinates.size.toSize())
                // 幂等写入：同一几何下回调可能被重复触发，别把指示器自己叫醒。
                if (bounds.size > index) {
                    if (bounds[index] != rect) bounds[index] = rect
                } else {
                    while (bounds.size <= index) bounds.add(Rect.Zero)
                    bounds[index] = rect
                }
            }
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(MikomiNavBarSpec.ItemHeight)
            .clip(MikomiPillShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MikomiIcon(
            icon = if (selected) item.selectedIcon else item.icon,
            contentDescription = null,
            tint = contentColor,
            size = MikomiNavBarSpec.IconSize,
        )
        Spacer(modifier = Modifier.height(MikomiNavBarSpec.IconLabelGap))
        Text(
            text = item.label,
            color = contentColor,
            style = MikomiTextStyles.IconLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 选中胶囊：与条目叠在同一坐标系，按实测矩形滑动。
 *
 * 相位按 Compose 的规矩摆，动画值不进组合阶段：
 * - 尺寸（宽度逐帧变化）走 `Modifier.layout`，只让这一个叶子重排；
 * - 位移走 `Modifier.graphicsLayer`，纯绘制期；
 * - 组合阶段只有一次 `LaunchedEffect(selectedIndex)`。
 *
 * 不拉伸、不缩放：`scaleX` 会把胶囊两端的圆角压成椭圆，宽度必须真改。
 */
@Composable
private fun NavBarIndicator(
    bounds: List<Rect>,
    selectedIndex: Int,
    color: Color,
) {
    // 1 = 停在目标上；切换时从 0 走到 1。
    val progress = remember { Animatable(1f) }
    var fromRect by remember { mutableStateOf<Rect?>(null) }
    var fromIndex by remember { mutableIntStateOf(selectedIndex) }
    var toIndex by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex == toIndex) return@LaunchedEffect
        // 连点时从「指针现在真实所在的位置」接着走，不跳回上一个条目。
        fromRect = indicatorRect(bounds, fromRect, fromIndex, toIndex, progress.value)
        fromIndex = toIndex
        toIndex = selectedIndex
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = IndicatorMillis, easing = MikomiMotion.Curve.Emphasized),
        )
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                val rect = indicatorRect(bounds, fromRect, fromIndex, toIndex, progress.value)
                if (rect != null) {
                    translationX = rect.left
                    translationY = rect.top
                }
            }
            .layout { measurable, _ ->
                val rect = indicatorRect(bounds, fromRect, fromIndex, toIndex, progress.value)
                val width = rect?.width?.roundToInt() ?: 0
                val height = rect?.height?.roundToInt() ?: 0
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .background(color, MikomiPillShape),
    )
}

/** 当前帧胶囊应处的矩形；目标条目还没上报边界时返回 null（画 0×0，不出错）。 */
private fun indicatorRect(
    bounds: List<Rect>,
    fromRect: Rect?,
    fromIndex: Int,
    toIndex: Int,
    progress: Float,
): Rect? {
    val to = bounds.getOrNull(toIndex) ?: return null
    val from = fromRect ?: to
    return Rect(
        left = lerp(from.left, to.left, progress),
        top = lerp(from.top, to.top, progress),
        right = lerp(from.right, to.right, progress),
        bottom = lerp(from.bottom, to.bottom, progress),
    )
}

/** 栏体高度。数值与理由见 `MikomiNavBarSpec.BodyHeight`。 */
val MikomiNavBarBodyHeight = MikomiNavBarSpec.BodyHeight

/** 栏体距屏幕左右的外边距。 */
val MikomiNavBarHorizontalMargin = MikomiNavBarSpec.HorizontalMargin

/** 栏体距屏幕底部（系统导航栏之上）的外边距。 */
val MikomiNavBarBottomMargin = MikomiNavBarSpec.BottomMargin

/** 栏体从屏幕下沿往上占用的高度（栏体 + 底部外边距，**不含**系统导航栏 inset）。 */
val MikomiNavBarBottomSpace = MikomiNavBarBottomMargin + MikomiNavBarBodyHeight

