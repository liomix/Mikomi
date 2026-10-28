package com.liomix.mikomi.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.component.AnimePosterCard
import com.liomix.mikomi.core.ui.component.ErrorState
import com.liomix.mikomi.core.ui.component.SkeletonBox

/** 网格列宽：手机竖屏约 3 列，宽屏自动增加列数（Dart 版为固定 3 列）。 */
private val HomeGridColumns = GridCells.Adaptive(minSize = 100.dp)

/**
 * 网格内容边距。
 *
 * 垂直节奏统一由网格的 [Arrangement.spacedBy] 提供（12dp），
 * 各区域组件不再自带区域间垂直 padding；此处仅为页面顶边
 * 补状态栏安全区 + 少量呼吸空间。
 */
@Composable
private fun homeContentPadding(): PaddingValues {
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = statusBarInset + 4.dp,
        bottom = 112.dp,
    )
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

    LazyVerticalGrid(
        columns = HomeGridColumns,
        modifier = modifier.fillMaxSize(),
        contentPadding = homeContentPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            SkeletonGridCard()
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
    LaunchedEffect(shouldLoadMore, isLoadingMore, hasMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore) {
            onLoadMore()
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
            contentPadding = homeContentPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                SkeletonGridCard()
            }
        } else {
            items(
                items = anime,
                key = { it.id },
                contentType = { "anime" },
            ) { item ->
                AnimePosterCard(
                    anime = item,
                    onClick = { onAnimeClick(item.id) },
                )
            }

            if (isLoadingMore) {
                item(key = "loadingMore", span = { GridItemSpan(maxLineSpan) }) {
                    // 指示器自带固定尺寸，直接 fillMaxWidth 会与其内部 size 约束冲突
                    // 导致圆环被拉伸变形；用全宽容器居中包裹。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
        }
    }
}

/**
 * 轮播图骨架屏：与 [HomeBanner] 尺寸（180.dp 高 + 12.dp 圆角）保持一致，
 * 在 `banners` 为空时占位显示，避免主页面积布局跳动。
 */
@Composable
private fun HomeBannerSkeleton(
    modifier: Modifier = Modifier,
) {
    SkeletonBox(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun SkeletonGridCard(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp),
            shape = RoundedCornerShape(4.dp),
        )
    }
}
