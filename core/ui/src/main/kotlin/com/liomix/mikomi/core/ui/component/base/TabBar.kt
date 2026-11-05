package com.liomix.mikomi.core.ui.component.base

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 标签的排布方向：图标与文字横排，或图标在上、文字在下。 */
enum class TabOrientation { Horizontal, Vertical }

/**
 * 单个标签的样式。
 *
 * 由 [TabBar] 统一下发，单项也可通过 [Tab] 的 `style` 覆盖。
 * 高度、内边距与字号一起决定**指示器（同时就是触控区）**的大小——调这里等于同时调了两者。
 */
@Immutable
data class TabItemStyle(
    val orientation: TabOrientation = TabOrientation.Horizontal,
    val height: Dp? = null,
    val iconSize: Dp = 18.dp,
    val iconLabelSpacing: Dp = 5.dp,
    val horizontalPadding: Dp = 8.dp,
    val verticalPadding: Dp = 3.dp,
    val labelStyle: TextStyle = TextStyle(fontSize = 14.5.sp),
    val supportingStyle: TextStyle? = null,
    /** 未选中时的字重；选中态固定 SemiBold，一次切换只重排一次文字。 */
    val labelWeight: FontWeight = FontWeight.Normal,
) {
    companion object {
        /** 个人资料页那种紧凑图标标签：18dp 图标 + 14.5sp 文字。 */
        fun compact(): TabItemStyle = TabItemStyle(
            iconSize = 18.dp,
            iconLabelSpacing = 5.dp,
            horizontalPadding = 8.dp,
            verticalPadding = 3.dp,
            labelStyle = TextStyle(fontSize = 14.5.sp),
            labelWeight = FontWeight.SemiBold,
        )

        /** 底部导航栏：图标在上、文字在下，配合 `weight(1f)` 等分。 */
        @Composable
        fun navigation(): TabItemStyle = TabItemStyle(
            orientation = TabOrientation.Vertical,
            iconSize = 24.dp,
            iconLabelSpacing = 2.dp,
            horizontalPadding = 12.dp,
            verticalPadding = 6.dp,
            labelStyle = MaterialTheme.typography.labelMedium,
            labelWeight = FontWeight.Medium,
        )

        /** 单行文字标签（48dp 高、18dp 横内边距）：排行榜、热播榜这类纯文字切换。 */
        @Composable
        fun text(): TabItemStyle = TabItemStyle(
            height = 48.dp,
            horizontalPadding = 18.dp,
            verticalPadding = 0.dp,
            labelStyle = MaterialTheme.typography.bodyLarge,
            labelWeight = FontWeight.Normal,
        )

        /** 两行标签（标题 + 副标题，56dp 高）：周日历这种等分七格。 */
        @Composable
        fun twoLine(): TabItemStyle = TabItemStyle(
            orientation = TabOrientation.Vertical,
            height = 56.dp,
            iconLabelSpacing = 2.dp,
            horizontalPadding = 6.dp,
            verticalPadding = 0.dp,
            labelStyle = MaterialTheme.typography.titleMedium,
            supportingStyle = MaterialTheme.typography.labelSmall,
            labelWeight = FontWeight.Normal,
        )
    }
}

/** 一组标签的配色。 */
@Immutable
data class TabColors(
    val indicator: Color,
    val selectedContent: Color,
    val unselectedContent: Color,
    val hoveredContent: Color,
)

/**
 * 一组标签的共享状态。
 *
 * - `selectedIndex` 由 [TabBar] 每次组合写入（普通字段，不是 State：选中态的唯一来源是调用方）；
 * - `bounds` 是每个标签在容器内的实测矩形，由 [Tab] 在布局阶段上报。
 *
 * 指示器只依赖这两样，因此**不需要标签等宽**，宽度不一的图标+文字标签也能贴合。
 */
@Stable
class TabBarState internal constructor() {
    var selectedIndex by mutableIntStateOf(0)
        internal set

    internal var onSelect: (Int) -> Unit = {}

    internal val bounds = mutableStateListOf<Rect>()

    /**
     * 上报一个标签的实测矩形。
     *
     * **幂等**：`onGloballyPositioned` 在同一几何下也可能被反复回调（指示器动画会逐帧让它所在容器重新测量，
     * 兄弟节点被重新放置时回调就会再走一遍）。`SnapshotStateList` 的 `set` 不比较相等，
     * 无条件写会让指示器每帧被自己叫醒一次 —— 所以值没变就别写。
     */
    internal fun report(index: Int, rect: Rect) {
        if (bounds.size <= index) {
            while (bounds.size <= index) bounds.add(Rect.Zero)
        }
        if (bounds[index] != rect) bounds[index] = rect
    }
}

private val LocalTabBarState = compositionLocalOf<TabBarState?> { null }

/** 一组标签的视觉参数：形状、尺寸样式、配色。用 CompositionLocal 下发，值变化时读取方会被重组。 */
@Immutable
private data class TabBarVisuals(
    val shape: Shape,
    val style: TabItemStyle,
    val colors: TabColors,
)

private val LocalTabBarVisuals = compositionLocalOf {
    TabBarVisuals(
        shape = RoundedCornerShape(percent = 50),
        style = TabItemStyle(),
        colors = TabColors(Color.Transparent, Color.Unspecified, Color.Unspecified, Color.Unspecified),
    )
}

/**
 * 标签栏：容器 + **在标签之间滑动**的指示器。
 *
 * 与 Material3 `TabRow` 的差别：
 * - 指示器位置来自每个标签的**实测矩形**，不按等分宽度推算，宽度不一的标签也能贴合；
 * - 触控范围与指示器矩形**完全重合**（内边距都在 `selectable` 内侧），不额外补 48dp；
 * - 尺寸与排版整套由 [TabItemStyle] 描述：个人页（图标+文字横排）、底部导航（图标在上）、
 *   排行榜（纯文字）、周日历（两行）共用同一套动效。
 *
 * 动效：指示器**左右两条边各走一条缓动**，切换时先被拉长再收拢（见 [TabIndicator]）；
 * 内容色用 `animateColorAsState` 过渡（只重绘，不重排文字）；字重只切两档。
 *
 * ```
 * TabBar(selectedIndex = selected, onSelect = { selected = it }) {
 *     Tab(index = 0, text = "历史", icon = Icons.Outlined.History)
 *     Tab(index = 1, text = "收藏", icon = Icons.Filled.Bookmark)
 * }
 * ```
 *
 * @param selectedIndex 当前选中项下标；组件不自己存选中态。
 * @param onSelect 点击某项时回调下标。
 * @param content 标签项，通常为若干 [Tab]；用 `Modifier.weight(1f)` 可等分宽度。
 */
@Composable
fun TabBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    indicatorColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    selectedContentColor: Color = MaterialTheme.colorScheme.primary,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    hoveredContentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = RoundedCornerShape(percent = 50),
    elevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    itemSpacing: Dp = 14.dp,
    itemStyle: TabItemStyle = TabItemStyle.compact(),
    content: @Composable RowScope.() -> Unit,
) {
    val state = remember { TabBarState() }
    state.selectedIndex = selectedIndex
    state.onSelect = onSelect
    val visuals = TabBarVisuals(
        shape = shape,
        style = itemStyle,
        colors = TabColors(
            indicator = indicatorColor,
            selectedContent = selectedContentColor,
            unselectedContent = unselectedContentColor,
            hoveredContent = hoveredContentColor,
        ),
    )

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = selectedContentColor,
        shadowElevation = elevation,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            TabIndicator(state, visuals)
            CompositionLocalProvider(
                LocalTabBarState provides state,
                LocalTabBarVisuals provides visuals,
            ) {
                Row(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/**
 * 指示器：与标签行叠在同一坐标系，按选中项的实测矩形做**弹性**滑动。
 *
 * ## 动效来源
 *
 * 复刻 Kazumi 追番页的 `TabBar(indicatorAnimation: TabIndicatorAnimation.elastic)`
 * （Flutter `_IndicatorPainter._applyElasticEffect`）：指示器不是整块平移过去，
 * 而是**左右两条边各走一条缓动**——前导边用 `sin`（起步快）、尾随边用 `1-cos`（起步慢），
 * 于是切换途中它先被拉长，再收拢到目标宽度。整块做插值就只是普通滑块，少了那橡皮筋感。
 *
 * 时长与整体缓动也照抄：Flutter 的 `TabController.animateTo` 默认 300ms + `Curves.ease`，
 * 然后在**已缓动的进度**上再套那两条正弦曲线，所以这里同样是两段叠加，不是二选一。
 *
 * `fromRect` 记的是「切换那一刻指针所在的位置」而不是上一个标签的矩形：连续快点时
 * 指针还没滑到就改口，动画会从当前位置接着走，不会先跳回上一个标签再出发。
 *
 * ## 相位（性能）
 *
 * Flutter 原版在 `paint()` 里按动画值算矩形，Compose 这边也要守住同一个原则：
 * **动画值只在布局或绘制阶段读**，不读进组合阶段。
 *
 * - 尺寸走 `Modifier.layout`：矩形宽度本身是动画量，没法只靠变换表达（`scaleX` 会把圆角压成椭圆）；
 * - 位移与透明度走 `Modifier.graphicsLayer`：纯绘制阶段失效，连重排都不需要；
 * - 组合阶段只有一个 `LaunchedEffect(selected)`，一次切换发生一次。
 *
 * 若把 `progress.value` 读在组合里，300ms 内每次切页要整块重组 18～36 次，
 * 属于「能跑但没必要」的那类浪费。
 */
@Composable
private fun TabIndicator(state: TabBarState, visuals: TabBarVisuals) {
    val bounds = state.bounds
    val selected = state.selectedIndex
    // 1 = 停稳在目标上；切换时从 0 走到 1。
    val progress = remember { Animatable(1f) }
    var fromRect by remember { mutableStateOf<Rect?>(null) }
    var fromIndex by remember { mutableIntStateOf(selected) }
    var toIndex by remember { mutableIntStateOf(selected) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(selected) {
        if (selected == toIndex) return@LaunchedEffect
        // 这里读 `progress.value` 不算组合期读取（协程里不受快照订阅影响），
        // 拿到的是上一帧画面上的真实位置（进度为 1 时就是旧标签的矩形）。
        fromRect = resolveRect(bounds, fromRect, fromIndex, toIndex, progress.value)
        fromIndex = toIndex
        toIndex = selected
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = IndicatorSwapMillis, easing = IndicatorSwapEasing),
        )
    }

    // 首帧尚未量到边界时不闪一个 0×0 的圆点。只有「有没有边界」这一件事进组合。
    val ready = bounds.getOrNull(toIndex) != null
    LaunchedEffect(ready) {
        fade.animateTo(
            targetValue = if (ready) 1f else 0f,
            animationSpec = tween(durationMillis = IndicatorFadeMillis),
        )
    }

    Box(
        modifier = Modifier
            // 位移、透明度、裁剪都在绘制阶段读：动画推进只让这一层失效。
            .graphicsLayer {
                val rect = resolveRect(bounds, fromRect, fromIndex, toIndex, progress.value)
                if (rect != null) {
                    translationX = rect.left
                    translationY = rect.top
                }
                alpha = fade.value
                shape = visuals.shape
                clip = true
            }
            // 尺寸在布局阶段读：只让这一个叶子的测量失效，不惊动标签行。
            .layout { measurable, _ ->
                val rect = resolveRect(bounds, fromRect, fromIndex, toIndex, progress.value)
                val width = rect?.width?.roundToInt() ?: 0
                val height = rect?.height?.roundToInt() ?: 0
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .background(visuals.colors.indicator),
    )
}

/**
 * 当前这一帧指示器应处的矩形：`to` 取目标标签的实测边界，`from` 取切换那一刻的快照。
 *
 * 供布局/绘制阶段调用，返回值随 [progress] 逐帧变化；`from` 为空（首帧还没切换过）时就停在目标上。
 */
private fun resolveRect(
    bounds: List<Rect>,
    fromRect: Rect?,
    fromIndex: Int,
    toIndex: Int,
    progress: Float,
): Rect? {
    val to = bounds.getOrNull(toIndex) ?: return null
    return elasticRect(
        from = fromRect ?: to,
        to = to,
        progress = progress,
        movingRight = toIndex > fromIndex,
    )
}

/**
 * 弹性插值：左右边各用一条缓动，得到「被拉长再收拢」的中间矩形。
 *
 * 往右切换时右边是前导边（先冲出去）、左边是尾随边（后跟上），所以前者用 [decelerate]、
 * 后者用 [accelerate]；往左切换反过来。上下边不需要拉伸，跟着整体进度走。
 *
 * `right` 兜一个 `coerceAtLeast(left)`：标签之间有空隙，理论上两条边不会交叉，
 * 但差一帧的边界情况不值得让宽度变成负数。
 */
private fun elasticRect(from: Rect, to: Rect, progress: Float, movingRight: Boolean): Rect {
    val leftFraction = if (movingRight) accelerate(progress) else decelerate(progress)
    val rightFraction = if (movingRight) decelerate(progress) else accelerate(progress)
    val left = lerp(from.left, to.left, leftFraction)
    val right = lerp(from.right, to.right, rightFraction)
    return Rect(
        left = left,
        top = lerp(from.top, to.top, progress),
        right = right.coerceAtLeast(left),
        bottom = lerp(from.bottom, to.bottom, progress),
    )
}

private const val HalfPi = (PI / 2).toFloat()

/** Flutter `accelerateInterpolation`：`1 - cos(tπ/2)`，起步慢、收尾快。 */
private fun accelerate(t: Float): Float = 1f - cos(t * HalfPi)

/** Flutter `decelerateInterpolation`：`sin(tπ/2)`，起步快、收尾慢。 */
private fun decelerate(t: Float): Float = sin(t * HalfPi)

/**
 * 切换时长：Flutter `TabController.animationDuration` 的默认值（`kTabScrollDuration`）。
 * 追番页没有传 `animationDuration`，所以走的就是这个 300ms。
 */
private const val IndicatorSwapMillis = 300

/** 整体进度的缓动：Flutter `TabController.animateTo` 的默认 `Curves.ease`。 */
private val IndicatorSwapEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** 指示器从「还没量到边界」到可见的淡入时长；只跑一次，不影响切换动效。 */
private const val IndicatorFadeMillis = 120

/**
 * 单个标签：图标（可选）+ 文字（可选第二行副标题），整块可点。
 *
 * 关键约定：**内边距在 `selectable` 内侧**，所以 触控范围 == 指示器矩形 == 实测矩形，
 * 不会出现「看到的胶囊比能点的地方大」。
 *
 * @param index 组内下标，必须与 [TabBar] 的 `selectedIndex` 同一套编号。
 * @param text 主文案；为 null 时只显示图标。
 * @param supportingText 第二行副标题，非空即两行排布。
 * @param badgeCount 角标数字，>0 时显示在图标右上角。
 * @param style 覆盖 [TabBar] 下发的样式。
 */
@Composable
fun Tab(
    index: Int,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    selectedIcon: ImageVector? = icon,
    supportingText: String? = null,
    badgeCount: Int? = null,
    style: TabItemStyle? = null,
) {
    val state = LocalTabBarState.current
        ?: error("Tab 必须放在 TabBar 内使用")
    val visuals = LocalTabBarVisuals.current
    val resolved = style ?: visuals.style
    val selected = state.selectedIndex == index
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val contentColor by animateColorAsState(
        targetValue = when {
            selected -> visuals.colors.selectedContent
            hovered -> visuals.colors.hoveredContent
            else -> visuals.colors.unselectedContent
        },
        animationSpec = tween(durationMillis = ColorAnimMillis),
        label = "tabContentColor",
    )

    val base = modifier
        // 最外层测量：量到的矩形同时就是「触控范围 + 指示器范围」
        .onGloballyPositioned { coords ->
            state.report(index, Rect(coords.positionInParent(), coords.size.toSize()))
        }
        .clip(visuals.shape)
        .selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            role = Role.Tab,
        ) { state.onSelect(index) }
        .then(if (resolved.height != null) Modifier.height(resolved.height) else Modifier)
        .padding(horizontal = resolved.horizontalPadding, vertical = resolved.verticalPadding)

    val label: @Composable () -> Unit = {
        TabLabel(
            text = text,
            supportingText = supportingText,
            style = resolved,
            selected = selected,
            color = contentColor,
        )
    }
    val iconSlot: (@Composable () -> Unit)? = icon?.let { iconVector ->
        {
            TabIcon(
                icon = if (selected) selectedIcon ?: iconVector else iconVector,
                size = resolved.iconSize,
                tint = contentColor,
                badgeCount = badgeCount,
            )
        }
    }

    when (resolved.orientation) {
        TabOrientation.Horizontal -> Row(
            modifier = base,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabContent(iconSlot, resolved.iconLabelSpacing, label, vertical = false)
        }

        TabOrientation.Vertical -> Column(
            modifier = base,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TabContent(iconSlot, resolved.iconLabelSpacing, label, vertical = true)
        }
    }
}

@Composable
private fun TabContent(
    icon: (@Composable () -> Unit)?,
    spacing: Dp,
    label: @Composable () -> Unit,
    vertical: Boolean,
) {
    if (icon != null) {
        icon()
        if (vertical) {
            Spacer(modifier = Modifier.height(spacing))
        } else {
            Spacer(modifier = Modifier.width(spacing))
        }
    }
    label()
}

@Composable
private fun TabIcon(
    icon: ImageVector,
    size: Dp,
    tint: Color,
    badgeCount: Int?,
) {
    if (badgeCount != null && badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(text = if (badgeCount > 99) "99+" else badgeCount.toString()) }
            },
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        }
    } else {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun TabLabel(
    text: String?,
    supportingText: String?,
    style: TabItemStyle,
    selected: Boolean,
    color: Color,
) {
    val weight = if (selected) FontWeight.SemiBold else style.labelWeight
    if (text != null) {
        Text(
            text = text,
            style = style.labelStyle,
            fontWeight = weight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (supportingText != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = supportingText,
            style = style.supportingStyle ?: style.labelStyle,
            fontWeight = weight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 内容色过渡时长：只影响颜色，不参与指示器位移。 */
private const val ColorAnimMillis = 150
