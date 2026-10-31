package com.liomix.mikomi.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 搜索页 Route（有状态）。
 *
 * 提交搜索时先让 ViewModel 记录历史，再把关键词交给导航层跳转结果页；
 * 从结果页返回后本页状态（输入框关键词、历史、热播榜 Tab）原样保留。
 */
@Composable
fun SearchRoute(
    onBackClick: () -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val submitSearch: (String) -> Unit = { keyword ->
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty()) {
            viewModel.onSearchSubmitted(trimmed)
            onSearch(trimmed)
        }
    }

    // 搜索页离开（去结果页 / 返回上一页）时才把内容复位为浏览态，
    // 避免点击联想的瞬间先闪一下「联想 → 历史」的切换。
    DisposableEffect(Unit) {
        onDispose { viewModel.onPageHidden() }
    }

    SearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = submitSearch,
        onClear = viewModel::clearQuery,
        onBack = onBackClick,
        onRemoveHistory = viewModel::removeHistory,
        onClearHistory = viewModel::clearHistory,
        onHotTabChange = viewModel::onHotTabChange,
        // 热播榜目前只展示 AniRpb 数据；点击先按标题进入 Mikomi 的搜索结果，
        // 待详情/播放层接入 AniRpb 后可直接跳到 AniRpb 详情。
        onHotItemClick = { item -> submitSearch(item.title) },
        onRetryHot = { viewModel.retryHot() },
        modifier = modifier,
    )
}
