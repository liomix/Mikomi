package com.liomix.mikomi.feature.home.rank

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RankRoute(
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RankViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RankScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onAnimeClick = onAnimeClick,
        onRetryClick = viewModel::refresh,
        modifier = modifier,
    )
}
