package com.liomix.mikomi.data.anime.internal

import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.datastore.cache.CacheJson
import com.liomix.mikomi.core.datastore.cache.CacheKey
import com.liomix.mikomi.core.datastore.cache.CacheStore
import com.liomix.mikomi.core.datastore.cache.CacheTtl
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.model.RankRegion
import com.liomix.mikomi.data.anime.AniRpbRepository
import com.liomix.mikomi.data.anime.RankRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 热播榜：AniRpb `video.categoryList`（分类整榜，sort=hottest）为唯一数据源，两榜各 100 条。
 *
 * 本地副本走 [CacheStore]（一整块 JSON），不再是 Room 的 `rank` 表 —— 这一页要的只是
 * 「上次服务端给了什么」，没有查询、没有关联，所以「可丢弃」比「可查询」更重要。
 *
 * 两榜合并成一个 payload：它们来自同一次刷新，拆成两条只会带来「刷新一半」的状态。
 */
internal class RankRepositoryImpl @Inject constructor(
    private val aniRpbRepository: AniRpbRepository,
    private val cacheStore: CacheStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RankRepository {

    override fun observeRank(region: RankRegion): Flow<List<RankEntry>> =
        cacheStore.observe(CacheKey.RANK).map { entry ->
            entry?.payload
                ?.let { raw -> CacheJson.decode<List<RankEntry>>(raw) }
                ?.filter { it.region == region }
                .orEmpty()
        }

    override suspend fun refreshIfStale() {
        if (cacheStore.isStale(CacheKey.RANK, CacheTtl.RANK)) refreshRank()
    }

    override suspend fun refreshRank(): Unit = withContext(ioDispatcher) {
        val cached = cacheStore.get(CacheKey.RANK)?.payload
            ?.let { raw -> CacheJson.decode<List<RankEntry>>(raw) }
            .orEmpty()

        val fetched = mutableMapOf<RankRegion, List<RankEntry>>()
        var lastFailure: Exception? = null

        for (region in RankRegion.entries) {
            try {
                fetched[region] = fetchRank(region)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fetch AniRpb rank failed, region=$region", e)
                lastFailure = e
            }
        }

        // 两榜都失败才向上抛；单榜失败时那一榜继续用缓存里的旧数据。
        if (fetched.isEmpty()) {
            lastFailure?.let { throw it }
            return@withContext
        }

        // 空结果多半是限额或空响应，不当成「榜上真的没有」：那一榜保留旧数据。
        val merged = RankRegion.entries.flatMap { region ->
            fetched[region]?.takeIf { it.isNotEmpty() }
                ?: cached.filter { it.region == region }
        }
        cacheStore.put(CacheKey.RANK, CacheJson.encode(merged))
    }

    /** AniRpb 整榜：名次取接口返回顺序（第 N 条 = position N-1）。 */
    private suspend fun fetchRank(region: RankRegion): List<RankEntry> {
        val items = aniRpbRepository.fetchRanking(region, RANK_LIMIT)
        Log.i(TAG, "整榜拉取完成 region=$region 条数=${items.size}")
        return items.map { it.toRankEntry(region) }
    }

    private fun AniRpbHotItem.toRankEntry(region: RankRegion): RankEntry {
        val anime = Anime(
            id = "$ANIRPB_ID_PREFIX$id",
            title = title,
            coverUrl = coverUrl,
            // AniRpb 只给评分与更新文案，没有简介与类型标签。
            rating = score,
        )
        return RankEntry(
            anime = anime,
            region = region,
            genres = emptyList(),
        )
    }

    private companion object {
        /** 主键前缀：AniRpb 视频 id 与 Bangumi / AniList 条目 id 不在同一命名空间。 */
        const val ANIRPB_ID_PREFIX = "anirpb:"

        /** 单榜条数：日漫 / 国漫各 100 条。 */
        const val RANK_LIMIT = 100

        const val TAG = "RankRepository"
    }
}
