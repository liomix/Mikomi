package com.liomix.mikomi.feature.home.rank

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 排行榜 Route（有状态）：收集榜单与刷新状态，刷新入口统一收敛到 ViewModel。
 *
 * 回调按标题而不是 id：榜单来自 AniRpb，其视频 id 与详情页的条目 id 空间不同。
 */
@Composable
fun RankRoute(
    onBackClick: () -> Unit,
    onAnimeTitleClick: (String) -> Unit,
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
        onAnimeTitleClick = onAnimeTitleClick,
        modifier = modifier,
    )
}
