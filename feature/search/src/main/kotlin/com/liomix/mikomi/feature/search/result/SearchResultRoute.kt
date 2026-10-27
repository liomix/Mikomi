package com.liomix.mikomi.feature.search.result

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 搜索结果页 Route（有状态）：绑定导航参数里的关键词、收集状态、回调上抛。
 */
@Composable
fun SearchResultRoute(
    keyword: String,
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchResultViewModel = hiltViewModel(),
) {
    viewModel.bind(keyword)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SearchResultScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::submitSearch,
        onClearQuery = viewModel::clearQuery,
        onBack = onBackClick,
        onAnimeClick = onAnimeClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}
