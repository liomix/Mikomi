package com.liomix.mikomi.feature.home.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CategoryRoute(
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CategoryScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onQueryChange = viewModel::onQueryChange,
        onAnimeClick = onAnimeClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}
