package com.liomix.mikomi.data.anime.repository

import com.liomix.mikomi.core.model.anime.RankEntry
import com.liomix.mikomi.core.model.anime.RankRegion
import kotlinx.coroutines.flow.Flow

/**
 * 排行榜：日漫 / 国漫两榜，**只走 AniRpb**。
 *
 * 不用 Bangumi 的榜单：它的排名是「全站综合」，不区分产地；而这个页面要的正是
 * 「日漫第 N」与「国漫第 N」两条独立的榜。两个源的排名口径不同，混着用会让
 * 「第 12 名」指代不明。
 *
 * 两榜在同一个缓存块里：它们来自同一次刷新，拆开会出现「日漫已更新、国漫还是昨天的」。
 */
interface RankRepository {

    /** 观察某一产地的榜单（按服务端名次升序）。 */
    fun observe(region: RankRegion): Flow<List<RankEntry>>

    /** 进页面时调用：缓存还新就不发请求。 */
    suspend fun refreshIfStale()

    /** 无条件刷新两榜；单榜失败保留该榜旧数据，两榜都失败才抛。 */
    suspend fun refresh()

    companion object {
        /** 单榜条数：两榜各取前 100。 */
        const val LIMIT = 100
    }
}
