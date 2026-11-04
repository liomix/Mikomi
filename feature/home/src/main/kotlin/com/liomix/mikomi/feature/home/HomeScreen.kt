package com.liomix.mikomi.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.AnimePosterCard
import com.liomix.mikomi.core.ui.component.ErrorState
import com.liomix.mikomi.core.ui.component.PosterCardSkeleton
import com.liomix.mikomi.core.ui.component.PosterSkeletonRow
import com.liomix.mikomi.core.ui.component.SkeletonBox
import com.liomix.mikomi.core.ui.component.SkeletonShimmer
import kotlinx.coroutines.delay

/** 网格最小列宽：手机竖屏约 3 列，宽屏自动增加列数（Dart 版为固定 3 列）。 */
private val HomeGridMinWidth = 100.dp

private val HomeGridColumns = GridCells.Adaptive(minSize = HomeGridMinWidth)

/** 网格行内 / 行间间距；分页骨架行必须用同一个值，否则列宽对不齐。 */
private val HomeGridSpacing = 12.dp

/** `pendingSize` 的哨兵值：当前没有等待中的翻页。 */
private const val NoPendingLoad = -1

/**
 * 请求结束、数据却没到时，骨架行最多再多留的时间。
 *
 * 正常情况 Room 的失效通知只需要一两帧（~30ms），这个宽限期只用来盖住那段空档；
 * 空页 / 请求失败时则保证骨架行一定会被撤掉，不会因为「条数永远不变」而永久挂着。
 */
private const val PendingGraceMillis = 300L

/**
 * 网格内容边距。
 *
 * 垂直节奏统一由网格的 [Arrangement.spacedBy] 提供（12dp），
 * 各区域组件不再自带区域间垂直 padding；此处仅为页面顶边
 * 补状态栏安全区 + 少量呼吸空间。
 *
 * 用 remember 固定实例：`PaddingValues` 是接口类型（Compose 视为不稳定），
 * 每次重组都新建会让 `LazyVerticalGrid` 无法跳过、白跑一次重排；
 * 下拉刷新、加载更多这些每页都会翻的状态更是每次都要赔一遍。
 */
@Composable
private fun rememberHomeContentPadding(): PaddingValues {
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return remember(statusBarInset) {
        PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = statusBarInset + 4.dp,
            bottom = 112.dp,
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 3

/**
 * 主页下拉刷新容器。
 *
 * Material 默认指示器是固定的灰底（surfaceContainerHigh）+ primary 箭头，
 * 底圈不随主题配色变化。这里显式指定容器与内容色，让整个指示器都受主题色支配：
 * 圆底取 primaryContainer、箭头取 onPrimaryContainer，切换配色或日夜模式都会同步。
 */
@Composable
private fun HomePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        content = content,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    isRefreshing: Boolean,
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onActionClick: (HomeQuickAction) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        // 骨架屏也支持下拉刷新：首次加载慢/失败时用户可主动触发重试。
        HomeUiState.Loading -> HomePullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
        ) {
            HomeSkeleton(
                onSearchClick = onSearchClick,
                onProfileClick = onProfileClick,
                onActionClick = onActionClick,
                modifier = Modifier.fillMaxSize(),
            )
        }

        is HomeUiState.Error -> ErrorState(
            message = uiState.message.ifEmpty { stringResource(R.string.home_load_failed) },
            onRetry = onRefresh,
            modifier = modifier,
        )

        is HomeUiState.Success -> HomeContent(
            banners = uiState.banners,
            anime = uiState.anime,
            isRefreshing = isRefreshing,
            isLoadingMore = uiState.isLoadingMore,
            hasMore = uiState.hasMore,
            onAnimeClick = onAnimeClick,
            onSearchClick = onSearchClick,
            onProfileClick = onProfileClick,
            onActionClick = onActionClick,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeSkeleton(
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onActionClick: (HomeQuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchHint = stringResource(R.string.home_search_hint)
    val profileContentDescription = stringResource(R.string.home_profile)

    // 整屏骨架共用一条扫光时间线：十几块 SkeletonBox 各自按帧驱动的话，
    // 同一帧里会有十几条协程同时写入、失效、重绘。见 SkeletonShimmer 注释。
    SkeletonShimmer {
        LazyVerticalGrid(
            columns = HomeGridColumns,
            modifier = modifier.fillMaxSize(),
            contentPadding = rememberHomeContentPadding(),
            horizontalArrangement = Arrangement.spacedBy(HomeGridSpacing),
            verticalArrangement = Arrangement.spacedBy(HomeGridSpacing),
        ) {
            item(key = "topBar", span = { GridItemSpan(maxLineSpan) }) {
                HomeTopBar(
                    onSearchClick = onSearchClick,
                    onProfileClick = onProfileClick,
                    searchHint = searchHint,
                    profileContentDescription = profileContentDescription,
                )
            }
            item(key = "bannerSkeleton", span = { GridItemSpan(maxLineSpan) }) {
                HomeBannerSkeleton()
            }
            item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
                HomeActionTabs(onActionClick = onActionClick)
            }
            items(
                count = 12,
                key = { "skeleton_$it" },
                contentType = { "skeleton" },
            ) {
                PosterCardSkeleton()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    banners: List<Anime>,
    anime: List<Anime>,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onActionClick: (HomeQuickAction) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchHint = stringResource(R.string.home_search_hint)
    val profileContentDescription = stringResource(R.string.home_profile)

    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            // 首帧布局尚未完成时 visibleItemsInfo 为空：此时必须判 false。
            // 否则 lastVisible(-1) >= totalItemsCount(0) - 3 恒成立，会在进入首页的瞬间
            // 自动拉第二页，第二页数据按热度并入后已显示的封面会整体重排。
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    // 上一次发起翻页时列表的条数；[NoPendingLoad] 表示当前没有在等数据落库。
    //
    // 为什么需要它：`isLoadingMore` 由 `_paging` 翻转，新卡片由 Room Flow 送达，
    // 是**两条独立的流**。请求返回后 `isLoadingMore` 先变 false，Room 的失效通知可能
    // 晚一两帧才把新列表送到 UI —— 中间那一两帧就是「骨架行已撤、新卡还没到」的空档。
    // 所以在 UI 层记住发起时的条数，条数真的变了才算数据到位。
    var pendingSize by remember { mutableIntStateOf(NoPendingLoad) }

    LaunchedEffect(shouldLoadMore, isLoadingMore, hasMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore) {
            pendingSize = anime.size
            onLoadMore()
        }
    }

    // 条数变了 = 新一页已经到了（刷新换一批数据同样算），立刻收掉骨架行。
    LaunchedEffect(anime.size) {
        if (pendingSize != NoPendingLoad && anime.size != pendingSize) {
            pendingSize = NoPendingLoad
        }
    }

    // 兜底：请求已结束而条数始终没变（空页 / 请求失败），最多再等一瞬就撤。
    // 没有这层，上面那条「等条数变化」在永远不变的情况下会让骨架行永久留在底部。
    LaunchedEffect(isLoadingMore, pendingSize) {
        if (isLoadingMore || pendingSize == NoPendingLoad) return@LaunchedEffect
        delay(PendingGraceMillis)
        if (pendingSize != NoPendingLoad && anime.size == pendingSize) {
            pendingSize = NoPendingLoad
        }
    }

    // 请求在途，或请求已结束但数据还没到，都算「正在补内容」。
    val pagingPending = pendingSize != NoPendingLoad && anime.size == pendingSize

    // 轮播图滚出屏幕后停止自动翻页。
    // 自动翻页会切换封面图 → 触发新的图片请求与解码；用户正在滚列表时，
    // 这等于让一个不可见的组件在后台抢解码线程和带宽，是滚动掉帧的常见来源。
    // derivedStateOf 只在 true/false 翻转时通知，滚动过程本身不会引起重组。
    val bannerVisible by remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.any {
                it.key == "banner" || it.key == "bannerSkeleton"
            }
        }
    }

    // 下拉刷新：刷新轮播图与内容区（同一数据源，refreshHome 重拉第一页并回写本地）。
    HomePullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = HomeGridColumns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = rememberHomeContentPadding(),
            horizontalArrangement = Arrangement.spacedBy(HomeGridSpacing),
            verticalArrangement = Arrangement.spacedBy(HomeGridSpacing),
        ) {
        item(key = "topBar", span = { GridItemSpan(maxLineSpan) }) {
            HomeTopBar(
                onSearchClick = onSearchClick,
                onProfileClick = onProfileClick,
                searchHint = searchHint,
                profileContentDescription = profileContentDescription,
            )
        }

        // 轮播图未就绪时用同尺寸骨架占位；key 随状态切换，避免骨架与真实轮播复用同一节点。
        val bannerItemKey = if (banners.isEmpty()) "bannerSkeleton" else "banner"
        item(key = bannerItemKey, span = { GridItemSpan(maxLineSpan) }) {
            if (banners.isEmpty()) {
                HomeBannerSkeleton()
            } else {
                HomeBanner(
                    banners = banners,
                    onBannerClick = onAnimeClick,
                    autoPlay = bannerVisible,
                )
            }
        }

        item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
            HomeActionTabs(onActionClick = onActionClick)
        }

        if (anime.isEmpty()) {
            // 首页无数据时用骨架屏代替空状态文案：轮播图/内容区保持占位，
            // 由下拉刷新或后续数据到达后自然替换。
            items(
                count = 12,
                key = { "skeleton_$it" },
                contentType = { "skeleton" },
            ) {
                PosterCardSkeleton()
            }
        } else {
            items(
                items = anime,
                key = { it.id },
                contentType = { "anime" },
            ) { item ->
                AnimePosterCard(
                    id = item.id,
                    title = item.title,
                    coverUrl = item.coverUrl,
                    onCardClick = onAnimeClick,
                )
            }

            if (isLoadingMore || pagingPending) {
                // 分页加载：补一行海报骨架，而不是到底一个转圈。
                // 骨架行与网格同列数、同宽、同间距，数据到位后原地被真实卡片替换，
                // 加载期间的版面高度也不会跳。
                item(key = "loadingMore", span = { GridItemSpan(maxLineSpan) }) {
                    PosterSkeletonRow(
                        spacing = HomeGridSpacing,
                        minColumnWidth = HomeGridMinWidth,
                    )
                }
            }
        }
        }
    }
}

/**
 * 轮播图骨架屏：与 [HomeBanner] 同高同圆角（共用 `BANNER_HEIGHT` / `BANNER_CORNER`），
 * 在 `banners` 为空时占位显示。尺寸写死在两处过：曾经这里用 12dp 圆角而真实卡片是 20dp，
 * 数据到货的瞬间四个角会明显弹一下。
 */
@Composable
private fun HomeBannerSkeleton(
    modifier: Modifier = Modifier,
) {
    SkeletonBox(
        modifier = modifier
            .fillMaxWidth()
            .height(BANNER_HEIGHT),
        shape = RoundedCornerShape(BANNER_CORNER),
    )
}
