package com.liomix.mikomi.data.anime.repository.Impl

import com.liomix.mikomi.core.cache.CacheJson
import com.liomix.mikomi.core.cache.CacheKey
import com.liomix.mikomi.core.cache.CacheTtl
import com.liomix.mikomi.core.cache.disk.DiskCache
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiSource
import com.liomix.mikomi.data.anime.repository.HomeContentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * 内容区：趋势榜整页，自己一块缓存（`CacheKey.HOME`）、自己的 TTL。
 *
 * 只装列表，不装轮播 —— 轮播在 [SliderImageRepositoryImpl]，两者不共享任何写入点，
 * 于是也不存在「谁先写、谁后写」的协调。
 */
internal class HomeContentRepositoryImpl @Inject constructor(
    private val source: BangumiSource,
    private val cacheStore: DiskCache,
) : HomeContentRepository {

    override fun observeContent(): Flow<List<Anime>> =
        cacheStore.observe(CacheKey.HOME)
            .map { entry -> entry?.decode()?.items.orEmpty() }
            .distinctUntilChanged()

    override suspend fun refreshIfStale() {
        if (!cacheStore.isStale(CacheKey.HOME, CacheTtl.HOME)) return
        refresh()
    }

    override suspend fun refresh(): Boolean = refreshFirstPage()

    override suspend fun loadMore(): Boolean {
        val current = cacheStore.get(CacheKey.HOME)?.decode() ?: HomeContentCache()

        val next = source.trending(limit = PAGE_SIZE, offset = current.nextOffset)
        if (next.isEmpty()) {
            // 空页 = 到底了。把 hasMore 落到缓存里，下次进页面不会再试。
            cacheStore.put(CacheKey.HOME, CacheJson.encode(current.copy(hasMore = false)))
            return false
        }

        // 趋势榜分页会因新条目上架而重复返回，按 id 去重，避免列表出现两条一样的卡片。
        val merged = (current.items + next).distinctBy { it.id }
        val hasMore = next.size >= PAGE_SIZE
        cacheStore.put(
            CacheKey.HOME,
            CacheJson.encode(
                current.copy(
                    items = merged,
                    hasMore = hasMore,
                    nextOffset = current.nextOffset + next.size,
                ),
            ),
        )
        return hasMore
    }

    /** 拉第一页，游标归零。 */
    private suspend fun refreshFirstPage(): Boolean {
        val items = source.trending(limit = PAGE_SIZE, offset = 0)
        val hasMore = items.size >= PAGE_SIZE
        cacheStore.put(
            CacheKey.HOME,
            CacheJson.encode(
                HomeContentCache(
                    items = items,
                    hasMore = hasMore,
                    nextOffset = items.size,
                ),
            ),
        )
        return hasMore
    }

    private fun com.liomix.mikomi.core.cache.CacheSnapshot.Entry.decode(): HomeContentCache =
        CacheJson.decode<HomeContentCache>(payload) ?: HomeContentCache()

    private companion object {
        const val PAGE_SIZE = HomeContentRepository.PAGE_SIZE
    }
}

/**
 * 内容区缓存块。
 *
 * [nextOffset] 存在缓存里而不是内存里：进程被回收后重新进页面，分页游标不会归零，
 * 否则会从第一页重新拉一遍、列表里多出一段重复内容。
 *
 * 旧版本的这块里还有 `banners` 字段 —— 靠 [CacheJson] 的 `ignoreUnknownKeys` 直接读得动，
 * 不需要迁移，也不需要让首页空一次。
 */
@Serializable
private data class HomeContentCache(
    val items: List<Anime> = emptyList(),
    val hasMore: Boolean = true,
    val nextOffset: Int = 0,
)
