package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.Anime
import kotlinx.coroutines.flow.Flow

/** 首页数据门面：banner 轮播 + 推荐列表，均为本地（Room）数据观察。 */
interface HomeRepository {

    /** 首页轮播图（热度前 N）。 */
    fun observeBanners(): Flow<List<Anime>>

    /** 首页推荐列表（按热度降序）。 */
    fun observeRecommended(): Flow<List<Anime>>

    /** 从 AniList 拉取 trending 并回写本地。 */
    suspend fun refreshHome()
}
