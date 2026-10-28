package com.liomix.mikomi.feature.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.ErrorState
import com.liomix.mikomi.core.ui.component.SkeletonBox
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.hazeSource

/** 头部固定高度。 */
private val HEADER_HEIGHT = 420.dp

/** 内容区底部留白：避让右下角悬浮按钮组（收藏 48 + 间距 12 + 追番 56 + 外边距 24 + 余量）。 */
internal val CONTENT_BOTTOM_SPACER = 156.dp

/** 收藏按钮直径（比追番按钮更收，形成主次）。 */
private val FAVORITE_BUTTON_SIZE = 48.dp

/**
 * 详情页。
 *
 * 布局分三层，纵向互不影响：
 * 1. 头部（封面 + 标题 + 信息）固定不动；
 * 2. TabBar 固定；
 * 3. 内容区占满剩余高度，**每个 Tab 拥有自己的滚动状态**，
 *    上下滑动只滚动当前 Tab 的内容，不会带动头部或整个页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    uiState: DetailUiState,
    creditsState: AnimeCreditsUiState,
    creditDetailState: CreditDetailUiState,
    creditCommentsState: CreditCommentsUiState,
    teasingState: TeasingUiState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onLoadCredits: () -> Unit,
    onCharacterClick: (Int) -> Unit,
    onStaffClick: (Int) -> Unit,
    onDismissCreditDetail: () -> Unit,
    onLoadTeasing: () -> Unit,
    onLoadMoreTeasing: () -> Unit,
    onRetryTeasing: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // 头部封面直接顶到屏幕最上方（状态栏之下由 header 自行留白），insets 在页面内自行处理。
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        when (uiState) {
            // 加载态用骨架屏而不是空白页：从列表点进来的首帧若渲染空白页，浅色主题下就是
            // 整屏发白的「白屏闪一下」；骨架屏与真实布局同构，转场过程中也不会突兀。
            DetailUiState.Loading -> DetailLoadingSkeleton(
                modifier = Modifier.padding(innerPadding),
            )

            DetailUiState.Error -> ErrorState(
                message = stringResource(R.string.detail_load_failed),
                modifier = Modifier.padding(innerPadding),
            )

            is DetailUiState.Success -> DetailContent(
                anime = uiState.anime,
                creditsState = creditsState,
                teasingState = teasingState,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onLoadCredits = onLoadCredits,
                onLoadTeasing = onLoadTeasing,
                onLoadMoreTeasing = onLoadMoreTeasing,
                onRetryTeasing = onRetryTeasing,
                onCharacterClick = onCharacterClick,
                onStaffClick = onStaffClick,
                onPlayClick = onPlayClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // 主创详情底部弹层。
    if (creditDetailState != CreditDetailUiState.Hidden) {
        CreditDetailSheet(
            detailState = creditDetailState,
            commentsState = creditCommentsState,
            onDismiss = onDismissCreditDetail,
        )
    }
}

@Composable
private fun DetailContent(
    anime: Anime,
    creditsState: AnimeCreditsUiState,
    teasingState: TeasingUiState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onLoadCredits: () -> Unit,
    onLoadTeasing: () -> Unit,
    onLoadMoreTeasing: () -> Unit,
    onRetryTeasing: () -> Unit,
    onCharacterClick: (Int) -> Unit,
    onStaffClick: (Int) -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(DetailTabType.OVERVIEW) }
    // 毛玻璃：内容作 source，顶栏作 child。
    val hazeState = remember { HazeState() }

    // 每个 Tab 独立的滚动状态，切 Tab 后各自位置保留、互不干扰。
    val overviewScroll = rememberScrollState()
    val creditsScroll = rememberScrollState()
    val commentScroll = rememberScrollState()
    val teasingListState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
        ) {
            // 头部：固定不随内容滚动。
            DetailHeader(
                anime = anime,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT),
            )
            DetailTabBar(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it },
            )
            // 内容区：占满剩余高度，内部各自滚动。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (selectedTab) {
                    DetailTabType.OVERVIEW -> ScrollableTabPane(scrollState = overviewScroll) {
                        OverviewTab(anime = anime)
                    }

                    DetailTabType.CREDITS -> ScrollableTabPane(scrollState = creditsScroll) {
                        CreditsTab(
                            creditsState = creditsState,
                            onLoadCredits = onLoadCredits,
                            onCharacterClick = onCharacterClick,
                            onStaffClick = onStaffClick,
                        )
                    }

                    DetailTabType.TEASING -> TeasingTab(
                        state = teasingState,
                        listState = teasingListState,
                        onLoadTeasing = onLoadTeasing,
                        onLoadMore = onLoadMoreTeasing,
                        onRetry = onRetryTeasing,
                    )
                }
            }
        }

        // 顶栏：常驻毛玻璃，让状态栏图标在封面图上依旧清晰。
        TopBar(hazeState = hazeState)

        // 右下角悬浮按钮组：收藏（圆形星标）+ 追番（扩展按钮）。
        BottomActions(
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onFollowClick = onPlayClick,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/**
 * 加载骨架：与真实布局同构（头部封面 + 右侧信息行 + 正文行）。
 *
 * 详情数据来自 Room，通常一两帧就到；这里只负责让这一两帧不出现「整屏空白」。
 */
@Composable
private fun DetailLoadingSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
        ) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(26.dp),
                shape = RoundedCornerShape(13.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2f / 3f),
                    shape = RoundedCornerShape(14.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    repeat(3) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SkeletonBox(
                                modifier = Modifier.size(width = 56.dp, height = 14.dp),
                                shape = RoundedCornerShape(7.dp),
                            )
                            SkeletonBox(
                                modifier = Modifier.size(width = 84.dp, height = 20.dp),
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(4) { index ->
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 3) 0.6f else 1f)
                        .height(14.dp),
                    shape = RoundedCornerShape(7.dp),
                )
            }
        }
    }
}

/** 右下角按钮组：收藏在上、追番在下，纵向右对齐。 */@Composable
private fun BottomActions(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(end = 20.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FavoriteButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
        )
        FollowButton(onClick = onFollowClick)
    }
}

/** 收藏按钮：圆形星标，已收藏时填充主色。 */
@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentDescriptionRes = if (isFavorite) {
        R.string.detail_remove_favorite
    } else {
        R.string.detail_add_favorite
    }
    Surface(
        onClick = onClick,
        modifier = modifier.size(FAVORITE_BUTTON_SIZE),
        shape = CircleShape,
        color = if (isFavorite) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (isFavorite) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = stringResource(contentDescriptionRes),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** 追番按钮：扩展 FAB（播放图标 + 文案），对齐 Dart 版 PlayButton。 */
@Composable
private fun FollowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        text = {
            Text(
                text = stringResource(R.string.detail_follow),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
    )
}

/** 普通内容 Tab 的滚动容器（概述 / 主创等非懒加载内容）。 */
@Composable
private fun ScrollableTabPane(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = CONTENT_BOTTOM_SPACER),
    ) {
        content()
    }
}

@Composable
private fun DetailTabBar(
    selectedTab: DetailTabType,
    onSelect: (DetailTabType) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {},
        ) {
            DetailTabType.entries.forEachIndexed { index, tab ->
                val selected = selectedTab == tab
                val textColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "tabTextColor",
                )
                Tab(
                    selected = selected,
                    onClick = { onSelect(tab) },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = textColor,
                        )
                    },
                )
            }
        }
    }
}

/** 顶栏毛玻璃：只覆盖状态栏高度（头部固定后不会有内容从顶栏下方穿过）。 */
@Composable
private fun TopBar(hazeState: HazeState) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusBarHeight)
            .hazeChild(state = hazeState),
    )
}

/** 详情页三个 Tab。 */
internal enum class DetailTabType(
    val labelRes: Int,
) {
    OVERVIEW(com.liomix.mikomi.feature.detail.R.string.detail_tab_overview),
    CREDITS(com.liomix.mikomi.feature.detail.R.string.detail_tab_credits),
    TEASING(com.liomix.mikomi.feature.detail.R.string.detail_tab_teasing),
}
