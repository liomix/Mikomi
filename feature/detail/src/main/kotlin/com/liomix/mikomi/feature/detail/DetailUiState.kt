package com.liomix.mikomi.feature.detail

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AnimeCharacter
import com.liomix.mikomi.core.model.StaffMember

/** 详情页加载状态。 */
sealed interface DetailUiState {
    data object Loading : DetailUiState

    data class Success(
        val anime: Anime,
    ) : DetailUiState

    data object Error : DetailUiState
}

/** 主创 Tab 的加载状态（懒加载：进入主创 Tab 才拉取）。 */
sealed interface AnimeCreditsUiState {
    /** 尚未加载（首次进入主创 Tab 前）。 */
    data object Idle : AnimeCreditsUiState

    data object Loading : AnimeCreditsUiState

    data class Loaded(
        val characters: List<AnimeCharacter>,
        val staff: List<StaffMember>,
    ) : AnimeCreditsUiState

    data object Error : AnimeCreditsUiState
}
