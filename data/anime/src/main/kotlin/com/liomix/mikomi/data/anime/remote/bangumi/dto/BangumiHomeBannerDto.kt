package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.Serializable

/**
 * 首页轮播图的一项。
 *
 * 来自 Next 的 `/p1/trending/subjects`：它不是「编辑选出来的 banner」，而是**趋势榜**。
 * 所以带 [count] —— 那个数字既是排序依据，也是「为什么这张图在这里」的唯一解释。
 *
 * 只有 [subject] 一个内容字段：轮播要展示的东西（标题、封面、评分）都在基础信息里，
 * 多包一层「轮播专用字段」只会让两处对不上。
 */
@Serializable
data class BangumiHomeBannerDto(
    val subject: BangumiDto,
    /** 趋势计数（热度）。首页按它降序排。 */
    val count: Int = 0,
)

/**
 * 轮播响应。
 *
 * `total` 保留而不是丢掉：接口给了总数，而首屏只取前几张 —— 排查「为什么轮播只有三张」
 * 时，能一眼看出是接口本身少还是我们截断了。
 */
@Serializable
data class BangumiHomeBannerResponseDto(
    val data: List<BangumiHomeBannerDto> = emptyList(),
    val total: Int = 0,
)
