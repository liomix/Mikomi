package com.liomix.mikomi.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 详情页 Route（有状态）：绑定 animeId 到 ViewModel，收集状态，回调上抛。
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
    val boundAnimeId by viewModel.boundAnimeId.collectAsStateWithLifecycle()
    val creditsState by viewModel.creditsState.collectAsStateWithLifecycle()
    val creditDetailState by viewModel.creditDetailState.collectAsStateWithLifecycle()
    val creditCommentsState by viewModel.creditCommentsState.collectAsStateWithLifecycle()
    val teasingState by viewModel.teasingState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()

    // 共享 ViewModel 会残留上一部番的 Success，bind 又要等首帧之后才跑；
    // 绑定前一律按加载态处理，避免首帧闪出上一部番（看起来就是「点 A 打开了 B」）。
    val shownState = if (boundAnimeId == animeId) uiState else DetailUiState.Loading

    DetailScreen(
        uiState = shownState,
        creditsState = creditsState,
        creditDetailState = creditDetailState,
        creditCommentsState = creditCommentsState,
        teasingState = teasingState,
        isFavorite = isFavorite,
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
