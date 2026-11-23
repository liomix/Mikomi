package com.liomix.mikomi.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.ui.base.ErrorState
import com.liomix.mikomi.core.ui.base.SkeletonBox
import com.liomix.mikomi.core.ui.base.SkeletonShimmer
import com.liomix.mikomi.core.ui.business.AnimePosterCard
import com.liomix.mikomi.core.ui.business.PosterCardSkeleton
import com.liomix.mikomi.core.ui.business.PosterGrid
import com.liomix.mikomi.core.ui.layout.LocalBottomBarSpace
import com.liomix.mikomi.core.ui.window.rememberBottomInset
import com.liomix.mikomi.core.ui.window.rememberInsetsDp
import com.liomix.mikomi.core.ui.window.rememberTopInset

/** 网格最小列宽：手机竖屏约 3 列，宽屏自动增加列数（Dart 版为固定 3 列）。 */
private val HomeGridMinWidth = 100.dp


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
private const val KEY_HEADER = "header"
private const val KEY_PAGING_SKELETON_PREFIX = "paging_"

/** 卡片与骨架各是一种复用类型：两者视觉不同，混用同一个池没有收益。 */
private const val CONTENT_TYPE_ANIME = "anime"
private const val CONTENT_TYPE_SKELETON = "skeleton"

/** 首屏骨架铺几行：多于一屏可见量，任何屏幕高度都填得满，且正好是整数行。 */
private const val SKELETON_ROWS = 4

/** 顶栏在快捷栏靠上来之前多远开始补底色：留一段提前量，滚到底时已经补满。 */
private val TopBarScrimLead = 40.dp

/**
 * 顶栏底色在按钮下方多伸出的一段。
 *
 * 底色底边与头像底边完全重合时，看起来像按钮自带的边框；多出这一小段，它才是一块
 * 「顶栏的底」。与头图之间的间距不参与计算，所以滚动触发点不受影响。
 */
private val TopBarScrimTail = Spacing.Sm


/**
 * 快捷栏折叠的行程。
 *
 * 取快捷栏的展开高度（栏内上下留白 4 + 4、按钮 58）。这里不做精确依赖：它只决定
 * 「高度压缩得多快」，即使差几 dp，终点也永远是被 `coerceIn` 收在 0 和 1 上的。
 */
private val ActionTabsFoldDistance = 66.dp

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
 *
 * 三个分量（[HomeChrome.start] / [HomeChrome.end] / [HomeChrome.top]）同时是**沉浸式头图
 * 的出血量**：头图要铺到状态栏后面、铺到屏幕两侧，就必须先知道内容列离边缘有多远。
 * 以前它只作为 `PaddingValues` 存在，于是「头图铺满」这件事只能靠再写一遍 16dp + inset ——
 * 两份数一旦不同步，头图就会与网格错开几个像素。
 */
@Immutable
private data class HomeChrome(
    /** 网格内容的边距。 */
    val padding: PaddingValues,
    /** 内容列与屏幕左右边缘的距离 —— 沉浸式头图按它反向出血。 */
    val start: Dp,
    val end: Dp,
    /** 内容列与屏幕顶部的距离（状态栏 + 呼吸）—— 头图同样按它向上出血。 */
    val top: Dp,
)

@Composable
private fun rememberHomeChrome(): HomeChrome {
    val topInset = rememberTopInset()
    val bottomInset = rememberBottomInset()
    val barSpace = LocalBottomBarSpace.current
    val insets = rememberInsetsDp()
    return remember(topInset, bottomInset, barSpace, insets) {
        val start = insets.start + Spacing.Lg
        val end = insets.end + Spacing.Lg
        // 顶栏与头图共用这一个值：头图上出血多少，网格首项就下移多少，两边永远对得上。
        val top = topInset + Spacing.Xs
        HomeChrome(
            padding = PaddingValues(
                start = start,
                end = end,
                // 顶栏已经移出网格、固定在最上层，头图要顶到状态栏后面，
                // 所以网格从 y = 0 开始 —— 上边距只能是 0。`top` 仍然保留给
                // 头图高度与顶栏容器用（它们的坐标系从屏幕顶端算）。
                top = Spacing.None,
                bottom = barSpace + bottomInset + ContentBottomGap,
            ),
            start = start,
            end = end,
            top = top,
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
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
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
            // 头图整块滚出视口就停自动翻页。合成项里含快捷栏，于是判定比头图本身
            // 多出那一栏的高度 —— 多出来的这段里头图已经滚掉大半，代价只是可能空翻
            // 一次，不值得为此再加一套位置计算。
            gridState.layoutInfo.visibleItemsInfo.any { it.key == KEY_HEADER }
        }
    }

    val showsSkeleton = anime.isEmpty()
    val chrome = rememberHomeChrome()
    val scrollChrome = rememberHomeScrollChrome(gridState, chrome)

    // 下拉进度要同时喂给两处：指示器（容器自己用）和轮播图的拉伸。
    // 所以 state 在这里创建，不由容器内部各建一个。
    val pullState = rememberPullToRefreshState()

    HomePullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        // 顶栏不在网格里：它固定贴着状态栏，网格从它底下滚过。
        // 所以网格从 y = 0 起（头图顶到状态栏后面），顶栏作为 overlay 画在最上层。
        val grid: @Composable () -> Unit = {
            // 先量宽、再算列数：`GridCells.Adaptive` 的列数要到测量阶段才知道，
            // 而「最后一行差几格」是组合期就要回答的问题（见 columnsFor）。
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = PosterGrid.columnsFor(maxWidth, HomeGridMinWidth, HomeGridSpacing)
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = chrome.padding,
                    horizontalArrangement = Arrangement.spacedBy(HomeGridSpacing),
                    verticalArrangement = Arrangement.spacedBy(HomeGridSpacing),
                ) {
                    // 头图与快捷栏合成一项，中间不留网格间距：
                    // 头图的底边已经淡出成页面底色，再隔一条空行就是双重分隔。
                    item(key = KEY_HEADER, span = { GridItemSpan(maxLineSpan) }) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HomeHero(
                                banners = banners,
                                chrome = chrome,
                                autoPlay = bannerVisible,
                                pullFraction = pullState.distanceFraction,
                                onAnimeClick = onAnimeClick,
                            )
                            val fold = scrollChrome.actionTabsFold.value
                            HomeActionTabs(
                                onActionClick = onActionClick,
                                // 压缩的**画面与占位都在组件内部**（它自己上报缩小后的高度），
                                // 这里只把滚动进度交给它。
                                collapse = fold,
                            )
                        }
                    }

                    if (showsSkeleton) {
                        // 正好铺满整数行：半行会在底部留一排空洞。
                        items(
                            count = columns * SKELETON_ROWS,
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
                            // 补满**当前行**：差几格补几格；正好铺满时再起一整行。
                            val gap = PosterGrid.cellsToFillRow(columns, anime.size)
                            val padding = if (gap == 0) columns else gap
                            items(
                                count = padding,
                                key = { index -> KEY_PAGING_SKELETON_PREFIX + index },
                                contentType = { CONTENT_TYPE_SKELETON },
                            ) {
                                PosterCardSkeleton()
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 扫光容器会开一条按帧唤醒的时间线：只在骨架真的显示时挂上，
            // 内容到位后它随分支一起离开组合，不留一条空转的无限动画。
            if (showsSkeleton) SkeletonShimmer(grid) else grid()

            HomeTopBarOverlay(
                solid = scrollChrome.topBarSolid,
                chrome = chrome,
                onSearchClick = onSearchClick,
                onProfileClick = onProfileClick,
                searchHint = searchHint,
                profileContentDescription = profileContentDescription,
            )
        }
    }
}

/**
 * 沉浸式头图：整张封面铺到屏幕顶端与两侧。
 *
 * 两件事让它「沉浸」，其余一概不动：
 *
 * 1. **顶到状态栏后面**：高度 = 轮播高 + 顶部安全区，网格上边距是 0，所以图片从
 *    y = 0 开始画；顶栏是另一层 overlay，浮在它上面。
 * 2. **向两侧出血**：宽度 = 内容列宽 + 左右边距，图片铺到屏幕边缘；横屏刘海机读到的是
 *    安全区之后的边缘。
 *
 * 用布局偏移而不是负 padding：`LazyGrid` 的一格只能拿到内容列宽，`padding` 不会让它变宽；
 * [bleedToEdge] 直接把测量约束放宽再往左放置，格子本身仍然只占「轮播高」这一行 ——
 * 于是下面的快捷入口与网格位置、滚动距离全部不变。
 *
 * 顶栏**不在这里**：它已经不随页面滚动了，见 [HomeTopBarOverlay]。
 */
@Composable
private fun HomeHero(
    banners: List<Anime>,
    chrome: HomeChrome,
    autoPlay: Boolean,
    pullFraction: Float,
    onAnimeClick: (String) -> Unit,
) {
    val height = BANNER_HEIGHT + chrome.top
    Box(
        modifier = Modifier
            .bleedToEdge(start = chrome.start, end = chrome.end, top = Spacing.None)
            .fillMaxWidth()
            .height(height)
            // 下拉时把封面往上拉长一点：`scaleY` 只改绘制，不推挤下面的内容，
            // 所以是「橡皮筋」而不是「整页跟着位移」。锚点在顶部 —— 底边被拉走，
            // 顶边纹丝不动，手感才是「从上面拽住一块布」。
            // 系数 0.06：拉到刷新阈值（`distanceFraction = 1`）时只长 6%，
            // 是「拽了一下」而不是「图被撑开」。上限 1.5 只防极端，正常拉不到。
            .graphicsLayer {
                scaleY = 1f + pullFraction.coerceIn(0f, 1.5f) * 0.06f
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
    ) {
        if (banners.isEmpty()) {
            HomeBannerSkeleton()
        } else {
            HomeBanner(
                banners = banners,
                onBannerClick = onAnimeClick,
                autoPlay = autoPlay,
                height = height,
            )
        }
    }
}

/**
 * 吸顶顶栏：固定贴着状态栏，底色随滚动从全透明变成页面底色。
 *
 * 它**不在网格里**，所以网格从它底下滚过 —— 这正是「头像和搜索框不跟着页面移动」。
 * 底色不能一直是透明的：头图滚过去之后，半透明的按钮背后会是封面、卡片、什么都有，
 * 白字随时可能撞上亮画面。所以快捷栏快顶到它时，底色补成页面底色，把后面的内容盖住。
 *
 * @param solid 底色不透明度；0 = 纯浮层（只有两个玻璃按钮），1 = 不透明页面底色。
 */
@Composable
private fun HomeTopBarOverlay(
    solid: State<Float>,
    chrome: HomeChrome,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    searchHint: String,
    profileContentDescription: String,
) {
    val alpha = solid.value
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 底色比按钮多出 [TopBarScrimTail]：底边正好压在头像底线上时，
            // 那一条会读成「按钮自带的下边框」，而不是「顶栏容器」。
            .height(chrome.top + HomeTopBarHeight + TopBarScrimTail)
            .background(MaterialTheme.colorScheme.background.copy(alpha = alpha)),
    ) {
        HomeTopBar(
            onSearchClick = onSearchClick,
            onProfileClick = onProfileClick,
            searchHint = searchHint,
            profileContentDescription = profileContentDescription,
            // 底色补满时，按钮本身也要换成页面配色 —— 否则白底白字。
            solid = alpha,
            // 用 padding 定位而不是 BottomCenter：按钮位置不能随底色下沿变，
            // 否则加个尾巴就把头像往下推了。
            modifier = Modifier
                .padding(
                    start = chrome.start,
                    end = chrome.end,
                    top = chrome.top,
                ),
        )
    }
}

/**
 * 两条由滚动位置推导的曲线。分开成两个 [State]，各喂给一个消费者：
 * 顶栏只关心底色，快捷栏只关心折叠 —— 合成一个对象的话，两者每次滚动都会一起重组。
 */
@Immutable
private data class HomeScrollChrome(
    val topBarSolid: State<Float>,
    val actionTabsFold: State<Float>,
)

@Composable
private fun rememberHomeScrollChrome(
    gridState: LazyGridState,
    chrome: HomeChrome,
): HomeScrollChrome {
    val density = LocalDensity.current
    val topBarBottom = chrome.top + HomeTopBarHeight
    val heroHeight = BANNER_HEIGHT + chrome.top

    val topBarSolid = remember(gridState, density, topBarBottom, heroHeight) {
        derivedStateOf {
            val actionsTop = actionTabsTop(gridState, density, heroHeight)
            val bottom = with(density) { topBarBottom.toPx() }
            val lead = with(density) { TopBarScrimLead.toPx() }
            ((bottom + lead - actionsTop) / lead).coerceIn(0f, 1f)
        }
    }
    val actionTabsFold = remember(gridState, density, topBarBottom, heroHeight) {
        derivedStateOf {
            val actionsTop = actionTabsTop(gridState, density, heroHeight)
            // 折叠区间 = [底色下沿, 底色下沿 + 一个栏高]：
            //   · 起点在下方一个栏高处 → 距离背景还有一栏时就开始压；
            //   · 终点正好落在底色下沿 → 压到 0 的那一刻顶边刚好贴着背景。
            // 于是整个压缩过程都发生在背景**之上**，栏顶一帧都不会滑进去被盖住。
            val distance = with(density) { ActionTabsFoldDistance.toPx() }
            val start = with(density) {
                (topBarBottom + TopBarScrimTail).toPx()
            } + distance
            ((start - actionsTop) / distance).coerceIn(0f, 1f)
        }
    }
    return remember(topBarSolid, actionTabsFold) {
        HomeScrollChrome(topBarSolid, actionTabsFold)
    }
}

/**
 * 快捷栏顶边此刻在视口中的 y（px）。
 *
 * 头图与快捷栏是网格的第 0 项，所以「滚过了多少」就等于快捷栏顶边被推上去多少 ——
 * 不需要去找这一项的布局信息，也就不会出现「读布局 → 改高度 → 布局又变」的循环。
 * 第 0 项已经滚出视口时返回一个足够小的值，两条曲线自然都停在终点。
 */
private fun actionTabsTop(
    gridState: LazyGridState,
    density: Density,
    heroHeight: Dp,
): Float {
    val scroll = if (gridState.firstVisibleItemIndex == 0) {
        gridState.firstVisibleItemScrollOffset
    } else {
        Int.MAX_VALUE / 4
    }
    return with(density) { heroHeight.toPx() } - scroll
}

/**
 * 让一格网格内容向外出血：测量时把约束放宽 [start] + [end]，绘制时向左偏移。
 *
 * 只用于「背景铺满」的头部：`LazyGrid` 的一格拿到的宽度是内容列宽，靠 `padding` 补不回来
 * （padding 只减少可用空间）。这里放宽约束后，子项能画到内容列之外，而它**占位**仍然只有
 * 原来的宽高，所以下方的项不会跟着移动。
 *
 * @param top 向上出血多少；子项高度必须已包含这段，才会正好铺到屏幕顶端。
 */
private fun Modifier.bleedToEdge(start: Dp, end: Dp, top: Dp): Modifier = layout { measurable, constraints ->
    val startPx = start.roundToPx()
    val endPx = end.roundToPx()
    val topPx = top.roundToPx()
    val widened = constraints.copy(
        minWidth = maxOf(constraints.minWidth + startPx + endPx, 0),
        maxWidth = constraints.maxWidth + startPx + endPx,
    )
    val placeable = measurable.measure(widened)
    // 占位仍是「内容列宽 × （子项高 − 上出血）」，绘制却从内容列左上方开始。
    layout(placeable.width - startPx - endPx, placeable.height - topPx) {
        placeable.place(-startPx, -topPx)
    }
}

/**
 * 轮播图骨架屏：与 [HomeBanner] 同高（共用 `BANNER_HEIGHT`），
 * 在 `banners` 为空时占位显示。尺寸写死在两处过：曾经这里用 12dp 圆角而真实卡片是 20dp，
 * 数据到货的瞬间四个角会明显弹一下。
 */
@Composable
private fun HomeBannerSkeleton(
    modifier: Modifier = Modifier,
) {
    // 头图没有圆角、贴边铺满：骨架必须同形，否则数据到达的瞬间四角会「补圆」。
    SkeletonBox(
        modifier = modifier.fillMaxSize(),
        shape = RectangleShape,
    )
}
