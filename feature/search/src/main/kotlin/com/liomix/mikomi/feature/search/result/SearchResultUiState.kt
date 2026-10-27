package com.liomix.mikomi.feature.search.result

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.feature.search.LoadState

/** 搜索结果页 UI 状态。 */
data class SearchResultUiState(
    /** 本次搜索的关键词。 */
    val keyword: String = "",
    /** 已加载的结果（已过滤为动画条目）。 */
    val items: List<Anime> = emptyList(),
    val state: LoadState = LoadState.Loading,
    /** 是否还有下一页。 */
    val hasMore: Boolean = false,
    /** 正在追加下一页。 */
    val loadingMore: Boolean = false,
)
