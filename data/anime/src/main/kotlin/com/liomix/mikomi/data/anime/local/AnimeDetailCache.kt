package com.liomix.mikomi.data.anime.local

import com.liomix.mikomi.core.cache.CacheJson
import com.liomix.mikomi.core.cache.CacheKey
import com.liomix.mikomi.core.cache.disk.DiskCache
import com.liomix.mikomi.core.model.anime.AnimeDetail
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 单条详情的缓存：`detail:{领域 id}` → 一份 `AnimeDetail` JSON。
 *
 * ## 为什么不是数据库
 *
 * 详情是「服务端上一次给的响应」，随时可以整块丢掉重建 —— 没有查询、没有关联，
 * 也没有需要长期保留的用户意图。为它建表要付 schema 与 migration 的代价，而收益是零：
 * 缓存没了重新拉一次就行。
 *
 * ## 唯一的例外
 *
 * 时间表的「按标题补简介」曾经读数据库里的番剧库。那条能力随离线库一起下线了 ——
 * 时间表条目现在只显示各源自己给的简介，不再做跨页面的二次补全。
 */
@Singleton
internal class AnimeDetailCache @Inject constructor(
    private val cache: DiskCache,
) {

    /** 观察某条详情；缓存被写入时再次发射。 */
    fun observe(animeId: String): Flow<AnimeDetail?> =
        cache.observe(key(animeId)).map { entry -> entry?.payload?.let { CacheJson.decode<AnimeDetail>(it) } }

    /** 读一次（播放页取标题这类一次性用途）。 */
    suspend fun get(animeId: String): AnimeDetail? =
        cache.get(key(animeId))?.payload?.let { CacheJson.decode<AnimeDetail>(it) }

    /** 写入；`savedAt` 由 [DiskCache] 统一盖。 */
    suspend fun save(detail: AnimeDetail) {
        cache.put(key(detail.id), CacheJson.encode(detail))
    }

    private fun key(animeId: String): String = CacheKey.DETAIL + animeId
}
