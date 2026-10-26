package com.liomix.mikomi.feature.detail

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.Episode

/** 详情页加载状态。 */
sealed interface DetailUiState {
    data object Loading : DetailUiState

    data class Success(
        val anime: Anime,
        val episodes: List<Episode>,
    ) : DetailUiState

    data object Error : DetailUiState
}
