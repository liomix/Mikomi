package com.liomix.mikomi.feature.home.rank

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 排行榜 Route（有状态）：收集榜单与刷新状态，刷新入口统一收敛到 ViewModel。
 */
@Composable
fun RankRoute(
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RankViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    RankScreen(
        uiState = uiState,
        isRefreshing = isRefreshing,
        onBackClick = onBackClick,
        onRefreshClick = viewModel::refreshByUser,
        onAnimeClick = onAnimeClick,
        modifier = modifier,
    )
}
