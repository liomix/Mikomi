package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.database.entity.AnimeEntity
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

/** 主键前缀，与 AniList 等数据源的 id 空间隔离，避免落同一张表时碰撞。 */
internal const val BANGUMI_ID_PREFIX = "bangumi:"
