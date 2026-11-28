package com.liomix.mikomi.feature.home.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.designsystem.Spacing
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.CategoryQuery
import com.liomix.mikomi.core.ui.base.EmptyState
import com.liomix.mikomi.core.ui.base.ErrorState
import com.liomix.mikomi.core.ui.business.AnimePosterCard
import com.liomix.mikomi.core.ui.business.PosterCardSkeleton
import com.liomix.mikomi.core.ui.business.PosterGrid
import com.liomix.mikomi.core.ui.layout.BaseScreenLayout
import com.liomix.mikomi.feature.home.R

/**
 * 分类页：顶栏 → 筛选栏 → 海报网格。
 *
 * 三段的分工与排行榜同一个形状：筛选栏**在状态分发之外** —— 加载中、空、出错时它都得在，
 * 否则用户想换一个条件时页面正卡在错误态，就只剩返回键一条路。
 *
 * 网格复用首页那套（`PosterGrid.columnsFor` 定列数、`AnimePosterCard` 画卡、骨架行末尾补满），
 * 两个页面的墙看起来才是同一个产品。
 */
@Composable
fun CategoryScreen(
    uiState: CategoryUiState,
    onBackClick: () -> Unit,
    onQueryChange: (CategoryQuery) -> Unit,
    onAnimeClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseScreenLayout(
        title = stringResource(R.string.home_action_category),
        onBackClick = onBackClick,
        // 条数是「这个分类有多少」的页面级信息，不是内容的第一行：放顶栏右侧，
        // 内容区就少一整行（40dp），第一排海报能早点露出来。
        actions = {
            CategoryResultCount(
                total = (uiState.content as? CategoryContent.Success)?.anime?.size ?: 0,
            )
        },
        // 网格自己滚：外层再套一层滚动会让「先滚谁」变成没人能回答的问题。
        scrollable = false,
        contentPadding = PaddingValues(Spacing.None),
        verticalArrangement = Arrangement.Top,
        modifier = modifier,
    ) {
        FilterBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
        )

        // weight 而不是 fillMaxSize：筛选栏先占位，剩下的高度才给网格。
        when (val content = uiState.content) {
            CategoryContent.Loading -> CategorySkeleton(modifier = Modifier.weight(1f))

            CategoryContent.Empty -> EmptyState(
                message = stringResource(R.string.category_empty),
                modifier = Modifier.weight(1f),
            )

            CategoryContent.Error -> ErrorState(
                message = stringResource(R.string.category_error),
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )

            is CategoryContent.Success -> CategoryGrid(
                anime = content.anime,
                isLoadingMore = content.isLoadingMore,
                hasMore = content.hasMore,
                onAnimeClick = onAnimeClick,
                onLoadMore = onLoadMore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 结果条数：「共 24 部动漫」，数字取主题色。放在顶栏右侧的动作位里。
 *
 * 三段拼在一个 [buildAnnotatedString] 里而不是三个 `Text`：三段各有字号、字重、颜色时，
 * `Row` 里的基线与段间距都要自己调，而行内 `SpanStyle` 只有一条基线、也随系统字号一起缩放。
 *
 * [total] 是**已加载**的部数 —— 这个接口不返回总数（`BangumiSearchResponseDto.total`
 * 一直是 0），所以数字随翻页增长。等服务端给了真总数，改的只是这一个入参的来源。
 *
 * 加载中显示 0 而不是隐藏：这一行在版面里占固定高度，藏起来会让下面的网格跳一下；
 * 而骨架屏就在正下方，不会让人误读成「一条结果都没有」。
 */
@Composable
private fun CategoryResultCount(
    total: Int,
    modifier: Modifier = Modifier,
) {
    val head = stringResource(R.string.category_count_head)
    val tail = stringResource(R.string.category_count_tail)

    Text(
        text = buildAnnotatedString {
            append(head)
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                append(total.toString())
            }
            append(tail)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // 顶栏把动作贴右摆放，这里自己让出页面水槽，才和下面的版心对齐。
        modifier = modifier.padding(end = Spacing.Gutter),
    )
}

@Composable
private fun CategoryGrid(
    anime: List<Anime>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onAnimeClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    // 离底还有三行就去要下一页：等滚到底再请求，用户会先看到一屏空白。
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            // 首帧布局未完成时 visibleItemsInfo 为空：必须判 false，否则一进页面就发请求。
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisible >= info.totalItemsCount - LoadMoreThreshold
        }
    }
    LaunchedEffect(shouldLoadMore, isLoadingMore, hasMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore) onLoadMore()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 先量宽、再算列数：`Adaptive` 的列数要到测量阶段才知道，而「补满当前行」
        // 是组合期就要回答的问题（见 `cellsToFillRow`）。
        val columns = PosterGrid.columnsFor(maxWidth, GridMinWidth, GridSpacing)
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.Md,
                end = Spacing.Md,
                top = Spacing.Sm,
                // 导航栏留白由 BaseScreenLayout 消费，这里只补与页面底边之间的呼吸。
                bottom = Spacing.Xl,
            ),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
            verticalArrangement = Arrangement.spacedBy(GridSpacing),
        ) {
            items(
                items = anime,
                key = { it.id },
                contentType = { ContentTypePoster },
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
                    key = { index -> "$ContentTypeSkeleton$index" },
                    contentType = { ContentTypeSkeleton },
                ) {
                    PosterCardSkeleton()
                }
            }
        }
    }
}

/** 首屏骨架：与真实网格同一套列数、间距、卡片外壳。 */
@Composable
private fun CategorySkeleton(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = PosterGrid.columnsFor(maxWidth, GridMinWidth, GridSpacing)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Spacing.Md, top = Spacing.Sm, end = Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(GridSpacing),
        ) {
            repeat(SkeletonRows) {
                Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                    repeat(columns) {
                        PosterCardSkeleton(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 最小列宽：与首页网格同值，两个页面的海报墙疏密一致。 */
private val GridMinWidth = 100.dp

/** 网格间距：与首页网格同值。 */
private val GridSpacing = Spacing.Md

/** 离底还剩几格就预取下一页。 */
private const val LoadMoreThreshold = 3

/** 骨架铺几行：一屏多一点，滚动时不留空底。 */
private const val SkeletonRows = 4

private const val ContentTypePoster = "category_poster"
private const val ContentTypeSkeleton = "category_skeleton"
