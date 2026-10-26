package com.liomix.mikomi.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRoute(
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onActionClick: (HomeQuickAction) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        isRefreshing = isRefreshing,
        onAnimeClick = onAnimeClick,
        onSearchClick = onSearchClick,
        onProfileClick = onProfileClick,
        onActionClick = onActionClick,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        modifier = modifier,
    )
}
