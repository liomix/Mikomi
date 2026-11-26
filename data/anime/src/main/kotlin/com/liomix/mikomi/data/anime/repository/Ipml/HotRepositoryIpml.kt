package com.liomix.mikomi.data.anime.repository.Ipml

import com.liomix.mikomi.core.datastore.cache.CacheJson
import com.liomix.mikomi.core.datastore.cache.CacheKey
import com.liomix.mikomi.core.datastore.cache.CacheStore
import com.liomix.mikomi.core.datastore.cache.CacheTtl
import com.liomix.mikomi.core.model.anime.RankRegion
import com.liomix.mikomi.core.model.anime.HotRanking
import com.liomix.mikomi.data.anime.AniRpbRepository
import com.liomix.mikomi.data.anime.remote.anirpb.AniRpbMapper.toHotRanking
import com.liomix.mikomi.data.anime.repository.HotRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 热播榜实现：AniRpb 取两榜 → 整块写缓存 → 页面读缓存。
 *
 * 两榜**一起成功才写**：这里是「首页那个短榜」，只给十几条，少半边的观感比整块旧数据
 * 更差（用户会以为国漫区就是空的）。与排行榜不同 —— 那边每榜 100 条，缺一半仍可用。
 */
internal class HotRepositoryIpml @Inject constructor(
    private val aniRpbRepository: AniRpbRepository,
    private val cacheStore: CacheStore,
) : HotRepository {

    override fun observe(region: RankRegion): Flow<List<HotRanking>> =
        cacheStore.observe(CacheKey.HOT).map { entry ->
            entry?.payload
                ?.let { raw -> CacheJson.decode<Map<RankRegion, List<HotRanking>>>(raw) }
                ?.get(region)
                .orEmpty()
        }

    override suspend fun refreshIfStale() {
        if (cacheStore.isStale(CacheKey.HOT, CacheTtl.HOT)) refresh()
    }

    override suspend fun refresh() {
        val fetched = mutableMapOf<RankRegion, List<HotRanking>>()
        for (region in RankRegion.entries) {
            try {
                fetched[region] = aniRpbRepository.fetchHotRanking(region).map { it.toHotRanking() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 任一榜失败就整块跳过：让已有的旧榜单继续显示，而不是换成一个半空的。
                return
            }
        }
        cacheStore.put(CacheKey.HOT, CacheJson.encode(fetched))
    }
}
