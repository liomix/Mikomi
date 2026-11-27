package com.liomix.mikomi.data.anime.repository.Impl

import android.util.Log
import com.liomix.mikomi.core.cache.CacheJson
import com.liomix.mikomi.core.cache.CacheKey
import com.liomix.mikomi.core.cache.disk.DiskCache
import com.liomix.mikomi.core.cache.CacheTtl
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.AnimeSource
import com.liomix.mikomi.core.model.anime.RankEntry
import com.liomix.mikomi.core.model.anime.RankRegion
import com.liomix.mikomi.data.anime.remote.anirpb.AniRpbSource
import com.liomix.mikomi.data.anime.repository.RankRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 排行榜实现：AniRpb 取两榜 → 整块写缓存 → 页面读缓存。
 *
 * 「攒够 100 条」与「整榜失败退热播」是本层策略，不是数据源的职责 —— 所以它们在这里，
 * 而不是在 [AniRpbSource] 里。数据源只回答「第 N 页是什么」。
 *
 * 两榜**分别**处理失败：单榜挂掉时那一榜保留旧数据，另一榜照常更新 —— 只有两榜全挂
 * 才向上抛，让页面显示「可重试」而不是把已经能看的那一榜也变成错误页。
 */
internal class RankRepositoryImpl @Inject constructor(
    private val source: AniRpbSource,
    private val cacheStore: DiskCache,
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
                fetched[region] = fetchRanking(region, RankRepository.LIMIT)
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

    /** 整榜攒够 [limit] 条就停；接口挂了退回首页 hot 模块（短榜胜过空榜）。 */
    private suspend fun fetchRanking(region: RankRegion, limit: Int): List<AniRpbHotItem> {
        val ranked = try {
            fetchRankPages(region, limit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "整榜接口失败，回退 home.load 热播榜（region=$region）", e)
            emptyList()
        }
        return ranked.ifEmpty { source.hotRanking(region).take(limit) }
    }

    /**
     * 整榜分页：攒够 [limit]、服务端没有下一页、或达到页数上限时停。
     *
     * 服务端对 `pageSize` 的实际上限可能低于请求值，所以不能假设「一页就够」。
     */
    private suspend fun fetchRankPages(region: RankRegion, limit: Int): List<AniRpbHotItem> {
        val items = mutableListOf<AniRpbHotItem>()
        var page = FIRST_PAGE

        while (items.size < limit && page <= MAX_RANK_PAGES) {
            val result = source.rankingPage(region = region, page = page, pageSize = RANK_PAGE_SIZE)
            if (result.items.isEmpty()) break
            items += result.items
            if (result.totalPages in 1..page) break
            page++
        }

        // id 缺失时用标题兜底：否则 distinctBy 会把所有空 id 的条目并成一条。
        return items.distinctBy { it.id.ifBlank { it.title } }.take(limit)
    }

    /** AniRpb 视频 → 榜单条目。id 带前缀，与 Bangumi 的条目 id 隔开。 */
    private fun AniRpbHotItem.toRankEntry(region: RankRegion): RankEntry = RankEntry(
        anime = Anime(
            id = AnimeSource.ANIRPB.prefix + id,
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

        const val FIRST_PAGE = 1

        /** 整榜单页条数：服务端可能按更小的上限裁剪，靠翻页补足。 */
        const val RANK_PAGE_SIZE = 100

        /** 翻页上限（100 条 × 10 页远大于目标，纯属防御）。 */
        const val MAX_RANK_PAGES = 10
    }
}
