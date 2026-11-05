package com.liomix.mikomi.feature.home.rank

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.theme.MikomiSpacing
import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.ui.component.base.ErrorState
import com.liomix.mikomi.core.ui.component.base.SkeletonBox
import com.liomix.mikomi.core.ui.component.base.Tab
import com.liomix.mikomi.core.ui.component.base.TabBar
import com.liomix.mikomi.core.ui.component.base.TabItemStyle
import com.liomix.mikomi.core.ui.component.layout.BaseScreenLayout
import com.liomix.mikomi.feature.home.R
import kotlinx.coroutines.launch

/**
 * 排行榜：首页快捷入口的二级页面（AniRpb 日漫 / 国漫整榜，每榜 100 条）。
 *
 * - 顶栏由 [BaseScreenLayout] 统一提供，右侧刷新：刷新进行中替换为进度环并禁用重复点击；
 * - 分类标签是静态资源，任何状态下都真实渲染，只有下方的卡片列表需要骨架屏；
 * - 卡片列表在「加载中」与「一个数据都没有」时都保持骨架屏，不切空态；
 * - 榜单卡片为「封面整块满铺」，左侧斜体名次，右侧单行标题（文字自带描影）；
 * - 错误态的重试与顶栏刷新是同一条链路，统一走 [onRefreshClick]。
 *
 * 无状态：数据与刷新策略由上层 [RankRoute] 通过 ViewModel 提供。
 *
 * @param uiState 页面状态（加载 / 数据 / 错误）。
 * @param isRefreshing 是否正在刷新，驱动顶栏刷新按钮的表现。
 * @param onBackClick 顶栏返回。
 * @param onRefreshClick 刷新（顶栏按钮与错误态重试共用同一入口）。
 * @param onAnimeTitleClick 点击榜单条目按标题搜索（AniRpb 视频 id 无法直连详情）。
 */
@Composable
fun RankScreen(
    uiState: RankUiState,
    isRefreshing: Boolean,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onAnimeTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 标签与分页在加载期间同样存在，翻页状态因此提到状态分发之外。
    val pagerState = rememberPagerState(pageCount = { RankType.entries.size })
    val coroutineScope = rememberCoroutineScope()

    BaseScreenLayout(
        title = stringResource(R.string.home_action_rank),
        modifier = modifier,
        onBackClick = onBackClick,
        actions = { RefreshAction(isRefreshing = isRefreshing, onClick = onRefreshClick) },
        // 内容自带滚动（标签栏固定 + Pager 内的 LazyColumn），骨架不再套一层垂直滚动。
        scrollable = false,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        RankTabs(
            selectedIndex = pagerState.currentPage,
            onSelect = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            },
        )

        RankBody(
            uiState = uiState,
            pagerState = pagerState,
            onAnimeTitleClick = onAnimeTitleClick,
            onRefreshClick = onRefreshClick,
            // weight 而不是 fillMaxSize：标签栏先占位，剩余高度才给列表。
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 顶栏刷新动作：刷新中用一个同尺寸的进度环替代图标，避免按钮宽度跳动；
 * 同时禁用按钮，防止连点叠加多次请求。
 */
@Composable
private fun RefreshAction(
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = !isRefreshing,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(RefreshIndicatorSize),
                strokeWidth = RefreshIndicatorStroke,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.rank_refresh),
            )
        }
    }
}

/**
 * 分类标签：静态资源，任何状态下都真实渲染。
 *
 * 选中态与 [HorizontalPager] 的当前页联动，点击直接切页。
 */
@Composable
private fun RankTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabBar(
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MikomiSpacing.Lg, vertical = MikomiSpacing.Md),
        itemStyle = TabItemStyle.text(),
        itemSpacing = 0.dp,
    ) {
        RankType.entries.forEachIndexed { index, type ->
            Tab(
                index = index,
                text = stringResource(type.labelRes),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 状态分发：加载中与空数据都是骨架屏，只有刷新失败才切错误态。 */
@Composable
private fun RankBody(
    uiState: RankUiState,
    pagerState: PagerState,
    onAnimeTitleClick: (String) -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        RankUiState.Loading -> RankCardSkeletonList(modifier = modifier)

        RankUiState.Error -> ErrorState(
            message = stringResource(R.string.rank_error),
            onRetry = onRefreshClick,
            modifier = modifier,
        )

        is RankUiState.Success -> HorizontalPager(
            state = pagerState,
            modifier = modifier,
        ) { page ->
            RankList(
                entries = when (RankType.entries[page]) {
                    RankType.CHINESE -> uiState.chinese
                    RankType.JAPANESE -> uiState.japanese
                },
                onAnimeTitleClick = onAnimeTitleClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 榜单列表：单个榜单没有数据时同样持续骨架屏，而不是切空态。 */
@Composable
private fun RankList(
    entries: List<RankEntry>,
    onAnimeTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        RankCardSkeletonList(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            // 导航栏留白由 BaseScreenLayout 统一消费，这里只补悬浮栏的避让余量。
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(RankCardSpacing),
    ) {
        itemsIndexed(
            items = entries,
            key = { _, entry -> entry.anime.id },
            contentType = { _, _ -> RankCardContentType },
        ) { index, entry ->
            RankAnimeCard(
                entry = entry,
                rank = index + 1,
                onClick = { onAnimeTitleClick(entry.anime.title) },
            )
        }
    }
}

/**
 * 卡片骨架列表：整卡 shimmer。
 *
 * 用 [LazyColumn] 而不是固定 [Column]：空数据时会长时间停留在这个状态，
 * 小屏上多出来的骨架必须被裁在可见区域内，不能画到导航栏下面去。
 * 数量取一屏可见量级，多出来的部分滚动时才出现。
 */
@Composable
private fun RankCardSkeletonList(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(RankCardSpacing),
    ) {
        items(count = SkeletonCardCount) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RankCardHeight),
                shape = RoundedCornerShape(RankCardCorner),
            )
        }
    }
}

/** 排行榜分类：日漫 / 国漫（日漫在前），两榜均按服务端整榜名次排列。 */
private enum class RankType(@param:StringRes val labelRes: Int) {
    JAPANESE(R.string.rank_tab_japanese),
    CHINESE(R.string.rank_tab_chinese),
}

/** 顶栏刷新进度环的直径。 */
private val RefreshIndicatorSize = 20.dp

/** 顶栏刷新进度环的线宽。 */
private val RefreshIndicatorStroke = 2.dp

/** 骨架列表的卡片数量（多于一屏，保证任何屏幕高度都填满）。 */
private const val SkeletonCardCount = 8

/** 榜单条目的复用类型（全部条目视觉一致）。 */
private const val RankCardContentType = "rank_entry"
