package com.liomix.mikomi.feature.detail

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AnimeCharacter
import com.liomix.mikomi.core.model.CreditComment
import com.liomix.mikomi.core.model.CreditDetail
import com.liomix.mikomi.core.model.StaffMember
import com.liomix.mikomi.core.model.TeasingComment

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

/** 主创详情弹层的状态。 */
sealed interface CreditDetailUiState {
    /** 弹层未打开。 */
    data object Hidden : CreditDetailUiState

    data object Loading : CreditDetailUiState

    data class Loaded(val detail: CreditDetail) : CreditDetailUiState

    data object Error : CreditDetailUiState
}

/** 主创吐槽/评论的状态。 */
sealed interface CreditCommentsUiState {
    /** 弹层未打开。 */
    data object Hidden : CreditCommentsUiState

    data object Loading : CreditCommentsUiState

    data class Loaded(val comments: List<CreditComment>) : CreditCommentsUiState

    data object Error : CreditCommentsUiState
}

/** 吐槽 Tab 状态（分页懒加载）。 */
sealed interface TeasingUiState {
    /** 首屏加载中。 */
    data object Loading : TeasingUiState

    data object Error : TeasingUiState

    data class Loaded(
        val comments: List<TeasingComment>,
        /** 是否还有更多（服务端仍有下一页）。 */
        val hasMore: Boolean,
        /** 正在加载下一页。 */
        val appending: Boolean = false,
        /** 下一页加载失败（可点击重试）。 */
        val appendFailed: Boolean = false,
    ) : TeasingUiState
}
