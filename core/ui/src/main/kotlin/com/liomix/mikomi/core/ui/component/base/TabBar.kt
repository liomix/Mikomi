package com.liomix.mikomi.core.ui.component.base

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize

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

    internal fun report(index: Int, rect: Rect) {
        if (bounds.size <= index) {
            while (bounds.size <= index) bounds.add(Rect.Zero)
        }
        bounds[index] = rect
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
 * 动效：指示器的 x / 宽 / 高共用一条 spring，切换时是**滑动**而不是原地淡入；
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

/** 指示器：与标签行叠在同一坐标系，按选中项的实测矩形做三轴动画。 */
@Composable
private fun TabIndicator(state: TabBarState, visuals: TabBarVisuals) {
    val target = state.bounds.getOrNull(state.selectedIndex)
    val density = LocalDensity.current
    val spec = spring<Dp>(
        dampingRatio = IndicatorSpring.dampingRatio,
        stiffness = IndicatorSpring.stiffness,
    )

    val x by animateDpAsState(with(density) { (target?.left ?: 0f).toDp() }, spec, label = "tabIndicatorX")
    val width by animateDpAsState(with(density) { (target?.width ?: 0f).toDp() }, spec, label = "tabIndicatorWidth")
    val height by animateDpAsState(with(density) { (target?.height ?: 0f).toDp() }, spec, label = "tabIndicatorHeight")
    // 首帧尚未量到边界时不闪一个 0×0 的圆点。
    val visible by animateFloatAsState(
        targetValue = if (target == null) 0f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "tabIndicatorAlpha",
    )

    Box(
        modifier = Modifier
            .offset(x = x)
            .width(width)
            .height(height)
            .alpha(visible)
            .clip(visuals.shape)
            .background(visuals.colors.indicator),
    )
}

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

/** 指示器滑动用的弹簧：略欠阻尼，收尾干净、不过冲。 */
private object IndicatorSpring {
    const val dampingRatio = 0.78f
    const val stiffness = 650f
}

private const val ColorAnimMillis = 150
