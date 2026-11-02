package com.liomix.mikomi.feature.profile

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liomix.mikomi.core.ui.component.MikomiNavBarBottomSpace
import com.liomix.mikomi.core.ui.component.Tab
import com.liomix.mikomi.core.ui.component.TabBar
import com.liomix.mikomi.feature.profile.component.ProfilePalette
import com.liomix.mikomi.feature.profile.component.ProfileTabContent
import com.liomix.mikomi.feature.profile.component.rememberProfilePalette
import com.liomix.mikomi.feature.profile.component.rememberProfileTabContentState
import kotlin.math.roundToInt

/**
 * 个人资料页（对齐 Telegram 13 的「我的」页）。
 *
 * 页面没有外层滚动容器，只有两层：
 * 1. 内容层是一个 [Column]：可收缩的头部占位 → 动作卡 → 标签栏 → 内容卡片；
 * 2. 只有内容卡片内部的列表在滚动。头部折叠由 `NestedScrollConnection` 在列表手势**之前**
 *    拦截：先收头部，收满后再把剩下的手势交给卡片内部列表。
 *
 * 唯一的驱动量是 `collapsePx`（0…头部高度−16dp）：
 * - 头部占位按同一位移在**布局阶段**缩短，下面的内容跟着上移；
 * - 卡片用 `weight(1f)` 吃掉剩余高度，因此任意进度下下沿都贴着屏幕底，底部不会露背景；
 * - 头像 96dp → 34dp 并挪到顶栏左侧；
 * - 名字同步上移，最终停在顶栏标题中线，简介在途中淡出。
 *
 * @param onThemeClick 动作卡「主题」。
 * @param onSettingsClick 顶栏设置入口与动作卡「设置」。
 * @param onEditInfo 动作卡「编辑信息」（占位）。
 */
@Composable
fun ProfileScreen(
    onThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEditInfo: () -> Unit = {},
) {
    val palette = rememberProfilePalette()
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barHeight = statusBar + TopBarHeight

    // 头部高度：头像 96 + 名字/简介与留白 85；完全收起时与顶栏留 16dp 呼吸。
    val headerHeight = HeaderAvatarSize + 85.dp
    val maxCollapsePx = with(density) { (headerHeight - CollapsedBottomGap).toPx() }

    var collapsePx by remember { mutableFloatStateOf(0f) }
    val collapseFraction: () -> Float = { (collapsePx / maxCollapsePx).coerceIn(0f, 1f) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val contentState = rememberProfileTabContentState()
    val contentListState = rememberLazyListState()

    // 只有「真的在滑动」且已经贴到列表底部时才追加：静止时不预加载，
    // 卡片里就先摆 6 个占位格；用户往上滑到底再续下一批。
    LaunchedEffect(contentListState, contentState) {
        snapshotFlow {
            val info = contentListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            contentListState.isScrollInProgress && lastVisible >= info.totalItemsCount - 2
        }.collect { atEnd -> if (atEnd) contentState.loadMore() }
    }

    // 手势接力：上滑先把头部收满，再交给卡片内部列表；下滑时列表到顶后把剩余量还给头部。
    val collapseConnection = remember(maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val room = maxCollapsePx - collapsePx
                if (room <= 0f) return Offset.Zero
                val used = (-available.y).coerceAtMost(room)
                collapsePx += used
                return Offset(0f, -used)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f || collapsePx <= 0f) return Offset.Zero
                val used = available.y.coerceAtMost(collapsePx)
                collapsePx -= used
                return Offset(0f, used)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y >= 0f || collapsePx >= maxCollapsePx) return Velocity.Zero
                animate(
                    initialValue = collapsePx,
                    targetValue = maxCollapsePx,
                    initialVelocity = -available.y,
                ) { value, _ -> collapsePx = value }
                return Velocity(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y <= 0f || collapsePx <= 0f) return Velocity.Zero
                animate(
                    initialValue = collapsePx,
                    targetValue = 0f,
                    initialVelocity = available.y,
                ) { value, _ -> collapsePx = value }
                return Velocity(0f, available.y)
            }
        }
    }

    // 没有外层滚动容器，所以直接拖头部区域也能收起。
    val headerDragState = rememberDraggableState { delta ->
        collapsePx = (collapsePx - delta).coerceIn(0f, maxCollapsePx)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        // 头部占位随折叠逐帧收缩，下面的内容自然上移。
        // 不能再用「整列 offset 上移」：Column 会把最后一个子项的高度夹在「剩余高度」以内，
        // 上移多少，卡片下沿就跟着抬起多少，屏幕底部会露出一条页面背景（截图里那块空白）。
        val headerHeightPx = with(density) { headerHeight.toPx() }

        // 卡片下沿停在悬浮导航栏上沿 14dp：底栏占位 = 系统导航栏 inset + 栏体 + 底部外边距。
        val navBarInset = WindowInsets
            .navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        val contentBottomSpace = navBarInset + MikomiNavBarBottomSpace + ContentBottomGap

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = barHeight, bottom = contentBottomSpace),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    // 在**布局阶段**读 collapsePx：收缩只触发重排，不触发重组。
                    .layout { measurable, constraints ->
                        val h = (headerHeightPx - collapsePx)
                            .coerceIn(0f, headerHeightPx)
                            .roundToInt()
                        val placeable = measurable.measure(
                            Constraints.fixed(constraints.maxWidth, h),
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                    .draggable(state = headerDragState, orientation = Orientation.Vertical),
            )

            ActionCardsRow(
                palette = palette,
                onThemeClick = onThemeClick,
                onEditInfo = onEditInfo,
                onSettingsClick = onSettingsClick,
            )

            Spacer(modifier = Modifier.height(21.dp))

            ProfileTabs(
                palette = palette,
                selected = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(13.dp))

            ProfileTabContent(
                state = contentState,
                palette = palette,
                listState = contentListState,
                modifier = Modifier
                    // 吃掉剩余高度：任意折叠进度下卡片下沿都贴着屏幕底，不会露背景。
                    .weight(1f)
                    .nestedScroll(collapseConnection)
                    // 左右滑动切换标签：竖向交给列表，横向由这里接管。
                    .pointerInput(selectedTab) {
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = {
                                val threshold = SwipeThreshold.toPx()
                                if (dragged <= -threshold) {
                                    selectedTab = (selectedTab + 1).coerceAtMost(TabCount - 1)
                                }
                                if (dragged >= threshold) {
                                    selectedTab = (selectedTab - 1).coerceAtLeast(0)
                                }
                                dragged = 0f
                            },
                            onHorizontalDrag = { change, amount ->
                                dragged += amount
                                change.consume()
                            },
                        )
                    },
            )
        }

        // 头部层必须画在内容层之上，且中间**不能**再夹一层不透明底色：
        // 内容层从顶栏下缘开始布局，本来就不会画到顶栏里，不需要底色遮挡；
        // 之前那层底色（画在头像之后）正好把停进顶栏的头像和昵称盖住了。
        ProfileHeader(
            palette = palette,
            progress = collapseFraction,
            restCenter = Offset(
                x = constraints.maxWidth / 2f,
                y = with(density) { (barHeight + HeaderAvatarSize / 2).toPx() },
            ),
            collapsedCenter = Offset(
                x = with(density) { (CollapsedInset + CollapsedAvatarSize / 2).toPx() },
                y = with(density) { (statusBar + TopBarHeight / 2).toPx() },
            ),
            nameRestCenterY = with(density) { (barHeight + HeaderAvatarSize + 14.dp + 11.dp).toPx() },
            nameTargetCenterY = with(density) { (statusBar + TopBarHeight / 2).toPx() },
        )

        ProfileTopBar(palette = palette, height = barHeight, onSettingsClick = onSettingsClick)
    }
}

/* ---------------------------------------------------------------- 尺寸常量 */

private val TopBarHeight = 56.dp

/** 头部头像直径（真机实测 96dp）。 */
private val HeaderAvatarSize = 96.dp

/** 折叠后停靠在顶栏里的小头像直径。 */
private val CollapsedAvatarSize = 34.dp

/** 折叠后小头像左边距。 */
private val CollapsedInset = 16.dp

/** 收起到底时，动作卡与顶栏之间保留的间距。 */
private val CollapsedBottomGap = 16.dp

/** 内容卡片下沿与悬浮导航栏上沿之间的间距。 */
private val ContentBottomGap = 14.dp

/** 横向滑动切换标签的触发距离。 */
private val SwipeThreshold = 72.dp

/** 标签数量（历史 / 收藏）。 */
private const val TabCount = 2

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
 * 头部层：盖在内容层之上。
 *
 * 头像从「页面中央 96dp」插值到「顶栏左侧 34dp」；名字同步上移到顶栏标题中线，
 * 简介在途中淡出。与 Telegram 一致，头像与名字画在内容之上，内容从下方经过。
 */
@Composable
private fun ProfileHeader(
    palette: ProfilePalette,
    progress: () -> Float,
    restCenter: Offset,
    collapsedCenter: Offset,
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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = with(density) { nameRestCenterY.toDp() } - 11.dp)
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

/* ---------------------------------------------------------------- 动作卡 */

@Composable
private fun ActionCardsRow(
    palette: ProfilePalette,
    onThemeClick: () -> Unit,
    onEditInfo: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
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

/** 动作卡：图标在上、文字在下，整块可点；按下轻缩，松手弹簧归位。 */
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

/* ---------------------------------------------------------------- 标签栏 */

/**
 * 分段标签：历史 / 收藏，复用通用 [TabBar] 的滑动指示器。
 *
 * 触控范围与胶囊矩形完全重合（内边距在 `selectable` 内侧）。
 */
@Composable
private fun ProfileTabs(
    palette: ProfilePalette,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        TabBar(
            selectedIndex = selected,
            onSelect = onSelect,
            containerColor = palette.card,
            indicatorColor = palette.accentContainer,
            selectedContentColor = palette.accent,
            unselectedContentColor = palette.tertiary,
            hoveredContentColor = palette.accent,
        ) {
            Tab(
                index = 0,
                text = stringResource(R.string.profile_tab_history),
                icon = Icons.Outlined.History,
            )
            Tab(
                index = 1,
                text = stringResource(R.string.profile_tab_favorite),
                icon = Icons.Filled.Bookmark,
            )
        }
    }
}
