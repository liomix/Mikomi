package com.liomix.mikomi.data.anime.repository

import com.liomix.mikomi.core.model.Anime
import kotlinx.coroutines.flow.Flow

/**
 * 首页**内容区**：趋势榜整页，也就是海报网格。
 *
 * ## 与轮播完全独立
 *
 * 轮播是 [SliderImageRepository]。两者各有自己的缓存块（`CacheKey.HOME` / `CacheKey.BANNER`）、
 * 自己的 TTL、自己的请求 —— 任一方换源、改刷新节奏、甚至下线，都不牵动另一方。
 * 代价是进首页可能发两次趋势榜请求；换来的是没有「必须一起写、一起读」这条约束，
 * 而那类约束一旦写进注释，就再也删不掉了。
 *
 * ## 存储
 *
 * 走 [com.liomix.mikomi.core.cache.disk.DiskCache]（整块 JSON），不落 Room：
 * 首页数据是「服务端上次给了什么」，没有查询需求，随时可以整块丢掉重建。
 *
 * 分页游标（`nextOffset`）存在缓存里而不是内存里：进程被回收后重新进页面，
 * 游标不会归零，否则会从第一页重新拉一遍、列表里多出一段重复内容。
 */
interface HomeContentRepository {

    /** 内容区当前这一页（含已加载的后续页）；值没变就不发射。 */
    fun observeContent(): Flow<List<Anime>>

    /** 进页面时调用：缓存还新就不发请求。 */
    suspend fun refreshIfStale()

    /** 无条件刷新第一页（用户下拉刷新）。返回刷新后是否还有下一页。 */
    suspend fun refresh(): Boolean

    /** 加载下一页，游标在数据层内部维护。返回是否还有下一页。 */
    suspend fun loadMore(): Boolean

    companion object {
        /** 内容区一页条数。 */
        const val PAGE_SIZE = 20
    }
}
