package com.liomix.mikomi.data.anime.internal

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.datastore.cache.CacheJson
import com.liomix.mikomi.core.datastore.cache.CacheKey
import com.liomix.mikomi.core.datastore.cache.CacheStore
import com.liomix.mikomi.core.datastore.cache.CacheTtl
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.RankRegion
import com.liomix.mikomi.data.anime.AniRpbRepository
import com.liomix.mikomi.data.anime.HotRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 两榜合成一份 payload：它们来自同一次页面加载，拆成两条只会带来「刷了一半」的状态。
 *
 * 排行榜存两榜是同一个理由，区别只在这里落在 JSON 而不是 Room。
 */
internal class HotRepositoryImpl @Inject constructor(
    private val aniRpbRepository: AniRpbRepository,
    private val cacheStore: CacheStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HotRepository {

    override fun observeHot(region: RankRegion): Flow<List<AniRpbHotItem>> =
        cacheStore.observe(CacheKey.HOT).map { entry ->
            entry?.payload
                ?.let { raw -> CacheJson.decode<Map<RankRegion, List<AniRpbHotItem>>>(raw) }
                ?.get(region)
                .orEmpty()
        }

    override suspend fun refreshHotIfStale() {
        if (cacheStore.isStale(CacheKey.HOT, CacheTtl.HOT)) refreshHot()
    }

    override suspend fun refreshHot(): Unit = withContext(ioDispatcher) {
        // 两榜都拿到才写；任一榜失败就整块保留旧缓存 —— 半个榜单比旧榜单更难看。
        val bundle = RankRegion.entries.associateWith { region ->
            aniRpbRepository.fetchHotRanking(region)
        }
        cacheStore.put(CacheKey.HOT, CacheJson.encode(bundle))
    }
}
