package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.model.RankRegion
import kotlinx.coroutines.flow.Flow

/**
 * 热播榜数据门面：日漫 / 国漫两榜，Room 为单一可信来源。
 *
 * 榜单只取 AniRpb 的分类整榜（`video.categoryList`，sort=hottest），每榜 100 条，
 * 顺序沿用服务端名次，不接 Bangumi / AniList，也不做跨源降级。
 */
interface RankRepository {

    /** 观察指定产地榜单（按服务端名次升序的本地数据）。 */
    fun observeRank(region: RankRegion): Flow<List<RankEntry>>

    /** 从 AniRpb 拉取日漫 / 国漫整榜并回写本地；两榜都失败时向上抛出。 */
    suspend fun refreshRank()
}
