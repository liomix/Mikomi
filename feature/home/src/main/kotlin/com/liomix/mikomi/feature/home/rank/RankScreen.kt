package com.liomix.mikomi.feature.home.rank

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.anime.RankEntry
import com.liomix.mikomi.core.ui.base.ErrorState
import com.liomix.mikomi.core.ui.base.SkeletonBox
import com.liomix.mikomi.core.ui.base.Tab
import com.liomix.mikomi.core.ui.base.TabBar
import com.liomix.mikomi.core.ui.base.TabHighlight
import com.liomix.mikomi.core.ui.base.TabItemStyle
import com.liomix.mikomi.core.ui.business.RefreshButton
import com.liomix.mikomi.core.ui.image.CoverPrefetch
import com.liomix.mikomi.core.ui.layout.BaseScreenLayout
import com.liomix.mikomi.feature.home.R

/**
 * 排行榜：首页快捷入口的二级页面（AniRpb 日漫 / 国漫整榜）。
 *
 * ## 一屏三段，各管一段
 *
 * ```
 * BaseScreenLayout        ← 顶栏（返回 + 刷新）、安全区、滚动容器
 *   └ TabBar              ← 分类，任何状态下都在
 *   └ 内容                ← 加载 / 错误 / 两个 pager 页
 * ```
 *
 * 分类栏**不跟着状态走**：数据还在下载时它也该在，否则用户看到的是一个「没有分类的
 * 空页面」，等数据到了再冒出来，页面要跳一下。所以它放在状态分发之外。
 *
 * 分页状态同样在状态分发之外：错误态重试成功之后，应该回到用户刚才看的那一页。
 *
 * @param uiState 页面状态（刷新指示 + 加载 / 数据 / 错误）。
 * @param onBackClick 顶栏返回。
 * @param onRefreshClick 刷新（顶栏按钮与错误态重试共用同一入口）。
 * @param onAnimeTitleClick 点击榜单条目按标题搜索（AniRpb 视频 id 无法直连详情）。
 */
@Composable
fun RankScreen(
    uiState: RankUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onAnimeTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { RankType.entries.size }

    BaseScreenLayout(
        title = stringResource(R.string.home_action_rank),
        onBackClick = onBackClick,
        actions = {
            RefreshButton(
                isRefreshing = uiState.refreshing,
                contentDescription = stringResource(R.string.rank_refresh),
                onClick = onRefreshClick,
            )
        },
        // 内容自己滚（pager 里是 LazyColumn），骨架不再套一层垂直滚动 ——
        // 两层滚动会让「先滚外层还是先滚列表」变成一个没人能回答的问题。
        scrollable = false,
        contentPadding = PaddingValues(Spacing.None),
        verticalArrangement = Arrangement.Top,
        modifier = modifier,
    ) {
        TabBar(
            selectedIndex = pagerState.currentPage,
            // 切页由 TabBar 自己做（它知道该给多久的动画，与指示器一致）。
            onSelect = {},
            modifier = Modifier
                .fillMaxWidth()
                // 只是两个分类的切换条，不是一排按钮：上下各 8dp，多了会把榜单往下推。
                .padding(horizontal = Spacing.Lg, vertical = Spacing.Sm),
            itemStyle = TabItemStyle.text(),
            // 不铺满：标签按文字宽度排列、整行居中，因此指示器宽度就是文字宽度。
            // 用 `weight(1f)` 平分的话，标签矩形是半栏宽，指示器会跟着变成一条长拱 ——
            // 那是 Flutter `indicatorSize: tab` 与 `label` 的区别，我们要后者。
            itemSpacing = Spacing.Xl,
            // 下划线形态：一组文字分类，不需要一块色底来「按下去」。
            highlight = TabHighlight.Underline,
            // 栏本身透明 —— 下划线要落在那条 1dp 分隔线上，而不是落在一块卡片上。
            containerColor = Color.Transparent,
            // 与 pager 联动：拖动时线跟着走，松手才落页。
            pagerState = pagerState,
        ) {
            RankType.entries.forEachIndexed { index, type ->
                Tab(
                    index = index,
                    text = stringResource(type.labelRes),
                )
            }
        }

        // weight 而不是 fillMaxSize：分类栏先占位，剩下的高度才给列表。
        when (val content = uiState.content) {
            RankContent.Loading -> RankSkeleton(modifier = Modifier.weight(1f))

            RankContent.Error -> ErrorState(
                message = stringResource(R.string.rank_error),
                onRetry = onRefreshClick,
                modifier = Modifier.weight(1f),
            )

            is RankContent.Success -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                RankPage(
                    entries = content.forType(RankType.entries[page]),
                    onAnimeTitleClick = onAnimeTitleClick,
                )
            }
        }
    }
}

/**
 * 单页榜单。
 *
 * 空数据与加载中共用骨架，而不是切一个「暂无数据」：整榜是一次性拉全量的，
 * 列表为空只可能是这次请求还没回来或失败了 —— 那是「还在路上」，不是「真的没有」。
 */
@Composable
private fun RankPage(
    entries: List<RankEntry>,
    onAnimeTitleClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 榜单一次拿到全量，滚到第 30 名时再下封面已经迟了：数据到手就把整榜排进预取队列。
    CoverPrefetch(urls = entries.map { it.anime.coverUrl })

    RankListScaffold(modifier = modifier) {
        if (entries.isEmpty()) {
            rankSkeletons()
        } else {
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
}

/** 骨架列表：与真实列表同构（同宽度、同间距、同高度），数据到位时行高不变。 */
@Composable
private fun RankSkeleton(modifier: Modifier = Modifier) {
    RankListScaffold(modifier = modifier) {
        rankSkeletons()
    }
}

/**
 * 榜单的列表骨架：内边距与间距只定义一次。
 *
 * 真实列表与骨架各写一份 `contentPadding`，两处一旦不一致，数据到达时整屏会横向
 * 抖一下 —— 那种抖动很难归因，但用户天天看到。
 */
@Composable
private fun RankListScaffold(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.Md,
            end = Spacing.Md,
            top = Spacing.Sm,
            // 导航栏留白由 BaseScreenLayout 消费，这里只补与页面底边之间的呼吸。
            bottom = Spacing.Xl,
        ),
        verticalArrangement = Arrangement.spacedBy(RankCardSpacing),
        content = content,
    )
}

/** 一屏的骨架卡：数量取可见量级，多出来的部分滚动时才出现。 */
private fun LazyListScope.rankSkeletons() {
    items(count = SkeletonCardCount) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(RankCardHeight),
            shape = RoundedCornerShape(RankCardCorner),
        )
    }
}

/** 状态里的两个榜单，按分类取。 */
private fun RankContent.Success.forType(type: RankType): List<RankEntry> = when (type) {
    RankType.JAPANESE -> japanese
    RankType.CHINESE -> chinese
}

/** 排行榜分类：日漫 / 国漫（日漫在前），两榜均按服务端整榜名次排列。 */
private enum class RankType(@param:StringRes val labelRes: Int) {
    JAPANESE(R.string.rank_tab_japanese),
    CHINESE(R.string.rank_tab_chinese),
}

/** 骨架卡数量：多于一屏，保证任何屏幕高度都填满。 */
private const val SkeletonCardCount = 8

/** 榜单条目的复用类型（全部条目视觉一致）。 */
private const val RankCardContentType = "rank_entry"
