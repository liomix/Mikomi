package com.liomix.mikomi.feature.home

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.SliderImage

/**
 * 首页的**唯一**状态。
 *
 * 形状是「外层 data class + 内层 sealed」而不是纯 sealed：下拉刷新指示器跨态存在
 * （骨架屏上也要能拉、错误页上也能重试），塞进 `Success` 分支就丢了。外层那一层专放
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

/**
 * 内容态：骨架 / 数据 / 错误。
 *
 * 没有「空态」：一个数据都没有时停在 [Loading]，界面就是骨架屏本身 —— 空的番剧列表
 * 不构成可展示的信息，切一块空占位只会让人以为坏了。这与排行榜、时间表同一口径。
 */
sealed interface HomeContent {
    data object Loading : HomeContent

    data class Success(
        /** 轮播图：由 [com.liomix.mikomi.data.anime.repository.SliderImageRepository] 单独给出。 */
        val sliders: List<SliderImage>,
        /** 内容区：海报网格。 */
        val anime: List<Anime>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
    ) : HomeContent

    /** 只有「没有任何本地数据、且最近一次刷新失败」才会到这里；文案由页面统一给。 */
    data object Error : HomeContent
}
