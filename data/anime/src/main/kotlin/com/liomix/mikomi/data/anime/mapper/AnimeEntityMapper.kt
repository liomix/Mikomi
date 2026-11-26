package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.model.Anime

/**
 * 列表条目 → Room 实体（首页落库用）。
 *
 * 只映射卡片要显示的字段：首页网格、轮播、推荐共用的就是这些。详情才有的集数、
 * 信息框不进这张表 —— 它们属于 `DetailRepository` 返回的模型。
 *
 * [source] 与 [homeRank] 由调用方给：来源决定首页查询能不能命中，位次决定 loadMore
 * 之后卡片会不会插队，两者都是**写入时机**的属性，不是条目本身的属性。
 */
internal fun Anime.toEntity(
    source: String = SOURCE_HOME,
    homeRank: Int = 0,
): AnimeEntity = AnimeEntity(
    id = id,
    title = title,
    coverUrl = coverUrl,
    synopsis = synopsis,
    rating = rating,
    status = "unknown",
    popularity = popularity,
    airDate = airDate.orEmpty(),
    rank = rank,
    ratingCount = ratingCount,
    genres = genres.joinToString(GENRES_SEPARATOR),
    source = source,
    homeRank = homeRank,
)

/** 首页来源标记（`AnimeEntity.source`）。详情页回写时保留已有行的来源，不覆盖。 */
internal const val SOURCE_HOME = "home"

/** 类型标签的多值分隔符：多值拼成一列存储，读出时按它切分。 */
internal const val GENRES_SEPARATOR = "·"
