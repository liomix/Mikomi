package com.liomix.mikomi.feature.search.result

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.feature.search.LoadState

/** 搜索结果页 UI 状态。 */
data class SearchResultUiState(
    /** 当前结果对应的关键词（已 trim）。 */
    val keyword: String = "",
    /** 搜索框内容（可编辑，提交后才生效）。 */
    val query: String = "",
    /** 已加载的结果（已过滤为动画条目）。 */
    val items: List<Anime> = emptyList(),
    val state: LoadState = LoadState.Loading,
    /** 是否还有下一页。 */
    val hasMore: Boolean = false,
    /** 正在追加下一页。 */
    val loadingMore: Boolean = false,
)
