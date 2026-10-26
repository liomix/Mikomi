package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.database.entity.RankEntity
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSubjectDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiTrendItem

/** trending 条目：用趋势计数 [BangumiTrendItem.count] 作为热度排序依据。 */
internal fun BangumiTrendItem.toEntity(): AnimeEntity =
    subject.toEntity(popularity = count)

internal fun BangumiSubjectDto.toEntity(popularity: Int? = null): AnimeEntity = AnimeEntity(
    id = "$BANGUMI_ID_PREFIX$id",
    title = displayName,
    coverUrl = images?.large ?: images?.common ?: images?.medium,
    synopsis = summary,
    rating = rating?.score,
    status = "unknown",
    popularity = popularity ?: (rating?.total ?: 0),
)

/**
 * 详情条目：带完整详情字段（上映日期 / 排行 / 评分人数 / 类型标签）。
 * 供详情页经 Bangumi `/v0/subjects/{id}` 拉取后回写。
 */
internal fun BangumiSubjectDto.toDetailEntity(): AnimeEntity = AnimeEntity(
    id = "$BANGUMI_ID_PREFIX$id",
    title = displayName,
    coverUrl = images?.large ?: images?.common ?: images?.medium,
    synopsis = summary,
    rating = rating?.score,
    status = "unknown",
    popularity = rating?.total ?: 0,
    airDate = date.orEmpty(),
    rank = rating?.rank ?: 0,
    ratingCount = rating?.total ?: 0,
    genres = metaTags
        .filterNot { it in GENRE_NOISE }
        .joinToString(GENRES_SEPARATOR),
)

/** 榜单实体：[region] 为产地标签（cn / jp），类型标签取 meta_tags 去噪后展示。 */
internal fun BangumiSubjectDto.toRankEntity(region: String): RankEntity = RankEntity(
    id = "$BANGUMI_ID_PREFIX$id",
    region = region,
    title = displayName,
    coverUrl = images?.large ?: images?.common ?: images?.medium,
    synopsis = summary,
    rating = rating?.score,
    status = "unknown",
    popularity = rating?.total ?: 0,
    genres = metaTags
        .filterNot { it in GENRE_NOISE }
        .take(GENRE_DISPLAY_LIMIT)
        .joinToString(GENRES_SEPARATOR),
)

/** 标准标签中的产地/媒介噪音（榜单已按产地分列，无需重复展示）。 */
private val GENRE_NOISE = setOf(
    "日本", "中国", "国产", "国产动画", "中国动画", "日漫", "国漫",
    "TV", "OVA", "OAD", "剧场版", "WEB", "真人版",
)

private const val GENRE_DISPLAY_LIMIT = 2

/** 主键前缀，与 AniList 等数据源的 id 空间隔离，避免落同一张表时碰撞。 */
internal const val BANGUMI_ID_PREFIX = "bangumi:"
