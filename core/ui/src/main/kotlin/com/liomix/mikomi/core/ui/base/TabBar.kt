package com.liomix.mikomi.core.ui.base

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.RectangleShape
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.liomix.mikomi.core.designsystem.Elevation
import com.liomix.mikomi.core.designsystem.Motion
import com.liomix.mikomi.core.designsystem.PillShape
import com.liomix.mikomi.core.designsystem.Sizes
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.designsystem.Stroke
import com.liomix.mikomi.core.designsystem.TextStyles
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 标签的排布方向：图标与文字横排，或图标在上、文字在下。 */
enum class TabOrientation { Horizontal, Vertical }

/**
 * 选中指示器的形态。
 *
 * - [Fill]：一块与标签矩形完全重合的淡色底（胶囊 / 方形，由 `shape` 决定）。适合**并列**
 *   的图标标签，选中项像一枚被按下的键。
 * - [Underline]：贴底的一条短横线，宽度收在文字范围内。适合**一组文字分类**：它只在
 *   告诉你「现在看的是哪一栏」，不在页面上多刷一块色。栏底会同时出现一条 1dp 分隔线
 *   （参考 Material `TabRow` 的做法），否则下划线悬在空中没有落点。
 */
enum class TabHighlight {
    /** 指示器就是标签本身：整块矩形（胶囊 / 方形由 `shape` 决定）。 */
    Fill {
        override fun containerShape(base: Shape): Shape = base

        override val drawsDivider: Boolean get() = false

        override fun indicatorShape(base: Shape): Shape = base

        override fun indicatorRect(
            label: Rect,
            insetPx: Float,
            thicknessPx: Float,
            dividerPx: Float,
        ): Rect = label
    },

    /** 贴底的一条短横线：左右让出标签的横内边距，宽度约等于文字本身。 */
    Underline {
        override fun containerShape(base: Shape): Shape = RectangleShape

        override val drawsDivider: Boolean get() = true

        // 拱形：**顶部倒圆、左右两条竖边、底边平**贴在分隔线上。
        //
        // 关键在顶角半径取高度的一部分而不是整个高度：
        // - 取整个高度 → 顶部成半圆，左右收成尖，读起来是「一片叶子」；
        // - 取 0 → 就是一块矩形。
        // 0.6 时顶部是一段扁弧、两侧各留四成高的竖边，形状才像「拱」。
        //
        // 用 `GenericShape`：半径要按实际绘制尺寸算（高度变了圆角跟着变）。
        override fun indicatorShape(base: Shape): Shape = GenericShape { size, _ ->
            val radius = size.height * ArchCornerRatio
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height),
                    topLeft = CornerRadius(radius),
                    topRight = CornerRadius(radius),
                ),
            )
        }

        override fun indicatorRect(
            label: Rect,
            insetPx: Float,
            thicknessPx: Float,
            dividerPx: Float,
        ): Rect {
            val inset = insetPx.coerceAtMost(label.width / 2f)
            // 底边让出那条 1dp 灰线的位置：指示条压在线的**上沿**，不是盖在线上。
            // 否则线的两半会被圆头遮掉，看起来像「线断了一截」。
            val bottom = label.bottom - dividerPx
            return Rect(
                left = label.left + inset,
                top = bottom - thicknessPx,
                right = label.right - inset,
                bottom = bottom,
            )
        }
    },
    ;

    /**
     * 栏容器的形状。
     *
     * Underline 必须是矩形：`Surface` 会把内容裁到 `shape`，底部那条分隔线在两端
     * 会被圆弧切掉，看起来就是「线的两头是斜的」。这一形态的栏通常是透明底，
     * 矩形裁剪不改变观感。
     */
    internal abstract fun containerShape(base: Shape): Shape

    /**
     * 是否在栏底画一条分隔线。
     *
     * Fill 的指示器本身就是一块底色，再压一条线会变成「胶囊坐在一条横线上」。
     */
    internal abstract val drawsDivider: Boolean

    /** 指示条自己的形状。Underline 是两端半圆的短棒，Fill 跟随栏的 `shape`。 */
    internal abstract fun indicatorShape(base: Shape): Shape

    /**
     * 把标签矩形换算成**指示器**矩形：两种形态对「指示器」的定义不一样。
     *
     * @param dividerPx 栏底那条分隔线的厚度（px），Underline 用它把指示条抬到线之上。
     */
    internal abstract fun indicatorRect(
        label: Rect,
        insetPx: Float,
        thicknessPx: Float,
        dividerPx: Float,
    ): Rect
}

/**
 * 单个标签的样式。
 *
 * 由 [TabBar] 统一下发，单项也可通过 [Tab] 的 `style` 覆盖。
 * 高度、内边距与字号一起决定**指示器（同时就是触控区）**的大小——调这里等于同时调了两者。
 *
 * 四个预设里仍有四个字面量不进间距梯队（5dp 图标与文字间距、3dp 纵向内边距、18dp 纯文字标签
 * 横内边距、56dp 两行标签高度）：它们是**标签的物理规格**，与字号绑在一起算出来的，
 * 换成 4dp/6dp 会一起改掉文字与图标的重心。其余能对上的（8→Sm、12→Md、6→Tight、
 * 2→Xxs、14→Gutter、48→ButtonHeight）都已走 token。
 */
@Immutable
data class TabItemStyle(
    val orientation: TabOrientation = TabOrientation.Horizontal,
    val height: Dp? = null,
    val iconSize: Dp = Sizes.IconMd,
    val iconLabelSpacing: Dp = 5.dp,
    val horizontalPadding: Dp = Spacing.Sm,
    val verticalPadding: Dp = 3.dp,
    val labelStyle: TextStyle = TextStyles.TabLabel,
    val supportingStyle: TextStyle? = null,
    /** 未选中时的字重；选中态固定 SemiBold，一次切换只重排一次文字。 */
    val labelWeight: FontWeight = FontWeight.Normal,
) {
    companion object {
        /** 个人资料页那种紧凑图标标签：18dp 图标 + 14.5sp 文字。 */
        fun compact(): TabItemStyle = TabItemStyle(
            iconSize = Sizes.IconMd,
            iconLabelSpacing = 5.dp, // design-ok: 非梯队，标签物理规格（图标与文字的重心间距）
            horizontalPadding = Spacing.Sm,
            verticalPadding = 3.dp, // design-ok: 非梯队，标签物理规格（24dp 图标与胶囊高度对齐）
            labelStyle = TextStyles.TabLabel,
            labelWeight = FontWeight.SemiBold,
        )

        /** 底部导航栏：图标在上、文字在下，配合 `weight(1f)` 等分。 */
        @Composable
        fun navigation(): TabItemStyle = TabItemStyle(
            orientation = TabOrientation.Vertical,
            iconSize = Sizes.IconMd,
            iconLabelSpacing = Spacing.Xxs,
            horizontalPadding = Spacing.Md,
            verticalPadding = Spacing.Tight,
            labelStyle = MaterialTheme.typography.labelMedium,
            labelWeight = FontWeight.Medium,
        )

        /** 单行文字标签（48dp 高、18dp 横内边距）：排行榜、热播榜这类纯文字切换。 */
        @Composable
        fun text(): TabItemStyle = TabItemStyle(
            height = Sizes.ButtonHeight,
            horizontalPadding = 18.dp, // design-ok: 非梯队，纯文字标签的横内边距
            verticalPadding = Spacing.None,
            labelStyle = MaterialTheme.typography.bodyLarge,
            labelWeight = FontWeight.Normal,
        )

        /** 两行标签（标题 + 副标题，56dp 高）：周日历这种等分七格。 */
        @Composable
        fun twoLine(): TabItemStyle = TabItemStyle(
            orientation = TabOrientation.Vertical,
            height = 56.dp,
            iconLabelSpacing = Spacing.Xxs,
            horizontalPadding = Spacing.Tight,
            verticalPadding = Spacing.None,
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
) {
    companion object {
        /** 默认配色：主色淡底指示器 + 主色选中文字 + 次级未选中文字 + 主色悬停文字。 */
        val Default: TabColors
            @Composable get() = TabColors(
                indicator = MaterialTheme.colorScheme.primary.copy(alpha = TabIndicatorAlpha),
                selectedContent = MaterialTheme.colorScheme.primary,
                unselectedContent = MaterialTheme.colorScheme.onSurfaceVariant,
                hoveredContent = MaterialTheme.colorScheme.onSurface,
            )
    }
}

/** 指示器底色的透明度：主色淡到能看见轮廓、又不压住文字。 */
private const val TabIndicatorAlpha = 0.14f

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
    val highlight: TabHighlight,
)

private val LocalTabBarVisuals = compositionLocalOf {
    TabBarVisuals(
        shape = PillShape,
        style = TabItemStyle(),
        colors = TabColors(Color.Transparent, Color.Unspecified, Color.Unspecified, Color.Unspecified),
        highlight = TabHighlight.Fill,
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
 *     Tab(index = 0, text = "历史", icon = Icons.History)
 *     Tab(index = 1, text = "收藏", icon = Icons.Bookmark)
 * }
 * ```
 *
 * @param selectedIndex 当前选中项下标；组件不自己存选中态。
 * @param onSelect 点击某项时回调下标。
 * @param containerColor 栏底色；需要和内容同色（例如叠在卡片上）时传透明或卡片色。
 * @param fillWidth 标签行是否撑满栏宽。默认 `true`（`weight` 均分需要）；紧贴内容时传 `false`。
 * @param pagerState 与之联动的 pager。传了之后指示器**跟手移动**（拖动到一半就停在两格
 *   中间），而不是松手后才弹到新格；不传则用内部的一次性切换动画。

 * @param colors 一组配色（指示器 / 选中 / 未选中 / 悬停）。**四个颜色是一组，所以做成一个参数**：
 *   个人页那种用自己的调色板的地方，一眼能看出这四项必须同源。
 * @param content 标签项，通常为若干 [Tab]；用 `Modifier.weight(1f)` 可等分宽度。
 */
@Composable
fun TabBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    colors: TabColors = TabColors.Default,
    shape: Shape = PillShape,
    elevation: Dp = Elevation.Flat,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.Compact, vertical = Spacing.Xs),
    itemSpacing: Dp = Spacing.Gutter,
    itemStyle: TabItemStyle = TabItemStyle.compact(),
    highlight: TabHighlight = TabHighlight.Fill,
    pagerState: PagerState? = null,
    fillWidth: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val state = remember { TabBarState() }
    val scope = rememberCoroutineScope()
    state.selectedIndex = selectedIndex
    state.onSelect = { index ->
        // 有 pager 时**由这里统一滚动**：指示器的位置读的就是 pager 的偏移量，
        // 两者的速度必须是同一条曲线，否则会「指示器到了、内容还在滑」。
        pagerState?.let { pager ->
            scope.launch { pager.animateScrollToPage(page = index, animationSpec = IndicatorSwapSpec) }
        }
        onSelect(index)
    }
    val visuals = TabBarVisuals(
        shape = shape,
        style = itemStyle,
        colors = colors,
        highlight = highlight,
    )

    // 下划线形态不要垂直内距：标签贴到栏底，指示条才会紧挨着那条灰线。
    // 留着 4dp 的话，线画在栏底、指示条在线上方 4dp，中间悬着一段空气。
    val layoutDirection = LocalLayoutDirection.current
    val barPadding = if (highlight.drawsDivider) {
        PaddingValues(
            start = contentPadding.calculateLeftPadding(layoutDirection),
            end = contentPadding.calculateRightPadding(layoutDirection),
        )
    } else {
        contentPadding
    }

    Surface(
        modifier = modifier,
        // 形状交给形态自己决定：Underline 必须是矩形，否则底部分隔线会被圆弧切掉。
        shape = highlight.containerShape(shape),
        color = containerColor,
        contentColor = colors.selectedContent,
        shadowElevation = elevation,
    ) {
        Box(
            modifier = Modifier
                // 分隔线画在 padding 外层：它属于「这一栏的底边」，应该贯穿整栏宽度，
                // 而不是缩在文字两端。是否需要这条线由形态自己回答（见 `drawsDivider`）。
                .then(
                    if (highlight.drawsDivider) {
                        val dividerColor = MaterialTheme.colorScheme.outlineVariant
                        Modifier.drawBehind {
                            val y = size.height - Stroke.Thin.toPx() / 2f
                            drawLine(
                                color = dividerColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = Stroke.Thin.toPx(),
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(barPadding),
        ) {
            TabIndicator(state, visuals, pagerState)
            CompositionLocalProvider(
                LocalTabBarState provides state,
                LocalTabBarVisuals provides visuals,
            ) {
                Row(
                    // `fillMaxWidth` 是居中的前提：Row 只占内容宽时，`CenterHorizontally`
                    // 没有多余空间可分配，标签仍然贴在左边。
                    modifier = Modifier
                        // 撑满：标签用 `weight` 等分时必须有富余宽度可分。
                        // 嵌在「标题行右侧」这种位置时传 `false`，栏紧贴内容，不被拉到整行宽。
                        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                        .selectableGroup(),
                    // 整体居中：标签按内容宽排列时（不传 `weight`），靠左会让两栏挤在
                    // 屏幕左边。传了 `weight` 的那几个调用点本来就被撑满，居中不起作用。
                    horizontalArrangement = Arrangement.spacedBy(
                        space = itemSpacing,
                        alignment = Alignment.CenterHorizontally,
                    ),
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
private fun TabIndicator(
    state: TabBarState,
    visuals: TabBarVisuals,
    pagerState: PagerState?,
) {
    val bounds = state.bounds
    val selected = state.selectedIndex
    // 1 = 停稳在目标上；切换时从 0 走到 1。
    val progress = remember { Animatable(1f) }
    var fromRect by remember { mutableStateOf<Rect?>(null) }
    var fromIndex by remember { mutableIntStateOf(selected) }
    var toIndex by remember { mutableIntStateOf(selected) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(selected, pagerState) {
        if (selected == toIndex) return@LaunchedEffect
        // 有 pager 时位置完全由它驱动（`currentRect` 读 `currentPageOffsetFraction`），
        // 再跑一遍这个内部动画就是白跑：`progress` 每帧变化 → `graphicsLayer` 与 `layout`
        // 跟着失效重算，结果却和上一帧一样 —— 点击时那一下卡顿就是它。
        if (pagerState != null) return@LaunchedEffect
        // 这里读 `progress.value` 不算组合期读取（协程里不受快照订阅影响），
        // 拿到的是上一帧画面上的真实位置（进度为 1 时就是旧标签的矩形）。
        fromRect = resolveRect(bounds, fromRect, fromIndex, toIndex, progress.value)
        fromIndex = toIndex
        toIndex = selected
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = IndicatorSwapSpec,
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

    val highlight = visuals.highlight
    val inset = visuals.style.horizontalPadding
    val thickness = UnderlineThickness
    // 形状按**实际绘制尺寸**算，所以只能交给 `background` 去画（它在拿到节点尺寸后
    // 才求形状）。放进 `graphicsLayer { shape = ...; clip = true }` 是不行的：
    // 那层裁剪作用在「之后声明的内容」上，而底色是最内层的绘制，顺序反了。
    val indicatorShape = highlight.indicatorShape(visuals.shape)

    /**
     * 当前该画在哪一格。
     *
     * 有 pager 时按它的 `currentPage + offsetFraction` 在两格之间插值 ——
     * 拖动多少就走多少（跟手），并且**两种形态共用**这一条路径：弹性插值算出来的是一个
     * 会被拉伸的矩形，胶囊与下划线都直接用它的结果。没有 pager 时走内部动画（[resolveRect]）。
     */
    fun currentRect(): Rect? {
        val pager = pagerState
        if (pager != null && bounds.size > 1) {
            val page = pager.currentPage.coerceIn(0, bounds.lastIndex)
            val fraction = pager.currentPageOffsetFraction
            val other = (page + if (fraction >= 0f) 1 else -1).coerceIn(0, bounds.lastIndex)
            // 关键：跟手这一段也走 `elasticRect`，不是逐边线性插值。
            // 线性插值下矩形只是「整体平移」，看起来像一块贴纸在滑；
            // 弹性插值让左右两边用不同缓动（一走一赶），拖动时矩形的宽度会先拉长再收回，
            // 这才是「液体」的观感 —— 也就是 Flutter `TabBar` 的 indicator 手感。
            val t = abs(fraction).coerceIn(0f, 1f)
            return elasticRect(
                from = bounds[page],
                to = bounds[other],
                progress = t,
                movingRight = other > page,
            )
        }
        return resolveRect(bounds, fromRect, fromIndex, toIndex, progress.value)
    }

    Box(
        modifier = Modifier
            // 位移、透明度、裁剪都在绘制阶段读：动画推进只让这一层失效。
            .graphicsLayer {
                val rect = currentRect()?.let { rect -> highlight.indicatorRect(rect, inset.toPx(), thickness.toPx(), Stroke.Thin.toPx()) }
                if (rect != null) {
                    translationX = rect.left
                    translationY = rect.top
                }
                alpha = fade.value
                // Underline 必须笔直：跟着 `shape` 走会变成一根两头半圆的短棒，
                // 在 2dp 的高度上读起来是「一颗小药丸」而不是一条线。
                shape = if (highlight == TabHighlight.Underline) {
                    RectangleShape
                } else {
                    visuals.shape
                }
                clip = true
            }
            // 尺寸在布局阶段读：只让这一个叶子的测量失效，不惊动标签行。
            .layout { measurable, _ ->
                val rect = currentRect()?.let { rect -> highlight.indicatorRect(rect, inset.toPx(), thickness.toPx(), Stroke.Thin.toPx()) }
                val width = rect?.width?.roundToInt() ?: 0
                val height = rect?.height?.roundToInt() ?: 0
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .background(color = visuals.colors.indicator, shape = indicatorShape),
    )
}

/** 拱形指示器的顶角半径占高度比例：0.6 时顶部是扁弧、两侧留四成竖边。 */
private const val ArchCornerRatio = 0.6f

/** 下划线厚度：走 `Stroke.Underline`（4dp）。2dp 时它会被读成一条分隔线而不是「选中的那一栏」。 */
private val UnderlineThickness = Stroke.Underline

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
    // 一种路径，不分跨几格：起始格的左边去追目标格的左边、右边去追右边。
    // 跨一格时两侧速度差最明显（先拉开再收住）；跨多格时它被拉长、盖住中间的格子。
    // 不再为「跨多格」单独写一条 —— 那只是同一个动作在更长距离上的样子。
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
 * 换位用的动画：**弹簧**，不是固定时长。
 *
 * 抄 Flutter `TabIndicatorAnimation.elastic`：跨一格轻推就把目标吸过来，跨四格距离长、
 * 弹簧给的速度自然更大，于是它真的会「甩」过去，而且比跨一格用时更长。
 * 固定 `tween` 做不到 —— 同样时长跨 4 格，速度就是 4 倍，只会显得急。
 *
 * `DampingRatioNoBouncy`（1.0）：临界阻尼，停在目标上不再回头。
 * 想要「撞一下再弹回」就调低它 —— 那是另一种手感，默认不要。
 */
private val IndicatorSwapSpec: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)


/** 指示器从「还没量到边界」到可见的淡入时长；只跑一次，不影响切换动效。 */
private const val IndicatorFadeMillis = Motion.Duration.Fast

/**
 * 单个标签：图标（可选）+ 文字（可选第二行副标题），整块可点。
 *
 * 关键约定：**内边距在 `selectable` 内侧**，所以 触控范围 == 指示器矩形 == 实测矩形，
 * 不会出现「看到的胶囊比能点的地方大」。
 *
 * 也因此这里不挂 indication：波纹画出来正好是这个胶囊，等于把触控范围显示给用户看。
 * 按压反馈只靠内容色与指示器动效，视觉上不会多出一层灰块。
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
            // 不画按压层：selectable 的自带波纹是「整个胶囊被填一层灰」，
            // 在图标+文字的小标签上看着就像把触控范围直接显示出来了。
            // 点击反馈由内容色过渡与指示器滑动承担（两者本来就在动）。
            indication = null,
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
            // 内容居中而不是靠左：等分宽度时（`weight(1f)`）靠左会让标签看起来
            // 偏在自己的格子里，几个标签的间距一眼就不齐。
            horizontalArrangement = Arrangement.Center,
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
            Icon(icon = icon, contentDescription = null, tint = tint, size = size)
        }
    } else {
        Icon(icon = icon, contentDescription = null, tint = tint, size = size)
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
        Spacer(modifier = Modifier.height(Spacing.Xxs))
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
private const val ColorAnimMillis = Motion.Duration.Short
