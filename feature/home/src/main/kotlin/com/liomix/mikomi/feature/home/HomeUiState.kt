package com.liomix.mikomi.feature.home

import com.liomix.mikomi.core.model.Anime

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val banners: List<Anime>,
        val anime: List<Anime>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}
