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
    onBackClick: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    viewModel.bind(animeId)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onEpisodeClick = onEpisodeClick,
        onPlayClick = onPlayClick,
        modifier = modifier,
    )
}
