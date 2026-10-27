package com.liomix.mikomi.feature.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 搜索页（无状态）。
 *
 * 只负责「输入」：历史 + 热门 / 实时联想两种浏览态；
 * 提交后由调用方跳转到搜索结果页（搜索页的第二级页面）。
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onRetryTrending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SearchTopBar(
            query = state.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            onBack = onBack,
        )

        // 内容区随键盘上浮，避免联想被输入法遮挡。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding(),
        ) {
            Crossfade(
                targetState = state.phase,
                animationSpec = tween(durationMillis = 180),
                label = "searchPhase",
            ) { phase ->
                when (phase) {
                    SearchPhase.BROWSE -> SearchBrowseSection(
                        state = state,
                        onHistoryClick = { keyword ->
                            onSearch(keyword)
                            keyboardController?.hide()
                        },
                        onRemoveHistory = onRemoveHistory,
                        onClearHistory = onClearHistory,
                        onAnimeClick = onAnimeClick,
                        onRetryTrending = onRetryTrending,
                    )

                    SearchPhase.SUGGEST -> SearchSuggestionList(
                        keyword = state.query,
                        suggestions = state.suggestions,
                        state = state.suggestionsState,
                        onSuggestionClick = { keyword ->
                            onSearch(keyword)
                            keyboardController?.hide()
                        },
                    )
                }
            }
        }
    }
}
