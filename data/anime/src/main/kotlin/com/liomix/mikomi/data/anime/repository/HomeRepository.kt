package com.liomix.mikomi.data.anime.repository

import com.liomix.mikomi.core.model.Anime
import kotlinx.coroutines.flow.Flow

/**
 * 首页：**轮播图**与**内容区**。
 *
 * 两个入口分开，因为它们的刷新节奏不同 —— 轮播换得慢（一天几次），内容区跟着趋势走
 * （几十分钟一变）。塞进一个 Flow，其中一方更新就会让另一方跟着重组一屏卡片。
 *
 * ## 数据只走 Bangumi
 *
 * 趋势榜（Next 的 `/p1/trending/subjects`）一份数据同时喂轮播与内容区：
 * 前几张当轮播、整页当推荐列表。AniList 的兜底链路不在这里 —— 两个源的条目 id 不在同一
 * 命名空间，混着展示会让「点开详情」在两条路径之间跳。
 *
 * ## 存储
 *
 * 走 [com.liomix.mikomi.core.datastore.cache.CacheStore]（整块 JSON），不落 Room：
 * 首页数据是「服务端上次给了什么」，没有查询需求，随时可以整块丢掉重建。
 */
interface HomeRepository {

    /** 轮播：趋势榜前几张，随缓存更新。 */
    fun observeBanners(): Flow<List<Anime>>

    /** 内容区：推荐列表（含已加载的后续页）。 */
    fun observeRecommend(): Flow<List<Anime>>

    /** 进页面时调用：缓存还新就不发请求。 */
    suspend fun refreshIfStale()

    /**
     * 加载下一页。
     *
     * @return 是否拿到了新内容；`false` 表示已到底或本次失败，列表不再显示「加载中」。
     */
    suspend fun loadMore(): Boolean

    companion object {
        /** 内容区一页条数。 */
        const val PAGE_SIZE = 20

        /** 轮播最多几张：再多横滑就没意义了，用户根本滑不到最后。 */
        const val BANNER_LIMIT = 5
    }
}
