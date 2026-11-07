package com.liomix.mikomi.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 详情页 Route（有状态）：绑定 animeId 到 ViewModel，收集**一次**状态，回调上抛。
 *
 * 这里曾经收集七次（内容、绑定 id、收藏、主创、主创弹层、弹层评论、吐槽）。七次订阅意味着
 * 七个 collector、七个 lifecycle 观察者，以及 `DetailScreen` 六个状态参数 —— 而其中没有一个是
 * 高频值。ViewModel 现在只导出一条 [DetailViewModel.uiState]，这个 Route 的形状也就固定了：
 * 一次 `collectAsStateWithLifecycle`、一个 `when`、一组回调。
 *
 * 高频值仍不进这条流：播放进度在 `PlayerPlaybackState` 那个 holder 里，可见「一屏一状态」
 * 说的是**低频状态**，不是"把所有东西都塞进一个对象"。
 */
@Composable
fun DetailRoute(
    animeId: String,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    // 用 LaunchedEffect 绑定参数（不在组合中直接调用），
    // 避免「闪一下上一个动漫详情」的副作用。
    LaunchedEffect(animeId) {
        viewModel.bind(animeId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // bind 要等首帧之后才跑，在那之前 uiState 里还带着上一个 entry 的 id（ViewModel 按 entry 存活，
    // 但同一个 entry 内参数仍会变）。对一次账：不一致就按「这一部、还没加载」渲染，
    // 否则表现出来就是「点 A 打开了 B」。
    val shownState = if (uiState.boundId == animeId) {
        uiState
    } else {
        DetailUiState(boundId = animeId)
    }

    DetailScreen(
        uiState = shownState,
        onToggleFavorite = viewModel::toggleFavorite,
        onLoadCredits = viewModel::loadCredits,
        onCharacterClick = { id -> viewModel.openCreditDetail(id, isCharacter = true) },
        onStaffClick = { id -> viewModel.openCreditDetail(id, isCharacter = false) },
        onDismissCreditDetail = viewModel::dismissCreditDetail,
        onLoadTeasing = viewModel::loadTeasing,
        onLoadMoreTeasing = viewModel::loadMoreTeasing,
        onRetryTeasing = viewModel::retryTeasing,
        onPlayClick = onPlayClick,
        modifier = modifier,
    )
}
