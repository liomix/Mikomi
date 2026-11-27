package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.core.model.anime.AnimeDetail

/**
 * 详情 → 头部摘要（[Anime]）。
 *
 * 详情页的头部（标题、封面、简介、评分、日期）用这一份；角色、职员、收藏分布各有各的
 * 加载时机，留在 [AnimeDetail] 里由各自的区域取。把它们塞进一个 `Anime`，
 * 列表与详情就会共用同一个模型却各懂一半字段。
 */
internal fun AnimeDetail.toSummary(): Anime = Anime(
    id = id,
    title = title,
    coverUrl = coverUrl,
    synopsis = synopsis,
    rating = score,
    status = AnimeStatus.UNKNOWN,
    genres = genres,
    airDate = date,
    rank = rank,
    ratingCount = ratingCount,
)
