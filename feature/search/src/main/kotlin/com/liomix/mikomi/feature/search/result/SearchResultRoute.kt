package com.liomix.mikomi.feature.search.result

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liomix.mikomi.feature.search.LoadState

/**
 * 搜索结果页 Route（有状态）：绑定导航参数里的关键词、收集状态、回调上抛。
 *
 * 用 [LaunchedEffect] 而不是在组合中直接调用 [SearchResultViewModel.bind]：
 * ViewModel 在 Activity 级共享，每次进入本页都必须按导航参数重新加载，
 * 否则会沿用上一次搜索的结果（搜过 A 再搜 B，结果仍是 A）。
 * 页内改关键词再次搜索不改变导航参数，因此不会触发这里重复加载。
 */
@Composable
fun SearchResultRoute(
    keyword: String,
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchResultViewModel = hiltViewModel(),
) {
    // 已按当前导航参数发起过加载的标记：绑定完成前先按加载中展示，
    // 避免首帧闪一下上一次搜索的残留结果。
    var boundKeyword by remember(keyword) { mutableStateOf<String?>(null) }
    LaunchedEffect(keyword) {
        viewModel.bind(keyword)
        boundKeyword = keyword
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val displayState = if (boundKeyword == keyword) {
        state
    } else {
        SearchResultUiState(keyword = keyword, state = LoadState.Loading)
    }

    SearchResultScreen(
        state = displayState,
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
