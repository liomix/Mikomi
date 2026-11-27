package com.liomix.mikomi.data.anime.repository.Impl

import com.liomix.mikomi.core.cache.CacheJson
import com.liomix.mikomi.core.cache.CacheKey
import com.liomix.mikomi.core.cache.CacheTtl
import com.liomix.mikomi.core.cache.disk.DiskCache
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.SliderImage
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiSource
import com.liomix.mikomi.data.anime.repository.SliderImageRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * 轮播图：自己一块缓存（`CacheKey.BANNER`）、自己的 TTL、自己的请求。
 *
 * 缓存里存 `Anime` 而不是 [SliderImage]：落盘的应该是「服务端给了什么」，
 * 收窄成 [SliderImage] 是**读出来**这一步的事。这样哪天轮播多要一个字段，
 * 不用动缓存格式、也不用让旧缓存失效一次。
 *
 * [`distinctUntilChanged`][distinctUntilChanged] 在这里是必需的：缓存写入会带来新的
 * `List` 实例，内容没变也不相等；不去重的话，每写一次就让头图重组一次。
 */
internal class SliderImageRepositoryImpl @Inject constructor(
    private val source: BangumiSource,
    private val cacheStore: DiskCache,
) : SliderImageRepository {

    override fun observeSliderImages(): Flow<List<SliderImage>> =
        cacheStore.observe(CacheKey.BANNER)
            .map { entry -> entry?.decode()?.images.orEmpty().map(Anime::toSliderImage) }
            .distinctUntilChanged()

    override suspend fun refreshIfStale() {
        if (!cacheStore.isStale(CacheKey.BANNER, CacheTtl.BANNER)) return
        refresh()
    }

    override suspend fun refresh() {
        val images = source.trending(limit = SLIDER_LIMIT, offset = 0).take(SLIDER_LIMIT)
        cacheStore.put(CacheKey.BANNER, CacheJson.encode(SliderCache(images = images)))
    }

    private fun com.liomix.mikomi.core.cache.CacheSnapshot.Entry.decode(): SliderCache =
        CacheJson.decode<SliderCache>(payload) ?: SliderCache()

    private companion object {
        const val SLIDER_LIMIT = SliderImageRepository.SLIDER_LIMIT
    }
}

/** 轮播缓存块：只要那几张图。 */
@Serializable
private data class SliderCache(
    val images: List<Anime> = emptyList(),
)

/** 收窄到轮播真正要用的三个字段。 */
private fun Anime.toSliderImage(): SliderImage = SliderImage(
    animeId = id,
    title = title,
    coverUrl = coverUrl,
)
