package com.liomix.mikomi.data.anime.remote.bangumi.mapper

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.data.anime.mapper.BANGUMI_ID_PREFIX
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiImagesDto

/**
 * 基础条目 → [Anime]。
 *
 * 这是唯一一处定义「一个 Bangumi 条目在项目里长什么样」的地方。首页、搜索、榜单
 * 都通过它转成同一种模型 —— 各处各写一遍的话，「封面取哪一档」「评分取 score 还是 rank」
 * 会慢慢变得不一致，而且没人说得清哪个才是对的。
 *
 * 不映射的东西：
 * - `type`：项目只做动画，这个字段恒为 2；
 * - `nsfw` / `collection`：只在详情页有意义，属于 [BangumiDetailDto]。
 */
internal fun BangumiDto.toDomain(): Anime = Anime(
    // 前缀把 Bangumi 的数字 id 和 AniRpb 的视频 id 隔开：两者都是「id」，但不同命名空间。
    id = "$BANGUMI_ID_PREFIX$id",
    title = displayName,
    coverUrl = images.bestCover(),
    synopsis = summary,
    rating = rating?.score,
    // 条目接口不给「连载状态」：v0 的 subject 没有这个字段，`date` 只能推首播时间。
    // 猜一个 ONGOING 会让已完结的番显示成「连载中」，所以宁可留 UNKNOWN。
    status = AnimeStatus.UNKNOWN,
    genres = metaTags,
    airDate = date,
    rank = rating?.rank ?: 0,
    ratingCount = rating?.total ?: 0,
)

/**
 * 取一张能用的封面。
 *
 * 顺序按「屏幕够用就好」排：`large` 是原图，列表里用不上；`common` 是最常用的一档。
 * 全为空返回 `null` —— 而不是返回空串，空串会被 Coil 当成一个待加载的 url。
 */
internal fun BangumiImagesDto?.bestCover(): String? =
    this?.large?.takeIf { it.isNotBlank() }
        ?: this?.common?.takeIf { it.isNotBlank() }
        ?: this?.medium?.takeIf { it.isNotBlank() }
        ?: this?.small?.takeIf { it.isNotBlank() }
