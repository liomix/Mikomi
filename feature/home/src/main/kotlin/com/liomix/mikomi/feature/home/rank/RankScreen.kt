package com.liomix.mikomi.feature.home.rank

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.ui.component.ErrorState
import com.liomix.mikomi.core.ui.component.SkeletonBox
import com.liomix.mikomi.core.ui.component.Tag
import com.liomix.mikomi.core.ui.component.TagRow
import com.liomix.mikomi.feature.home.R
import kotlinx.coroutines.launch

/**
 * 排行榜：首页快捷入口的二级页面（国漫 / 日漫综合评分榜）。
 *
 * - 顶栏右侧刷新：刷新进行中替换为进度环并禁用重复点击；
 * - 分类标签是静态资源，任何状态下都真实渲染，只有下方的卡片列表需要骨架屏；
 * - 榜单卡片为「封面满铺 + 向右渐进模糊」，左侧斜体名次，右侧单行标题；
 * - 错误态的重试与顶栏刷新是同一条链路，统一走 [onRefreshClick]。
 *
 * 无状态：数据与刷新策略由上层 [RankRoute] 通过 ViewModel 提供。
 *
 * @param uiState 页面状态（加载 / 数据 / 空 / 错误）。
 * @param isRefreshing 是否正在刷新，驱动顶栏刷新按钮的表现。
 * @param onBackClick 顶栏返回。
 * @param onRefreshClick 刷新（顶栏按钮与错误态重试共用同一入口）。
 * @param onAnimeClick 点击榜单条目进入详情。
 */
@Composable
fun RankScreen(
    uiState: RankUiState,
    isRefreshing: Boolean,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 标签与分页在加载期间同样存在，翻页状态因此提到状态分发之外。
    val pagerState = rememberPagerState(pageCount = { RankType.entries.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            RankTopBar(
                isRefreshing = isRefreshing,
                onBackClick = onBackClick,
                onRefreshClick = onRefreshClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
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
                onAnimeClick = onAnimeClick,
                onRefreshClick = onRefreshClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 顶栏：标题居中、左侧返回、右侧刷新。
 *
 * 刷新中用一个同尺寸的进度环替代图标，避免按钮宽度跳动；
 * 同时禁用按钮，防止连点叠加多次请求。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankTopBar(
    isRefreshing: Boolean,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.home_action_rank),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.rank_back),
                )
            }
        },
        actions = {
            IconButton(
                onClick = onRefreshClick,
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
        },
    )
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
    TagRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        RankType.entries.forEachIndexed { index, type ->
            Tag(
                text = stringResource(type.labelRes),
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 状态分发：只有卡片列表会在加载 / 空态时退化为骨架屏。 */
@Composable
private fun RankBody(
    uiState: RankUiState,
    pagerState: PagerState,
    onAnimeClick: (String) -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState) {
        RankUiState.Loading,
        RankUiState.Empty,
        -> RankCardSkeletonList(modifier = modifier)

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
                onAnimeClick = onAnimeClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 榜单列表：单个榜单没有数据时同样持续骨架屏，而不是切空态。 */
@Composable
private fun RankList(
    entries: List<RankEntry>,
    onAnimeClick: (String) -> Unit,
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
            // 底部留出悬浮导航栏空间，避免最后一项被遮挡。
            bottom = WindowInsets.navigationBars.asPaddingValues()
                .calculateBottomPadding() + 96.dp,
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
                onClick = { onAnimeClick(entry.anime.id) },
            )
        }
    }
}

/** 卡片骨架列表：整卡 shimmer，数量取一屏可见量级。 */
@Composable
private fun RankCardSkeletonList(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(RankCardSpacing),
    ) {
        repeat(SkeletonCardCount) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RankCardHeight),
                shape = RoundedCornerShape(RankCardCorner),
            )
        }
    }
}

/** 排行榜分类：国漫 / 日漫，两榜均按综合评分降序。 */
private enum class RankType(@param:StringRes val labelRes: Int) {
    CHINESE(R.string.rank_tab_chinese),
    JAPANESE(R.string.rank_tab_japanese),
}

/** 顶栏刷新进度环的直径。 */
private val RefreshIndicatorSize = 20.dp

/** 顶栏刷新进度环的线宽。 */
private val RefreshIndicatorStroke = 2.dp

/** 骨架列表的卡片数量。 */
private const val SkeletonCardCount = 6

/** 榜单条目的复用类型（全部条目视觉一致）。 */
private const val RankCardContentType = "rank_entry"
