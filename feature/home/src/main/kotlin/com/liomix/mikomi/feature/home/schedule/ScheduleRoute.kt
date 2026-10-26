package com.liomix.mikomi.feature.home.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ScheduleRoute(
    onBackClick: () -> Unit,
    onAnimeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onAnimeClick = onAnimeClick,
        onRetryClick = viewModel::refresh,
        modifier = modifier,
    )
}
