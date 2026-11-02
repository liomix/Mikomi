package com.liomix.mikomi.feature.profile

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize

/**
 * 个人资料页（对齐 Telegram 13 的「我的」页）。
 *
 * 结构（自上而下）：
 * 1. 固定顶栏：只有右侧设置入口，标题由头部名字滑上来顶替；
 * 2. 头部：96dp 头像 + 名字 + 简介。滚动时头像一路缩到 30dp 并挪进顶栏左侧，
 *    名字同步向上滑、最终停在顶栏标题中线，简介在途中淡出
 *    （对应 Telegram 的 `avatarContainer` + `nameTextView` 变换）；
 * 3. 三张动作卡：主题 / 编辑信息 / 设置；
 * 4. 分段标签：动态 / 已归档的动态，指示胶囊用弹簧跟随选中项；
 * 5. 内容区：白色圆角面板 + 三列分隔线。
 *
 * 尺寸与配色取自真机截图实测（1080×2400 / 480dpi）：底 `#F1F1F3`、卡片纯白、
 * 动作卡 106×54dp 圆角 16dp、头像 96dp。
 *
 * @param onThemeClick 动作卡「主题」。
 * @param onSettingsClick 动作卡「设置」与顶栏设置入口。
 * @param onEditInfo 动作卡「编辑信息」（占位，接入编辑页后替换）。
 */
@Composable
fun ProfileScreen(
    onThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEditInfo: () -> Unit = {},
) {
    val palette = rememberProfilePalette()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barHeight = statusBar + TopBarHeight

    // 头部高度：头像 96 + 名字/简介与留白 85。
    val headerHeight = HeaderAvatarSize + 85.dp
    val headerHeightPx = with(density) { headerHeight.toPx() }
    // 收起到底时头像/昵称正好停在顶栏中线，此时动作卡还差 16dp 才到顶栏，所以折叠行程 = 头部高度 − 留白。
    val collapseDistancePx = headerHeightPx - with(density) { CollapsedBottomGap.toPx() }

    // 折叠进度。
    //
    // 不能读 LazyListState 的 item offset：带 contentPadding 时它量的是「内容区起点之后」的距离，
    // 静止态恒为 0（第一版就是在这里算成 1，头像一进页面就贴在左上角）。
    // 也不能直接拿「列表 top + 头部高度」当基准 —— 那依赖 padding 在测量链里的位置。
    // 这里只量同一个节点（动作卡）的根坐标，并在列表回到顶部时重新锚定基准，
    // 于是基准差值与任何 padding / 嵌套容器都无关，等价于 Telegram 读 `item0.getTop()`。
    var restTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var actionsTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var bottomTopPx by remember { mutableFloatStateOf(Float.NaN) }
    val collapse: () -> Float = {
        val restTop = restTopPx
        val actionsTop = actionsTopPx
        val bottomTop = bottomTopPx
        if (restTop.isNaN() || actionsTop.isNaN()) {
            0f
        } else {
            // 分母默认取「头部高度 − 留白」，这是滚到底时的位移，和上面反推的滚动量严格一致；
            // 只有在内容比预期更短（屏幕过高导致底部留白被夹到 0）时才改用实测的可滚距离，
            // 保证滚到底仍然收满，且中途不会因为分母变化而跳一下。
            val scrollable = if (bottomTop.isNaN()) 0f else restTop - bottomTop
            val distance = if (scrollable > 1f) minOf(collapseDistancePx, scrollable) else collapseDistancePx
            ((restTop - actionsTop) / distance).coerceIn(0f, 1f)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }


    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        // 底部留白反推：可滚距离 = 内容总高 − 视口高，取「头部高度 − 16dp」。
        // 少这 16dp 是为了收起后动作卡与顶栏之间留一条呼吸线；多给则卡片会顶到顶栏上。
        // 视口越矮留白越多，用视口算而不是写死 dp。
        val contentPaddingBottom = (maxHeight - barHeight + headerHeight - CollapsedBottomGap -
            (FixedContentAbovePanel + PanelHeight)).coerceAtLeast(0.dp)

        val widthPx = constraints.maxWidth.toFloat()
        val restCenter = Offset(
            x = widthPx / 2f,
            y = with(density) { (barHeight + HeaderAvatarSize / 2).toPx() },
        )
        // 名字的两个纵向锚点：静止时在头像下方一行，折叠后落在顶栏标题中线。
        val nameRestCenterY = with(density) {
            (barHeight + HeaderAvatarSize + 14.dp + 11.dp).toPx()
        }
        val nameTargetCenterY = with(density) { (statusBar + TopBarHeight / 2).toPx() }

        val collapsedCenter = Offset(
            x = with(density) { (CollapsedInset + CollapsedAvatarSize / 2).toPx() },
            y = with(density) { (statusBar + TopBarHeight / 2).toPx() },
        )

        ProfileContent(
            palette = palette,
            listState = listState,
            headerHeight = headerHeight,
            barHeight = barHeight,
            contentPaddingBottom = contentPaddingBottom,
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
            onEditInfo = onEditInfo,
            onThemeClick = onThemeClick,
            onSettingsClick = onSettingsClick,
            onActionsPositioned = { top, atRest, atBottom ->
                actionsTopPx = top
                if (atRest) restTopPx = top
                if (atBottom) bottomTopPx = top
            },
        )

        // 顶栏底色（在列表之上、头像之下）：
        // 内容滚到顶栏时会被这层挡住而不是露在顶栏里；头像画在它上面，所以照样能停在顶栏中。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(palette.background),
        )

        ProfileHeader(
            palette = palette,
            progress = collapse,
            restCenter = restCenter,
            collapsedCenter = collapsedCenter,
            barHeight = barHeight,
            nameRestCenterY = nameRestCenterY,
            nameTargetCenterY = nameTargetCenterY,
        )

        ProfileTopBar(
            palette = palette,
            height = barHeight,
            onSettingsClick = onSettingsClick,
        )
    }
}

/* ---------------------------------------------------------------- 尺寸常量 */

private val TopBarHeight = 56.dp

/** 头部头像直径（真机实测 96dp）。 */
private val HeaderAvatarSize = 96.dp

/** 折叠后停靠在顶栏里的小头像直径（Telegram 是 42dp，这里取 34dp）。 */
private val CollapsedAvatarSize = 34.dp

/** 折叠后小头像左边距。 */
private val CollapsedInset = 16.dp

/** 内容面板最小高度（空网格也要能撑出可滚距离）。 */
private val PanelHeight = 420.dp

/** 收起到底时，动作卡与顶栏之间保留的间距。 */
private val CollapsedBottomGap = 16.dp

/** 面板以上的固定内容高度：头部 + 动作卡 + 两处间距 + 标签栏。 */
private val FixedContentAbovePanel = 306.dp

/* ---------------------------------------------------------------- 配色 */

/**
 * 页面配色。
 *
 * 日间对齐 Telegram 的浅灰底 + 纯白卡片；夜间跟随主题转纯黑 + 深灰卡片，
 * 不写死日间色值，避免夜间模式下列表整片发白。
 */
private data class ProfilePalette(
    val background: Color,
    val card: Color,
    val title: Color,
    val value: Color,
    val secondary: Color,
    val tertiary: Color,
    val divider: Color,
    val accent: Color,
    val accentContainer: Color,
)

@Composable
private fun rememberProfilePalette(): ProfilePalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return remember(dark, scheme.primary) {
        if (dark) {
            ProfilePalette(
                background = Color(0xFF000000),
                card = Color(0xFF1A1A1C),
                title = Color(0xFFF5F5F5),
                value = Color(0xFFEDEDED),
                secondary = Color(0xFF8E9296),
                tertiary = Color(0xFF9AA0A6),
                divider = Color(0xFF232326),
                accent = Color(0xFF6FB6E8),
                accentContainer = Color(0xFF16303F),
            )
        } else {
            ProfilePalette(
                background = Color(0xFFF1F1F3),
                card = Color(0xFFFFFFFF),
                title = Color(0xFF222222),
                value = Color(0xFF1A1D21),
                secondary = Color(0xFF79807E),
                tertiary = Color(0xFF82868A),
                divider = Color(0xFFF7F7F8),
                accent = Color(0xFF3A95D5),
                accentContainer = Color(0xFFDFECF6),
            )
        }
    }
}

/* ---------------------------------------------------------------- 顶栏 */

@Composable
private fun ProfileTopBar(
    palette: ProfilePalette,
    height: Dp,
    onSettingsClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.profile_settings_entry),
                tint = palette.title,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/* ---------------------------------------------------------------- 头部 */

/**
 * 头部层：盖在列表之上。
 *
 * 头像从「页面中央 96dp」插值到「顶栏左侧 34dp」；名字同步上移到顶栏标题中线，
 * 简介在途中淡出。与 Telegram 一致，头像与名字画在列表上层，滚动时内容从下方经过。
 */
@Composable
private fun ProfileHeader(
    palette: ProfilePalette,
    progress: () -> Float,
    restCenter: Offset,
    collapsedCenter: Offset,
    barHeight: Dp,
    nameRestCenterY: Float,
    nameTargetCenterY: Float,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { HeaderAvatarSize.toPx() }
    val collapsedSizePx = with(density) { CollapsedAvatarSize.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val p = progress()
                    // 与 Telegram 一致：尺寸随折叠进度线性收缩（`lerp(42, 96, diff) / 100`），
                    // 只有下拉放大那条路径才有「前 1/3 缩完」的平台期，折叠态没有。
                    val cx = restCenter.x + (collapsedCenter.x - restCenter.x) * p
                    val cy = restCenter.y + (collapsedCenter.y - restCenter.y) * p
                    val side = sizePx + (collapsedSizePx - sizePx) * p
                    translationX = cx - side / 2f
                    translationY = cy - side / 2f
                    scaleX = side / sizePx
                    scaleY = side / sizePx
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .size(HeaderAvatarSize)
                .clip(CircleShape),
        ) {
            ProfileAvatar()
        }

        // 名字：跟着折叠一路向上滑，最终停在顶栏标题的位置（不是原地淡出、另起一个标题）。
        // 简介没有对应位置，在路上淡掉。
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = barHeight + HeaderAvatarSize + 14.dp)
                .graphicsLayer {
                    translationY = (nameTargetCenterY - nameRestCenterY) * progress()
                },
        ) {
            Text(
                text = stringResource(R.string.profile_name),
                color = palette.title,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 56.dp),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.profile_bio),
                color = palette.secondary,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 56.dp)
                    .graphicsLayer { alpha = (1f - progress() / 0.4f).coerceIn(0f, 1f) },
            )
        }
    }
}

/** 头像占位：资料图尚未接入，先用应用图标顶着。 */
@Composable
private fun ProfileAvatar() {
    Image(
        painter = painterResource(R.drawable.mikomi_avatar),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/* ---------------------------------------------------------------- 列表内容 */

@Composable
private fun ProfileContent(
    palette: ProfilePalette,
    listState: LazyListState,
    headerHeight: Dp,
    barHeight: Dp,
    contentPaddingBottom: Dp,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onThemeClick: () -> Unit,
    onEditInfo: () -> Unit,
    onSettingsClick: () -> Unit,
    onActionsPositioned: (top: Float, atRest: Boolean, atBottom: Boolean) -> Unit,
) {
    LazyColumn(
        state = listState,
        // 列表整体从顶栏下方开始，滚动到顶栏处即被裁掉。
        modifier = Modifier
            .fillMaxSize()
            .padding(top = barHeight)
            // 显式裁切：滚动时内容在顶栏下缘被切掉，而不是画到顶栏上面
            .clipToBounds(),
        // 顶部留出头部高度；底部留白由视口高度反推，保证可滚距离不小于头部高度，
        // 同时顺带盖住悬浮导航栏。
        contentPadding = PaddingValues(top = headerHeight, bottom = contentPaddingBottom),
    ) {
        item(key = "actions") {
            ActionCardsRow(
                palette = palette,
                onThemeClick = onThemeClick,
                onEditInfo = onEditInfo,
                onSettingsClick = onSettingsClick,
                // 顶部之外的位置只更新读数；回到列表顶端（canScrollBackward=false）时顺带重新锚定基准。
                modifier = Modifier.onGloballyPositioned {
                    onActionsPositioned(
                        it.positionInRoot().y,
                        !listState.canScrollBackward,
                        !listState.canScrollForward,
                    )
                },
            )
        }
        item(key = "tabs") {
            Spacer(modifier = Modifier.height(21.dp))
            ProfileTabs(
                palette = palette,
                selected = selectedTab,
                onSelect = onSelectTab,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "content") {
            Spacer(modifier = Modifier.height(13.dp))
            ContentPanel(palette = palette, selectedTab = selectedTab)
        }
    }
}

@Composable
private fun ActionCardsRow(
    palette: ProfilePalette,
    onThemeClick: () -> Unit,
    onEditInfo: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        ActionCard(
            palette = palette,
            icon = Icons.Outlined.Palette,
            label = stringResource(R.string.profile_theme_entry),
            onClick = onThemeClick,
            modifier = Modifier.weight(1f),
        )
        ActionCard(
            palette = palette,
            icon = Icons.Filled.Edit,
            label = stringResource(R.string.profile_action_edit_info),
            onClick = onEditInfo,
            modifier = Modifier.weight(1f),
        )
        ActionCard(
            palette = palette,
            icon = Icons.Filled.Settings,
            label = stringResource(R.string.profile_action_settings),
            onClick = onSettingsClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 动作卡：图标在上、文字在下，整块可点。
 *
 * 按压时轻微回缩（0.97），松手用弹簧归位 —— Telegram 这里是纯 ripple，
 * 但没有触感反馈的卡片在长列表里很难确认「点到了」。
 */
@Composable
private fun ActionCard(
    palette: ProfilePalette,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "actionCardScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(palette.card)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.title,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = palette.title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** 分段标签的一项：图标 + 文字。 */
private data class ProfileTabItem(val label: String, val icon: ImageVector)

/**
 * 分段标签：白色胶囊容器 + 选中项下的浅色胶囊。
 *
 * 每项是「图标 + 文字 + 左右各 8dp + 上下各 3dp」，**触控范围与蓝色胶囊完全重合**
 * （不补到 48dp 最小触控尺寸，这是本页的特例）：横向间距交给父级 `spacedBy`，
 * 纵向 3dp 放在 clickable 内侧，胶囊高度直接取实测边界，容器上下留白相等。
 * 指示胶囊用弹簧跟随选中项，x / 宽 / 高都按实测边界动画，两项宽度不同也能贴合。
 */
@Composable
private fun ProfileTabs(
    palette: ProfilePalette,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        ProfileTabItem(stringResource(R.string.profile_tab_history), Icons.Outlined.History),
        ProfileTabItem(stringResource(R.string.profile_tab_favorite), Icons.Filled.Bookmark),
    )
    val density = LocalDensity.current
    val bounds = remember { mutableStateListOf<Rect>() }

    val target = bounds.getOrNull(selected) ?: Rect.Zero
    val spec = spring<Dp>(dampingRatio = 0.78f, stiffness = 650f)
    val indicatorX by animateDpAsState(with(density) { target.left.toDp() }, spec, label = "tabX")
    val indicatorWidth by animateDpAsState(with(density) { target.width.toDp() }, spec, label = "tabWidth")
    val indicatorHeight by animateDpAsState(
        targetValue = with(density) { target.height.toDp() },
        animationSpec = spec,
        label = "tabHeight",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(palette.card)
                // 上下留白相同，蓝色胶囊居中，上下边距一致
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorX)
                        .width(indicatorWidth)
                        .height(indicatorHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(palette.accentContainer),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items.forEachIndexed { index, item ->
                        val color = if (index == selected) palette.accent else palette.tertiary
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // 横向不留内边距（间距由父级 spacedBy 给），纵向 3dp 在 clickable 内侧：
                            // 于是触控范围 == 下面那颗蓝色胶囊的矩形，且不补到 48dp（本页特例）。
                            modifier = Modifier
                                .onGloballyPositioned { coords ->
                                    val rect = Rect(
                                        offset = coords.positionInParent(),
                                        size = coords.size.toSize(),
                                    )
                                    if (bounds.size <= index) {
                                        while (bounds.size <= index) bounds.add(Rect.Zero)
                                    }
                                    bounds[index] = rect
                                }
                                .clip(RoundedCornerShape(percent = 50))
                                .clickable { onSelect(index) }
                                // 内边距全部放在 clickable 内侧：触控范围就此等于蓝色胶囊范围。
                                // 横向 8dp 让胶囊两侧有点呼吸，纵向 3dp 让胶囊比文字略高。
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = item.label,
                                color = color,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 内容面板：白色圆角面板 + 三列分隔线。
 *
 * 高度固定 420dp 起：可滚距离由列表底部留白补足（见 `contentPaddingBottom`），
 * 这里不再承担「撑高页面」的职责。
 */
@Composable
private fun ContentPanel(palette: ProfilePalette, selectedTab: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PanelHeight)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(palette.card),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.width / 3f
            for (i in 1..2) {
                drawLine(
                    color = palette.divider,
                    start = Offset(step * i, 0f),
                    end = Offset(step * i, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        Crossfade(targetState = selectedTab, label = "tabContent") { tab ->
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(
                        if (tab == 0) R.string.profile_empty_history else R.string.profile_empty_favorite,
                    ),
                    color = palette.tertiary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp),
                )
            }
        }
    }
}
