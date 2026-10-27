package com.liomix.mikomi.feature.detail

import androidx.compose.runtime.Composable
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
    viewModel.bind(animeId)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val creditsState by viewModel.creditsState.collectAsStateWithLifecycle()
    val creditDetailState by viewModel.creditDetailState.collectAsStateWithLifecycle()
    val creditCommentsState by viewModel.creditCommentsState.collectAsStateWithLifecycle()
    val teasingState by viewModel.teasingState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()

    DetailScreen(
        uiState = uiState,
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
