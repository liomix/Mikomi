package com.liomix.mikomi.data.anime.repository.Ipml

import android.util.Log
import com.liomix.mikomi.core.datastore.cache.CacheJson
import com.liomix.mikomi.core.datastore.cache.CacheKey
import com.liomix.mikomi.core.datastore.cache.CacheStore
import com.liomix.mikomi.core.datastore.cache.CacheTtl
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.anime.RankEntry
import com.liomix.mikomi.core.model.anime.RankRegion
import com.liomix.mikomi.data.anime.AniRpbRepository
import com.liomix.mikomi.data.anime.repository.RankRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 排行榜实现：AniRpb 取两榜 → 整块写缓存 → 页面读缓存。
 *
 * 翻页与回退交给 [AniRpbRepository]：它已经处理了「一页不够」与「整榜失败退热播」，
 * 在这里再写一遍就会出现两套翻页规则，而它们迟早会不一致。
 *
 * 两榜**分别**处理失败：单榜挂掉时那一榜保留旧数据，另一榜照常更新 —— 只有两榜全挂
 * 才向上抛，让页面显示「可重试」而不是把已经能看的那一榜也变成错误页。
 */
internal class RankRepositoryIpml @Inject constructor(
    private val aniRpbRepository: AniRpbRepository,
    private val cacheStore: CacheStore,
) : RankRepository {

    override fun observe(region: RankRegion): Flow<List<RankEntry>> =
        cacheStore.observe(CacheKey.RANK).map { entry ->
            entry?.payload
                ?.let { raw -> CacheJson.decode<List<RankEntry>>(raw) }
                ?.filter { it.region == region }
                .orEmpty()
        }

    override suspend fun refreshIfStale() {
        if (cacheStore.isStale(CacheKey.RANK, CacheTtl.RANK)) refresh()
    }

    override suspend fun refresh() {
        val cached = cacheStore.get(CacheKey.RANK)?.payload
            ?.let { raw -> CacheJson.decode<List<RankEntry>>(raw) }
            .orEmpty()

        val fetched = mutableMapOf<RankRegion, List<RankEntry>>()
        var lastFailure: Exception? = null

        for (region in RankRegion.entries) {
            try {
                fetched[region] = aniRpbRepository
                    .fetchRanking(region, RankRepository.LIMIT)
                    .map { it.toRankEntry(region) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fetch rank failed, region=$region", e)
                lastFailure = e
            }
        }

        if (fetched.isEmpty()) {
            lastFailure?.let { throw it }
            return
        }

        // 空结果多半是限额或空响应，不当成「榜上真的没有」：那一榜保留旧数据。
        val merged = RankRegion.entries.flatMap { region ->
            fetched[region]?.takeIf { it.isNotEmpty() }
                ?: cached.filter { it.region == region }
        }
        cacheStore.put(CacheKey.RANK, CacheJson.encode(merged))
    }

    /** AniRpb 视频 → 榜单条目。id 带前缀，与 Bangumi 的条目 id 隔开。 */
    private fun AniRpbHotItem.toRankEntry(region: RankRegion): RankEntry = RankEntry(
        anime = Anime(
            id = "$ANIRPB_ID_PREFIX$id",
            title = title,
            coverUrl = coverUrl,
            rating = score,
            popularity = playCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            genres = tags,
        ),
        region = region,
        genres = tags,
    )

    private companion object {
        const val TAG = "RankRepository"

        /** AniRpb 视频 id 的前缀：与 Bangumi / AniList 的条目 id 不在同一命名空间。 */
        const val ANIRPB_ID_PREFIX = "anirpb:"
    }
}
