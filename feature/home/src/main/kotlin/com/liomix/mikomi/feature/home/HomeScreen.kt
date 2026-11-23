package com.liomix.mikomi.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.base.ErrorState
import com.liomix.mikomi.core.ui.base.SkeletonBox
import com.liomix.mikomi.core.ui.base.SkeletonShimmer
import com.liomix.mikomi.core.ui.business.AnimePosterCard
import com.liomix.mikomi.core.ui.business.PosterCardSkeleton
import com.liomix.mikomi.core.ui.business.PosterSkeletonRow
import com.liomix.mikomi.core.ui.layout.LocalBottomBarSpace
import com.liomix.mikomi.core.ui.window.rememberBottomInset
import com.liomix.mikomi.core.ui.window.rememberInsetsDp
import com.liomix.mikomi.core.ui.window.rememberTopInset

/** 网格最小列宽：手机竖屏约 3 列，宽屏自动增加列数（Dart 版为固定 3 列）。 */
private val HomeGridMinWidth = 100.dp

private val HomeGridColumns = GridCells.Adaptive(minSize = HomeGridMinWidth)

/** 网格行内 / 行间间距；分页骨架行必须用同一个值，否则列宽对不齐。 */
private val HomeGridSpacing = Spacing.Md

/**
 * 网格里各段的稳定 key。
 *
 * 骨架与真实内容共用同一套：轮播从骨架换成真实图时 key 会变（`banner` /
 * `bannerSkeleton`），这是刻意的 —— 两者高度相同但内容形态不同，复用同一节点会让
 * AsyncImage 在骨架的 Box 上重跑一次首帧。其余段（顶栏、快捷入口、分页骨架行）不随
 * 数据变化，key 稳定即可，避免整段被重建。
 */
private const val KEY_TOP_BAR = "topBar"
private const val KEY_BANNER = "banner"
private const val KEY_BANNER_SKELETON = "bannerSkeleton"
private const val KEY_ACTIONS = "actions"
private const val KEY_LOADING_MORE = "loadingMore"

/** 卡片与骨架各是一种复用类型：两者视觉不同，混用同一个池没有收益。 */
private const val CONTENT_TYPE_ANIME = "anime"
private const val CONTENT_TYPE_SKELETON = "skeleton"

/** 骨架屏的卡片数量：多于一屏可见量，任何屏幕高度都填得满。 */
private const val SKELETON_CARD_COUNT = 12

/** 距列表末尾还有几项时预取下一页。 */
private const val LOAD_MORE_THRESHOLD = 3

/**
 * 列表最后一行与悬浮底栏之间的呼吸。
 *
 * 它是一条设计节奏，不是几何量：底栏多高、导航栏多高都由别人给，
 * 只有「贴多近算太近」是页面的判断。24dp 与页面顶边那条呼吸同值。
 */
private val ContentBottomGap = Spacing.Xl

/**
 * 网格内容边距。
 *
 * 垂直节奏统一由网格的 [Arrangement.spacedBy] 提供，各区域组件不再自带区域间垂直
 * padding；此处补的是**安全区**与页面呼吸。
 *
 * 底部那一段必须是算出来的，不能写死：`112.dp` 曾经在手势导航的手机上正好等于
 * 「栏体 56 + 外边距 8 + 手势条 24 + 呼吸 24」，但换成三键导航（导航栏 48dp）时
 * 最后一行就被压在导航栏底下了 —— 一个只在部分设备上出现、且没人会去复现的缺陷。
 * 现在按「壳下发的栏体高度 + 系统导航栏 + 呼吸」相加，换设备、换导航方式都成立。
 *
 * 左右也读安全区：横屏刘海机的挖孔在两侧，第一列封面会被切掉一条边。
 *
 * 用 remember 固定实例：`PaddingValues` 是接口类型（Compose 视为不稳定），
 * 每次重组都新建会让 `LazyVerticalGrid` 无法跳过、白跑一次重排。
 */
@Composable
private fun rememberHomeContentPadding(): PaddingValues {
    val topInset = rememberTopInset()
    val bottomInset = rememberBottomInset()
    val barSpace = LocalBottomBarSpace.current
    val insets = rememberInsetsDp()
    return remember(topInset, bottomInset, barSpace, insets) {
        PaddingValues(
            start = insets.start + Spacing.Lg,
            end = insets.end + Spacing.Lg,
            top = topInset + Spacing.Xs,
            bottom = barSpace + bottomInset + ContentBottomGap,
        )
    }
}

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

/**
 * 首页：一个网格承载「顶栏 / 轮播 / 快捷入口 / 海报」四段。
 *
 * **骨架屏不是另一种布局，它就是同一个网格在空数据下的样子**：轮播为空 → 轮播骨架，
 * 海报为空 → 12 张卡片骨架。所以这里只有一个 [HomeFeed]，[HomeContent.Loading] 与
 * [HomeContent.Success] 的区别只是喂给它的数据。两份网格各写一遍的下场是骨架与真实
 * 内容永远对不齐 —— 列宽、间距、占位高度都要手工同步，而它们本来就是同一份东西。
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onActionClick: (HomeQuickAction) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val content = uiState.content) {
        HomeContent.Loading -> HomeFeed(
            banners = emptyList(),
            anime = emptyList(),
            isRefreshing = uiState.isRefreshing,
            isLoadingMore = false,
            hasMore = true,
            onAnimeClick = onAnimeClick,
            onSearchClick = onSearchClick,
            onProfileClick = onProfileClick,
            onActionClick = onActionClick,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            modifier = modifier,
        )

        HomeContent.Error -> ErrorState(
            message = stringResource(R.string.home_load_failed),
            onRetry = onRefresh,
            modifier = modifier,
        )

        is HomeContent.Success -> HomeFeed(
            banners = content.banners,
            anime = content.anime,
            isRefreshing = uiState.isRefreshing,
            isLoadingMore = content.isLoadingMore,
            hasMore = content.hasMore,
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

/**
 * 首页网格本体：内容态与骨架态共用。
 *
 * 「是否显示骨架」不再是一个独立页面，而是两个局部判断：轮播为空、海报为空。
 * 于是数据从无到有时，页面上只有对应的那一格换掉，网格本身不会被替换 —— 顶栏、
 * 快捷入口不闪，滚动位置（如果用户已经能滚）也不会丢。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeed(
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
        if (shouldLoadMore && hasMore && !isLoadingMore) onLoadMore()
    }

    // 轮播图滚出屏幕后停止自动翻页：翻页会切图 → 新的图片请求与解码，
    // 用户正在滚列表时等于让一个不可见的组件在后台抢解码线程和带宽。
    // derivedStateOf 只在 true/false 翻转时通知，滚动过程本身不会引起重组。
    val bannerVisible by remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.any {
                it.key == KEY_BANNER || it.key == KEY_BANNER_SKELETON
            }
        }
    }

    val showsSkeleton = anime.isEmpty()

    HomePullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        val grid: @Composable () -> Unit = {
            LazyVerticalGrid(
                state = gridState,
                columns = HomeGridColumns,
                modifier = Modifier.fillMaxSize(),
                contentPadding = rememberHomeContentPadding(),
                horizontalArrangement = Arrangement.spacedBy(HomeGridSpacing),
                verticalArrangement = Arrangement.spacedBy(HomeGridSpacing),
            ) {
                item(key = KEY_TOP_BAR, span = { GridItemSpan(maxLineSpan) }) {
                    HomeTopBar(
                        onSearchClick = onSearchClick,
                        onProfileClick = onProfileClick,
                        searchHint = searchHint,
                        profileContentDescription = profileContentDescription,
                    )
                }

                item(
                    key = if (banners.isEmpty()) KEY_BANNER_SKELETON else KEY_BANNER,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
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

                item(key = KEY_ACTIONS, span = { GridItemSpan(maxLineSpan) }) {
                    HomeActionTabs(onActionClick = onActionClick)
                }

                if (showsSkeleton) {
                    items(
                        count = SKELETON_CARD_COUNT,
                        key = { index -> "skeleton_$index" },
                        contentType = { CONTENT_TYPE_SKELETON },
                    ) {
                        PosterCardSkeleton()
                    }
                } else {
                    items(
                        items = anime,
                        key = { it.id },
                        contentType = { CONTENT_TYPE_ANIME },
                    ) { item ->
                        AnimePosterCard(
                            id = item.id,
                            title = item.title,
                            coverUrl = item.coverUrl,
                            onCardClick = onAnimeClick,
                        )
                    }

                    if (isLoadingMore) {
                        // 分页加载补一行海报骨架，而不是到底一个转圈：骨架行与网格同列数、
                        // 同宽、同间距，数据到位后原地被真实卡片替换，加载期间版面高度也不跳。
                        item(key = KEY_LOADING_MORE, span = { GridItemSpan(maxLineSpan) }) {
                            PosterSkeletonRow(
                                spacing = HomeGridSpacing,
                                minColumnWidth = HomeGridMinWidth,
                            )
                        }
                    }
                }
            }
        }

        // 扫光容器会开一条按帧唤醒的时间线：只在骨架真的显示时挂上，
        // 内容到位后它随分支一起离开组合，不留一条空转的无限动画。
        if (showsSkeleton) SkeletonShimmer(grid) else grid()
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
