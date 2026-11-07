package com.liomix.mikomi.feature.home

import com.liomix.mikomi.core.model.Anime

/**
 * 首页的**唯一**状态。
 *
 * 形状是「外层 data class + 内层 sealed」而不是纯 sealed：下拉刷新指示器跨态存在
 * （骨架屏上也要能拉、错误页上也能拉），塞进 `Success` 分支就丢了。外层那一层专放
 * **跨态**字段，内层 sealed 只表达「内容现在是什么」。
 *
 * 这样 `HomeRoute` 只订阅一次，`HomeScreen` 只收一个状态参数 —— 两个独立流时，
 * 一次「刷新结束」要发两次（指示器熄灭 + 数据到达），用户看到的是两帧。
 */
data class HomeUiState(
    /** 下拉刷新指示器：仅用户主动下拉时为真，自动首载不显示。 */
    val isRefreshing: Boolean = false,
    val content: HomeContent = HomeContent.Loading,
)

/** 内容态：骨架 / 数据 / 错误。 */
sealed interface HomeContent {
    data object Loading : HomeContent

    data class Success(
        val banners: List<Anime>,
        val anime: List<Anime>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
    ) : HomeContent

    data class Error(val message: String) : HomeContent
}
