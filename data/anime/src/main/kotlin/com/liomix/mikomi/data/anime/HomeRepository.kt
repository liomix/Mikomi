package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.Anime
import kotlinx.coroutines.flow.Flow

/** 首页数据门面：banner 轮播 + 推荐列表，均为本地（Room）数据观察。 */
interface HomeRepository {

    /** 首页轮播图（最新热门：按评分降序前 N）。 */
    fun observeBanners(): Flow<List<Anime>>

    /** 首页推荐列表（按热度降序，含分页追加的全部数据）。 */
    fun observeRecommended(): Flow<List<Anime>>

    /** 刷新：拉取第一页并回写本地（Bangumi 主，失败降级 AniList）。 */
    suspend fun refreshHome(): LoadMoreResult

    /** 加载下一页并追加回写本地。 */
    suspend fun loadMore(offset: Int): LoadMoreResult
}

/** 分页加载结果。 */
data class LoadMoreResult(
    val hasMore: Boolean,
    val loadedCount: Int,
)
