package com.liomix.mikomi.data.anime

import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.model.RankRegion
import kotlinx.coroutines.flow.Flow

/**
 * 综合评分榜数据门面：国漫 / 日漫两榜，Room 为单一可信来源。
 *
 * 排序口径为数据源综合评分（AniList averageScore，已换算 0–10）降序。
 */
interface RankRepository {

    /** 观察指定产地榜单（按综合评分降序的本地数据）。 */
    fun observeRank(region: RankRegion): Flow<List<RankEntry>>

    /** 从 AniList 拉取国漫 / 日漫评分榜并整体回写本地；失败向上抛出。 */
    suspend fun refreshRank()
}
