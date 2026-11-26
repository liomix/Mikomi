package com.liomix.mikomi.data.anime.repository.Ipml

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.datastore.cache.CacheJson
import com.liomix.mikomi.core.datastore.cache.CacheKey
import com.liomix.mikomi.core.datastore.cache.CacheStore
import com.liomix.mikomi.core.datastore.cache.CacheTtl
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiSource
import com.liomix.mikomi.data.anime.repository.HomeRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * 首页实现：趋势榜一份数据 → 轮播（前 5 张）+ 内容区（整页）。
 *
 * 两个观察入口来自**同一个缓存块**：分开存会让「轮播已刷新、列表还是旧的」这种状态
 * 真实存在，而那只是把同一个响应的两半拆坏。
 */
internal class HomeRepositoryIpml @Inject constructor(
    private val source: BangumiSource,
    private val cacheStore: CacheStore,
) : HomeRepository {

    override fun observeBanners(): Flow<List<Anime>> =
        cacheStore.observe(CacheKey.HOME).map { entry -> entry?.decode()?.banners.orEmpty() }

    override fun observeRecommend(): Flow<List<Anime>> =
        cacheStore.observe(CacheKey.HOME).map { entry -> entry?.decode()?.items.orEmpty() }

    override suspend fun refreshIfStale() {
        if (!cacheStore.isStale(CacheKey.HOME, CacheTtl.HOME)) return
        refresh()
    }

    override suspend fun refresh(): HomeRepository.LoadResult = refreshPage()

    override suspend fun loadMore(): HomeRepository.LoadResult {
        val current = cacheStore.get(CacheKey.HOME)?.decode() ?: HomeCache()

        val next = source.trending(limit = PAGE_SIZE, offset = current.nextOffset)
        if (next.isEmpty()) {
            // 空页 = 到底了。把 hasMore 落到缓存里，下次进页面不会再试。
            cacheStore.put(CacheKey.HOME, CacheJson.encode(current.copy(hasMore = false)))
            return HomeRepository.LoadResult(hasMore = false)
        }

        // 趋势榜分页会因新条目上架而重复返回，按 id 去重，避免列表出现两条一样的卡片。
        val merged = (current.items + next).distinctBy { it.id }
        cacheStore.put(
            CacheKey.HOME,
            CacheJson.encode(
                current.copy(
                    items = merged,
                    hasMore = next.size >= PAGE_SIZE,
                    nextOffset = current.nextOffset + next.size,
                ),
            ),
        )
        return HomeRepository.LoadResult(hasMore = next.size >= PAGE_SIZE, loadedCount = next.size)
    }

    /** 拉首页第一页：轮播取前几张、内容区取整页。 */
    private suspend fun refreshPage(): HomeRepository.LoadResult {
        val items = source.trending(limit = PAGE_SIZE, offset = 0)
        cacheStore.put(
            CacheKey.HOME,
            CacheJson.encode(
                HomeCache(
                    banners = items.take(BANNER_LIMIT),
                    items = items,
                    hasMore = items.size >= PAGE_SIZE,
                    nextOffset = items.size,
                ),
            ),
        )
        return HomeRepository.LoadResult(hasMore = items.size >= PAGE_SIZE, loadedCount = items.size)
    }

    private fun com.liomix.mikomi.core.datastore.cache.CacheSnapshot.Entry.decode(): HomeCache =
        CacheJson.decode<HomeCache>(payload) ?: HomeCache()

    private companion object {
        const val PAGE_SIZE = HomeRepository.PAGE_SIZE
        const val BANNER_LIMIT = HomeRepository.BANNER_LIMIT
    }
}

/**
 * 首页缓存块：轮播与内容区**必须一起存**。
 *
 * 分开存的话，「轮播已刷新、列表还是旧的」这种组合会真实存在；而它们本来就是同一个
 * 响应（趋势榜）的两半。
 *
 * [nextOffset] 存在缓存里而不是内存里：进程被回收后重新进页面，分页游标不会归零，
 * 否则会从第一页重新拉一遍、列表里多出一段重复内容。
 */
@Serializable
private data class HomeCache(
    val banners: List<Anime> = emptyList(),
    val items: List<Anime> = emptyList(),
    val hasMore: Boolean = true,
    val nextOffset: Int = 0,
)
