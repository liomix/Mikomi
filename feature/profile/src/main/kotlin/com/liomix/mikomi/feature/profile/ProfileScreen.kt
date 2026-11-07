package com.liomix.mikomi.feature.profile

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.icon.MikomiIcons
import com.liomix.mikomi.core.designsystem.spec.MikomiWindowSpec
import com.liomix.mikomi.core.designsystem.theme.MikomiMedia
import com.liomix.mikomi.core.designsystem.theme.MikomiMotion
import com.liomix.mikomi.core.designsystem.theme.MikomiRadius
import com.liomix.mikomi.core.designsystem.theme.MikomiSizes
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.designsystem.theme.MikomiTextStyles
import com.liomix.mikomi.core.ui.component.base.MikomiIcon
import com.liomix.mikomi.core.ui.component.base.Tab
import com.liomix.mikomi.core.ui.component.base.TabBar
import com.liomix.mikomi.core.ui.component.layout.LocalBottomBarSpace
import com.liomix.mikomi.core.ui.window.LocalMikomiWindowSize
import com.liomix.mikomi.core.ui.window.rememberMikomiBottomInset
import com.liomix.mikomi.core.ui.window.rememberMikomiTopInset
import com.liomix.mikomi.feature.profile.component.ProfileTabContent
import com.liomix.mikomi.feature.profile.component.rememberProfileTabContentState
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
    onNotificationsClick: () -> Unit = {},
) {
    val palette = rememberProfilePalette()
    val density = LocalDensity.current
    val statusBar = rememberMikomiTopInset()
    val barHeight = statusBar + TopBarHeight

    // 头部高度：头像 96 + 名字/简介与留白 85；完全收起时与顶栏留 16dp 呼吸。
    val headerHeight = HeaderAvatarSize + 85.dp
    val maxCollapsePx = with(density) { (headerHeight - CollapsedBottomGap).toPx() }

    var collapsePx by remember { mutableFloatStateOf(0f) }
    val collapseFraction: () -> Float = { (collapsePx / maxCollapsePx).coerceIn(0f, 1f) }

    // 下拉两段式（对应 Telegram 的 expandProgress + expandAnimator）：
    // 1) 拖拽段：头像**保持圆形**，从 96dp 一路长到 148dp，拖到临界点就"上膛"；
    // 2) 上膛后：长震一下，再由 snap 用 260ms 把圆撑成满宽照片（盖住动作卡，约半屏）。
    var pullPx by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val pullThresholdPx = with(density) { PullThreshold.toPx() }
    val dragFraction: () -> Float = { (pullPx / pullThresholdPx).coerceIn(0f, 1f) }

    // armed 一变就自动跑展开/收起动画；这个值只在布局/绘制阶段读，不逐帧重组。
    val snapState = animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = tween(durationMillis = SnapMillis, easing = MikomiMotion.Curve.Standard),
        label = "profileSnap",
    )
    val snap: () -> Float = { snapState.value }
    // 头部占位只在上膛后被撑开：拖拽段里内容不跟着动，免得圆还没长大下面先空一条。
    val pullLayout: () -> Float = { pullPx * snapState.value }

    // 撑满之后还能再往下拉一点点：只把照片往下拉长（橡皮筋），松手弹回，给半屏一点"动感"。
    var stretchPx by remember { mutableFloatStateOf(0f) }
    val maxStretchPx = with(density) { MaxStretch.toPx() }

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

    // 上膛那一下长震：先震、再撑满（Telegram 在 expandProgress >= 0.33 时做的是同一件事）。
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // 兜底回弹：慢慢松手时系统不派发 fling，下拉量会停在原地。
    // 一旦卡片内列表不再处于滚动状态（松手且惯性结束），就把下拉量弹回 0。
    // 松手后头部只有两个落点：过半就完全收进顶栏，否则完全回到原位——
    // 不允许停在半路。慢慢松手时系统不派发 fling，全靠这条兜底。
    val settleCollapse: suspend () -> Unit = {
        val target = if (collapsePx > maxCollapsePx / 2f) maxCollapsePx else 0f
        if (collapsePx != target) {
            animate(collapsePx, target, animationSpec = CollapseSpring) { v, _ -> collapsePx = v }
        }
    }

    LaunchedEffect(contentListState) {
        snapshotFlow { contentListState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    if (armed || pullPx > 0f || stretchPx > 0f) {
                        armed = false
                        // 并发收：橡皮筋和下拉量一起回到 0，头像不会在放大态多停一截。
                        coroutineScope {
                            if (stretchPx > 0f) {
                                launch {
                                    animate(stretchPx, 0f, animationSpec = StretchSpring) { v, _ -> stretchPx = v }
                                }
                            }
                            if (pullPx > 0f) {
                                launch {
                                    animate(pullPx, 0f, animationSpec = tween(SnapMillis)) { v, _ -> pullPx = v }
                                }
                            }
                        }
                    }
                    settleCollapse()
                }
            }
    }

    // 手势接力：上滑先收回下拉展开、再收头部，最后交给卡片内部列表；
    // 下滑时列表到顶后先把头部放平，再继续往下拉——拉过临界点就自动撑满。
    val collapseConnection = remember(maxCollapsePx, pullThresholdPx) {
        object : NestedScrollConnection {
            /** 松手收起：解除上膛，下拉量归零（snap 由 animateFloatAsState 自己回落）。 */
            private suspend fun release() {
                armed = false
                // 两条动画必须并发：pullPx 决定头像大小，排在 stretch 后面收会让头像
                // 在 148dp 上多停三四百毫秒（"大头停一会儿再缩回去"）。
                coroutineScope {
                    if (stretchPx > 0f) {
                        launch {
                            animate(stretchPx, 0f, animationSpec = StretchSpring) { v, _ -> stretchPx = v }
                        }
                    }
                    if (pullPx > 0f) {
                        launch {
                            animate(pullPx, 0f, animationSpec = tween(SnapMillis)) { v, _ -> pullPx = v }
                        }
                    }
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                // 已撑开：先把手拉出来的那点橡皮筋收回去，其余手势吃掉，等松手统一收起。
                if (armed) {
                    val used = (-available.y).coerceAtMost(stretchPx)
                    if (used > 0f) stretchPx -= used
                    return Offset(0f, available.y)
                }

                var left = -available.y
                // 1) 先收回下拉（圆缩回去）
                if (pullPx > 0f) {
                    val used = left.coerceAtMost(pullPx)
                    pullPx -= used
                    left -= used
                }
                // 2) 再折叠头部
                if (left > 0f) {
                    val room = maxCollapsePx - collapsePx
                    if (room > 0f) {
                        val used = left.coerceAtMost(room)
                        collapsePx += used
                        left -= used
                    }
                }
                // left 是没吃掉的量，消费量 = available.y + left（负值 = 向上）
                return Offset(0f, available.y + left)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f) return Offset.Zero
                if (armed) {
                    // 已在撑满态：继续下拉只是把照片拉长一点（阻尼 0.35，最多 64dp）
                    val room = maxStretchPx - stretchPx
                    if (room > 0f) {
                        stretchPx = (stretchPx + available.y * StretchDamping).coerceAtMost(maxStretchPx)
                    }
                    return Offset(0f, available.y)
                }

                var left = available.y
                // 1) 先把折叠的头部放平
                if (collapsePx > 0f) {
                    val used = left.coerceAtMost(collapsePx)
                    collapsePx -= used
                    left -= used
                }
                // 2) 头部放平后继续下拉 → 圆继续变大；到临界点就上膛
                if (left > 0f && collapsePx <= 0f) {
                    pullPx = (pullPx + left * PullDamping).coerceAtMost(pullThresholdPx)
                    if (pullPx >= pullThresholdPx) armed = true
                }
                // 全部吃掉，避免列表越界回弹与下拉互相打架。
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (armed || available.y >= 0f || pullPx > 0f) {
                    release()
                    return Velocity(0f, available.y)
                }
                if (collapsePx >= maxCollapsePx) return Velocity.Zero
                animate(
                    initialValue = collapsePx,
                    targetValue = maxCollapsePx,
                    initialVelocity = -available.y,
                ) { value, _ -> collapsePx = value }
                return Velocity(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (armed) {
                    release()
                    return Velocity(0f, available.y)
                }
                if (available.y > 0f && collapsePx > 0f) {
                    animate(
                        initialValue = collapsePx,
                        targetValue = 0f,
                        initialVelocity = available.y,
                    ) { value, _ -> collapsePx = value }
                }
                release()
                return Velocity(0f, available.y)
            }
        }
    }

    // 没有外层滚动容器，所以直接拖头部区域也能收起 / 下拉。
    val headerDragState = rememberDraggableState { delta ->
        if (armed) return@rememberDraggableState
        if (delta > 0f && collapsePx <= 0f) {
            pullPx = (pullPx + delta * PullDamping).coerceIn(0f, pullThresholdPx)
            if (pullPx >= pullThresholdPx) armed = true
        } else {
            collapsePx = (collapsePx - delta).coerceIn(0f, maxCollapsePx)
        }
    }

    // 宽屏（≥840dp）收成可读列并居中：动作卡、标签栏、历史/收藏网格一起收。
    //
    // 不收列的话，1120dp 的平板上三个动作按钮各占 300dp 宽，昵称与简介居中在一片
    // 空旷里 —— 元素本身没错，是「同一行该有多少内容」这件事没有上限。
    //
    // 收列用外层 Box + contentAlignment，而不是自己算左右偏移：照片层与折叠头
    // 都按 `constraints.maxWidth` 算中心，把约束收窄它们就自动跟着收，几何代码一行不改。
    val wide = LocalMikomiWindowSize.current.isExpandedWidth
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .then(
                    if (wide) {
                        Modifier.widthIn(max = MikomiWindowSpec.ContentMaxWidth)
                    } else {
                        Modifier
                    },
                )
                .fillMaxSize(),
        ) {
            // 头部占位随折叠逐帧收缩，下面的内容自然上移。
            // 不能再用「整列 offset 上移」：Column 会把最后一个子项的高度夹在「剩余高度」以内，
            // 上移多少，卡片下沿就跟着抬起多少，屏幕底部会露出一条页面背景（截图里那块空白）。
            val headerHeightPx = with(density) { headerHeight.toPx() }

            // 卡片下沿停在悬浮导航栏上沿 14dp：底栏占位 = 系统导航栏 inset + 栏体 + 底部外边距。
            // 栏体那部分是壳下发的（LocalBottomBarSpace），这里不关心底部浮着什么。
            val navBarInset = rememberMikomiBottomInset()
            val contentBottomSpace = navBarInset + LocalBottomBarSpace.current + ContentBottomGap
            val barHeightPx = with(density) { barHeight.toPx() }
            val actionCardHeightPx = with(density) { ActionCardHeight.toPx() }
            val photoInsetPx = with(density) { PhotoBottomInset.toPx() }

            // 上膛后动作卡整体收掉（高度压到 0 + 淡出），标签栏跟着顶上来，
            // 于是撑满态只剩「照片 + 昵称 + 简介」，下面的标签/内容不会被挡住。
            val cardsHeightPx: () -> Float = { actionCardHeightPx * (1f - snapState.value) }
            val photoBottomPx: () -> Float = {
                barHeightPx + headerHeightPx - collapsePx + pullLayout() + cardsHeightPx() +
                    photoInsetPx + stretchPx
            }

            // 照片层画在**内容层之下**：下拉撑开时它会盖住动作卡所在的整块区域，
            // 动作卡再画在照片上（半透明），标签与内容卡片在照片下沿之后。
            ProfilePhoto(
                progress = collapseFraction,
                dragT = dragFraction,
                snap = snap,
                photoBottom = photoBottomPx,
                restCenter = Offset(
                    x = constraints.maxWidth / 2f,
                    y = barHeightPx + with(density) { (HeaderAvatarSize / 2).toPx() },
                ),
                collapsedCenter = Offset(
                    x = with(density) { (CollapsedInset + CollapsedAvatarSize / 2).toPx() },
                    y = with(density) { (statusBar + TopBarHeight / 2).toPx() },
                ),
            )

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
                            // 折叠收缩 + 下拉拉长：两个方向共用同一块头部占位，
                            // 下拉时下面的动作卡/标签/卡片被整体推下去。
                            val h = (headerHeightPx - collapsePx + pullLayout() + stretchPx)
                                .coerceIn(0f, headerHeightPx + pullThresholdPx + maxStretchPx)
                                .roundToInt()
                            val placeable = measurable.measure(
                                Constraints.fixed(constraints.maxWidth, h),
                            )
                            layout(placeable.width, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                        .draggable(
                            state = headerDragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                armed = false
                                if (pullPx > 0f) {
                                    animate(pullPx, 0f, animationSpec = tween(SnapMillis)) { v, _ -> pullPx = v }
                                }
                                settleCollapse()
                            },
                        ),
                )

                ActionCardsRow(
                    palette = palette,
                    expand = snap,
                    modifier = Modifier.layout { measurable, constraints ->
                        // 高度随上膛进度压到 0：标签栏跟着往上顶，撑满态只留照片+名字。
                        val h = cardsHeightPx().roundToInt().coerceAtLeast(0)
                        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                        layout(placeable.width, h) { placeable.place(0, 0) }
                    },
                    onThemeClick = onThemeClick,
                    onEditInfo = onEditInfo,
                    onSettingsClick = onSettingsClick,
                )

                Spacer(modifier = Modifier.height(MikomiSpacing.Room))

                ProfileTabs(
                    palette = palette,
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(MikomiSpacing.Md))

                ProfileTabContent(
                    state = contentState,
                    listState = contentListState,
                    cardColor = palette.card,
                    dividerColor = palette.divider,
                    skeletonColor = palette.skeleton,
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
            ProfileNameOverlay(
                palette = palette,
                progress = collapseFraction,
                dragT = dragFraction,
                snap = snap,
                photoBottom = photoBottomPx,
                nameRestCenterY = barHeightPx + with(density) { (HeaderAvatarSize + 14.dp + 11.dp).toPx() },
                nameTargetCenterY = with(density) { (statusBar + TopBarHeight / 2).toPx() },
            )

            ProfileTopBar(
                palette = palette,
                expand = snap,
                height = barHeight,
                onNotificationsClick = onNotificationsClick,
            )
        }
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

/** 动作卡高度，同时决定照片下沿的位置（照片底边 = 卡片下沿）。 */
/** 收拢态姓名/简介的左右让位：照片两侧要留出圆形头像的收口，文字不能压到弧上。 */
private val CollapsedTextInset = 56.dp

private val ActionCardHeight = 54.dp

/** 展开态下名字块底边与照片下沿的距离。 */
private val NameBottomInset = 16.dp

/** 展开态下动作卡与照片下沿之间的留白（截图里按钮下面还露出一条照片）。 */
private val PhotoBottomInset = 12.dp

/** 下拉多少行程算"上膛"：过这个点就长震 + 自动撑满半屏。 */
private val PullThreshold = 84.dp

/** 拖拽段头像能长到的最大圆形直径（继续拉就该撑开了）。 */
private val MaxCircleSize = 148.dp

/** 上膛后撑开 / 松手收起的时长（Telegram 是 250ms 的 EASE_OUT_QUINT，对应 `Medium`）。 */
private const val SnapMillis = MikomiMotion.Duration.Medium

/** 松手后头部归位的弹簧：只落到「完全收起」或「完全展开」两端。 */
private val CollapseSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 520f)

/** 撑满后还能再往下拉出的最大距离（照片被拉长的上限）。 */
private val MaxStretch = 64.dp

/** 这一段的下拉阻尼：手指走 1px，照片只长 0.35px，越拉越沉。 */
private const val StretchDamping = 0.35f

/** 橡皮筋回弹：略欠阻尼，松手会轻轻弹一下。 */
private val StretchSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)

/** 下拉阻尼：手指走 1px，头像只长 0.85px。 */
private const val PullDamping = 0.85f

/** 横向滑动切换标签的触发距离。 */
private val SwipeThreshold = 72.dp

/** 标签数量（历史 / 收藏）。 */
private const val TabCount = 2

/* ---------------------------------------------------------------- 顶栏 */

@Composable
private fun ProfileTopBar(
    palette: ProfilePalette,
    expand: () -> Float,
    height: Dp,
    onNotificationsClick: () -> Unit,
) {
    // 展开后照片盖住顶栏，图标得转白；按 4 档量化，避免逐帧重组。
    val tintStep = remember { derivedStateOf { (expand() * 4f).roundToInt() / 4f } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        IconButton(
            onClick = onNotificationsClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = MikomiSpacing.Xs, bottom = MikomiSpacing.Xs),
        ) {
            MikomiIcon(
                icon = MikomiIcons.Email,
                contentDescription = stringResource(R.string.profile_notifications_entry),
                tint = lerp(palette.title, MikomiMedia.content, tintStep.value.coerceIn(0f, 1f)),
                size = MikomiSizes.IconMd,
            )
        }
    }
}

/* ---------------------------------------------------------------- 照片层 */

/**
 * 照片层：画在内容层**之下**，只有一张图。
 *
 * 三段几何：
 * - **收起段**（[progress]）：96dp 圆居中 → 34dp 小圆进顶栏左侧；
 * - **拖拽段**（[dragT]）：仍是圆，以圆心为中心向四周放大到 148dp；
 * - **上膛段**（[snap]）：圆 → 满宽矩形，顶边顶到 0（盖住状态栏）、底边到 [photoBottom]。
 */
@Composable
private fun ProfilePhoto(
    progress: () -> Float,
    dragT: () -> Float,
    snap: () -> Float,
    photoBottom: () -> Float,
    restCenter: Offset,
    collapsedCenter: Offset,
) {
    val density = LocalDensity.current
    val baseSizePx = with(density) { HeaderAvatarSize.toPx() }
    val collapsedSizePx = with(density) { CollapsedAvatarSize.toPx() }
    val maxCircleSizePx = with(density) { MaxCircleSize.toPx() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()

        // 几何只在布局/绘制阶段求值，不进组合。
        fun geometry(): AvatarGeometry = avatarGeometry(
            collapse = progress(),
            dragT = dragT(),
            snap = snap(),
            photoBottom = photoBottom(),
            baseSize = baseSizePx,
            collapsedSize = collapsedSizePx,
            maxCircleSize = maxCircleSizePx,
            width = widthPx,
            restCenter = restCenter,
            collapsedCenter = collapsedCenter,
        )

        Box(
            modifier = Modifier
                .offset {
                    val g = geometry()
                    IntOffset(g.left.roundToInt(), g.top.roundToInt())
                }
                // 尺寸在布局阶段给：用 graphicsLayer 缩放会把图拉变形，ContentScale.Crop 就失效了。
                .layout { measurable, _ ->
                    val g = geometry()
                    val placeable = measurable.measure(
                        Constraints.fixed(g.width.roundToInt(), g.height.roundToInt()),
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    shape = RoundedCornerShape((geometry().radius / density.density).dp)
                    clip = true
                },
        ) {
            ProfileAvatar()
        }
    }
}

/** 头像在某一帧的落位（px）。 */
private class AvatarGeometry(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
)

/**
 * 头像几何：圆 → 满宽矩形。
 *
 * - 收起段：96dp 圆（居中、顶边贴顶栏下缘）→ 34dp 小圆（顶栏左侧），线性插值；
 * - 拖拽段：仍是圆，以圆心为中心向四周均匀放大到 [maxCircleSize]；
 * - 上膛段：按 `snap` 把圆插值成满宽矩形——顶边到 0（盖住状态栏）、底边到 [photoBottom]、
 *   圆角收到 0。
 */
private fun avatarGeometry(
    collapse: Float,
    dragT: Float,
    snap: Float,
    photoBottom: Float,
    baseSize: Float,
    collapsedSize: Float,
    maxCircleSize: Float,
    width: Float,
    restCenter: Offset,
    collapsedCenter: Offset,
): AvatarGeometry {
    val p = collapse.coerceIn(0f, 1f)
    val collapsedSide = baseSize + (collapsedSize - baseSize) * p
    val centerX = restCenter.x + (collapsedCenter.x - restCenter.x) * p
    val centerY = restCenter.y + (collapsedCenter.y - restCenter.y) * p

    // 拖拽段：圆，向四周均匀放大
    val d = dragT.coerceIn(0f, 1f)
    val side = collapsedSide + (maxCircleSize - collapsedSide) * d
    val circleLeft = centerX - side / 2f
    val circleTop = centerY - side / 2f

    // 上膛段：圆 → 满宽矩形
    val s = snap.coerceIn(0f, 1f)
    return AvatarGeometry(
        left = circleLeft + (0f - circleLeft) * s,
        top = circleTop + (0f - circleTop) * s,
        width = side + (width - side) * s,
        height = side + (photoBottom - side) * s,
        radius = (side / 2f) * (1f - s),
    )
}

/* ---------------------------------------------------------------- 名字层 */

/**
 * 名字/简介层：画在照片与内容层**之上**。
 *
 * - 收起段：居中版跟着头像上移到顶栏标题中线；
 * - 拖拽段：头像变大的同时淡出居中版（别让深色字压在头像上）；
 * - 撑满段：左下角的大号白字淡入，压在照片下沿（对齐 TG 满屏态）。
 *
 * 所有 alpha / 位移都在绘制或布局阶段读状态，下拉过程不重组。
 */
@Composable
private fun ProfileNameOverlay(
    palette: ProfilePalette,
    progress: () -> Float,
    dragT: () -> Float,
    snap: () -> Float,
    photoBottom: () -> Float,
    nameRestCenterY: Float,
    nameTargetCenterY: Float,
) {
    val density = LocalDensity.current
    val nameTop = with(density) { nameRestCenterY.toDp() } - 11.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()

        // 居中版：常态 + 收起态
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = nameTop)
                .graphicsLayer {
                    translationY = (nameTargetCenterY - nameRestCenterY) * progress()
                    alpha = (1f - dragT() * 1.6f).coerceIn(0f, 1f) *
                        (1f - snap().coerceIn(0f, 1f))
                },
        ) {
            Text(
                text = stringResource(R.string.profile_name),
                color = palette.title,
                style = MikomiTextStyles.DisplayName,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = CollapsedTextInset),
            )
            Spacer(modifier = Modifier.height(MikomiSpacing.Xxs))
            Text(
                text = stringResource(R.string.profile_bio),
                color = palette.secondary,
                style = MikomiTextStyles.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = CollapsedTextInset)
                    .graphicsLayer { alpha = (1f - progress() / 0.4f).coerceIn(0f, 1f) },
            )
        }

        // 展开版：名字贴着照片下沿、靠左
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = MikomiSpacing.Lg)
                .offset {
                    val bottom = photoBottom() - NameBottomInset.toPx()
                    IntOffset(0, -(heightPx - bottom).roundToInt())
                }
                .graphicsLayer { alpha = snap().coerceIn(0f, 1f) },
        ) {
            Text(
                text = stringResource(R.string.profile_name),
                color = MikomiMedia.content,
                style = MikomiTextStyles.TopBarTitle,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(MikomiSpacing.Xxs))
            Text(
                text = stringResource(R.string.profile_bio),
                color = MikomiMedia.contentSecondary,
                style = MikomiTextStyles.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    expand: () -> Float,
    onThemeClick: () -> Unit,
    onEditInfo: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MikomiSpacing.Tight),
        modifier = modifier
            .fillMaxWidth()
            // 撑满态整排消失：只留昵称与简介。
            .graphicsLayer { alpha = (1f - expand()).coerceIn(0f, 1f) }
            .padding(horizontal = MikomiSpacing.Gutter),
    ) {
        ActionCard(
            palette = palette,
            icon = MikomiIcons.Palette,
            label = stringResource(R.string.profile_theme_entry),
            onClick = onThemeClick,
            modifier = Modifier.weight(1f),
        )
        ActionCard(
            palette = palette,
            icon = MikomiIcons.Edit,
            label = stringResource(R.string.profile_action_edit_info),
            onClick = onEditInfo,
            modifier = Modifier.weight(1f),
        )
        ActionCard(
            palette = palette,
            icon = MikomiIcons.Settings,
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
            .height(ActionCardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(MikomiRadius.Xl))
            .background(palette.card)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        MikomiIcon(
            icon = icon,
            contentDescription = null,
            tint = palette.title,
            size = MikomiSizes.IconSm,
        )
        Spacer(modifier = Modifier.height(MikomiSpacing.Xxs))
        Text(
            text = label,
            color = palette.title,
            style = MikomiTextStyles.IconLabel,
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
                icon = MikomiIcons.History,
            )
            Tab(
                index = 1,
                text = stringResource(R.string.profile_tab_favorite),
                icon = MikomiIcons.Bookmark,
            )
        }
    }
}

/* ---------------------------------------------------------------- 配色 */

/**
 * 个人页配色：全部取自应用自己的主题（`MaterialTheme.colorScheme`），
 * 换主题色（蓝 / 粉 / 绿 / 紫）、切日夜都会自动跟着变。
 *
 * 角色映射：
 * - [background] 页面底色 → `background`
 * - [card] 卡片 / 胶囊底 → `surface`
 * - [title] / [value] 主文字 → `onSurface`
 * - [secondary] 次要文字（简介）→ `onSurfaceVariant`
 * - [tertiary] 弱化文字（未选中标签、空态）→ `onSurfaceVariant` 再淡一档
 * - [divider] 网格分隔线 → `outlineVariant` 淡化
 * - [skeleton] 占位骨架格 → `surfaceContainerHighest` 淡化
 * - [accent] 选中态文字 / 图标 → `primary`
 * - [accentContainer] 选中胶囊底 → `primaryContainer`
 */
private data class ProfilePalette(
    val background: Color,
    val card: Color,
    val title: Color,
    val value: Color,
    val secondary: Color,
    val tertiary: Color,
    val divider: Color,
    val skeleton: Color,
    val accent: Color,
    val accentContainer: Color,
)

@Composable
private fun rememberProfilePalette(): ProfilePalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { scheme.toProfilePalette() }
}

private fun ColorScheme.toProfilePalette(): ProfilePalette = ProfilePalette(
    background = background,
    card = surface,
    title = onSurface,
    value = onSurface,
    secondary = onSurfaceVariant,
    tertiary = onSurfaceVariant.copy(alpha = 0.78f),
    divider = outlineVariant.copy(alpha = 0.55f),
    skeleton = surfaceContainerHighest.copy(alpha = 0.6f),
    accent = primary,
    accentContainer = primaryContainer,
)
