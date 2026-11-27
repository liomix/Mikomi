package com.liomix.mikomi.data.anime.repository

import com.liomix.mikomi.core.model.anime.SliderImage
import kotlinx.coroutines.flow.Flow

/**
 * 首页**轮播图**。
 *
 * ## 与内容区完全独立
 *
 * 自己的缓存块（`CacheKey.BANNER`）、自己的 TTL、自己的请求。轮播的节奏与内容区不同 ——
 * 它一天才换几次 —— 所以它不该跟着内容区一起过期。绑在一起时只能取两者中更短的那个，
 * 这正是分开的理由。
 *
 * 与内容区唯一的关系是：两者当前都来自 Bangumi 趋势榜。哪天轮播换成运营位
 * （另一个接口、另一套字段、另一个来源），改的只有本接口的实现，[HomeContentRepository]
 * 一行不动。
 *
 * ## 形状
 *
 * 返回 [SliderImage] 而不是 `Anime`：轮播只用 id / 标题 / 封面三个字段，收窄发生在
 * 数据层，界面就不必再从一个大模型里挑它要的那两三个值。
 */
interface SliderImageRepository {

    /** 当前这一轮的轮播图；值没变就不发射。 */
    fun observeSliderImages(): Flow<List<SliderImage>>

    /** 进页面时调用：缓存还新就不发请求。 */
    suspend fun refreshIfStale()

    /** 无条件刷新。 */
    suspend fun refresh()

    companion object {
        /** 最多几张：再多横滑就没意义了，用户根本滑不到最后。 */
        const val SLIDER_LIMIT = 5
    }
}
