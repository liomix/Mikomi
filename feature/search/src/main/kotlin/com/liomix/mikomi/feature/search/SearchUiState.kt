package com.liomix.mikomi.feature.search

import com.liomix.mikomi.core.model.Anime

/** 搜索页当前展示的内容区。 */
enum class SearchPhase {
    /** 未输入：搜索历史 + 热门推荐。 */
    BROWSE,

    /** 输入中：实时联想。 */
    SUGGEST,
}

/** 单个数据区块的加载状态。 */
enum class LoadState {
    Idle,
    Loading,
    Success,
    Error,
}

/**
 * 搜索页 UI 状态（单一可信来源）。
 *
 * 搜索结果不在这里：结果展示是搜索页的下一级页面（`SearchResultDestination`），
 * 由 `feature.search.result` 包内的 ViewModel 独立管理。
 */
data class SearchUiState(
    val query: String = "",
    val phase: SearchPhase = SearchPhase.BROWSE,
    /** 搜索历史（最新在前）。 */
    val history: List<String> = emptyList(),
    /** 热门推荐（Bangumi 趋势）。 */
    val trending: List<Anime> = emptyList(),
    val trendingState: LoadState = LoadState.Loading,
    /** 实时联想结果。 */
    val suggestions: List<Anime> = emptyList(),
    val suggestionsState: LoadState = LoadState.Idle,
)
