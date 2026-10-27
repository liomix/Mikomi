package com.liomix.mikomi.feature.search.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liomix.mikomi.core.ui.component.AnimePosterCard
import com.liomix.mikomi.core.ui.component.SkeletonBox
import com.liomix.mikomi.feature.search.LoadState
import com.liomix.mikomi.feature.search.R
import com.liomix.mikomi.feature.search.SearchCenteredHint
import com.liomix.mikomi.feature.search.SearchTopBar

/** 网格卡片最小宽度：与首页推荐区一致（手机竖屏约 3 列）。 */
private val RESULT_GRID_MIN_SIZE = 100.dp

/** 骨架屏卡片数量。 */
private const val RESULT_SKELETON_COUNT = 9

/**
 * 搜索结果页（搜索页的第二级页面）。
 *
 * 顶部与搜索页共用同一套搜索栏，可直接修改关键词再次搜索（结果原地替换，不新增层级）。
 * 卡片样式与首页推荐区完全一致（[AnimePosterCard]：2:3 封面 + 单行滚动标题）。
 */
@Composable
fun SearchResultScreen(
    state: SearchResultUiState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SearchTopBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClearQuery,
            onBack = onBack,
            // 结果页不自动聚焦，避免一进来就弹键盘遮住结果。
            autoFocus = false,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .imePadding(),
        ) {
            when (state.state) {
                LoadState.Idle,
                LoadState.Loading,
                -> ResultSkeleton()

                LoadState.Error -> SearchCenteredHint(
                    title = stringResource(R.string.search_load_failed),
                    actionLabel = stringResource(R.string.search_retry),
                    onAction = onRetry,
                )

                LoadState.Success -> {
                    if (state.items.isEmpty()) {
                        SearchCenteredHint(
                            title = stringResource(R.string.search_no_result, state.keyword),
                            subtitle = stringResource(R.string.search_no_result_hint),
                        )
                    } else {
                        ResultGrid(
                            state = state,
                            onAnimeClick = onAnimeClick,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

/** 结果网格 + 计数 + 翻页页脚。 */
@Composable
private fun ResultGrid(
    state: SearchResultUiState,
    onAnimeClick: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = RESULT_GRID_MIN_SIZE),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = state.items,
            key = { anime -> "result_${anime.id}" },
            contentType = { "result_item" },
        ) { anime ->
            AnimePosterCard(
                anime = anime,
                onClick = { onAnimeClick(anime.id) },
            )
        }
        item(
            key = "result_footer",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            ResultFooter(
                hasMore = state.hasMore,
                loadMoreKey = state.items.size,
                onLoadMore = onLoadMore,
            )
        }
    }
}

/** 网格底部：滚到底自动翻页（没有更多时不再显示任何提示）。 */
@Composable
private fun ResultFooter(
    hasMore: Boolean,
    loadMoreKey: Int,
    onLoadMore: () -> Unit,
) {
    if (!hasMore) return

    // 底部进入可视区即预取下一页；结果数量变化后会再次触发，直到填满或没有更多。
    LaunchedEffect(loadMoreKey) {
        onLoadMore()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
        )
    }
}

/** 骨架屏网格：与首页推荐区同构（海报块 + 标题条）。 */
@Composable
private fun ResultSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = RESULT_GRID_MIN_SIZE),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = RESULT_SKELETON_COUNT,
            key = { "result_skeleton_$it" },
            contentType = { "result_skeleton" },
        ) {
            Column {
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
    }
}
