package com.liomix.mikomi.data.anime.repository

import com.liomix.mikomi.core.model.anime.RankRegion
import com.liomix.mikomi.core.model.anime.HotRanking
import kotlinx.coroutines.flow.Flow

/**
 * 热播榜：日漫 / 国漫两榜，**只走 AniRpb**。
 *
 * 与 [RankRepository] 的区别是数据源与用途：排行榜要「整榜前 100」（`video.categoryList`），
 * 热播榜要「首页那个 hot 模块」（`home.load`，条数由服务端决定）。两者都来自 AniRpb，
 * 但一个求全、一个求快。
 *
 * 两榜存在同一个缓存块：它们来自同一次刷新，拆开会出现「日漫已更新、国漫还是旧的」。
 */
interface HotRepository {

    /** 观察某一产地的热播榜（按播放量降序，名次从 1 开始）。 */
    fun observe(region: RankRegion): Flow<List<HotRanking>>

    /** 进页面时调用：缓存还新就不发请求。 */
    suspend fun refreshIfStale()

    /** 无条件刷新两榜；任一榜失败都整块保留旧缓存 —— 半个榜单比旧榜单更难看。 */
    suspend fun refresh()
}
