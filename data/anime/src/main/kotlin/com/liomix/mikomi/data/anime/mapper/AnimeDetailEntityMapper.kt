package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.model.anime.AnimeDetail

/**
 * 详情 → Room 实体（详情页回写用）。
 *
 * 方向是「领域模型 → 实体」，与 `remote` 那套（DTO → 领域模型）相反：详情数据已经在
 * 数据源里变成了领域模型，这里只是把它落库，不该再回头去碰 DTO。
 *
 * 不写 `genres` 与 `source`：前者是首页卡片的展示字段（详情页不负责它的排序），
 * 后者由调用方按「已有行保留原来源」的规则决定（见 `AnimeRepositoryImpl`）。
 */
internal fun AnimeDetail.toEntity(): AnimeEntity = AnimeEntity(
    id = id,
    title = title,
    coverUrl = coverUrl,
    synopsis = synopsis,
    rating = score,
    status = "unknown",
    popularity = ratingCount,
    airDate = date.orEmpty(),
    rank = rank,
    ratingCount = ratingCount,
)
