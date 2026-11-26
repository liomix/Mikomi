package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 番剧基础信息 —— **其余所有 DTO 的公共零件**。
 *
 * 首页、搜索、详情、榜单拿到的都是「一个条目」，只是各自附带的字段不同。所以这里只放
 * 无论从哪个接口拿都一定有的那几项；`infobox`、`eps`、`tags` 这类只在详情接口才完整的
 * 字段归 [BangumiDetailDto]，不往基类里塞 —— 否则首页每一条都要背着一堆空字段。
 *
 * ## 两个名字字段
 *
 * Bangumi 的两套接口用不同拼法表示同一个东西：
 * - v0（`api.bgm.tv`）是 `name_cn`，映射到 [nameCn]
 * - Next（`next.bgm.tv`）是 `nameCN`，映射到 [nameCnNext]
 *
 * 保留两个字段而不是在解析时合并：合并就丢掉了「这个中文名是哪个接口给的」这条信息，
 * 而排查「首页有中文名、详情页却没有」这类问题时，恰恰需要它。展示用 [displayName]。
 */
@Serializable
data class BangumiDto(
    val id: Int,
    val name: String,
    @SerialName("name_cn") val nameCn: String? = null,
    @SerialName("nameCN") val nameCnNext: String? = null,
    val type: Int = 0,
    val images: BangumiImagesDto? = null,
    val rating: BangumiRatingDto? = null,
    val summary: String? = null,
    /** 首播日期（`yyyy-MM-dd`，可能只到年或月）。 */
    val date: String? = null,
    /** 标准标签（v0 是下划线风格的 `meta_tags`）。 */
    @SerialName("meta_tags") val metaTags: List<String> = emptyList(),
) {
    /** 展示名：中文名优先，回退原名。 */
    val displayName: String
        get() = nameCn?.takeIf { it.isNotBlank() }
            ?: nameCnNext?.takeIf { it.isNotBlank() }
            ?: name
}

/** 封面尺寸集合。Bangumi 一次给五档，取哪一档由调用方按屏幕决定。 */
@Serializable
data class BangumiImagesDto(
    val large: String? = null,
    val common: String? = null,
    val medium: String? = null,
    val small: String? = null,
    val grid: String? = null,
)

/**
 * 评分。
 *
 * 三个字段常常只有一部分有值：新番没有 `rank`、没人评分的条目 `score` 为 0。
 * 用可空类型表达「服务端没给」，不用 0 表示 —— 0 分和没有评分是两件事。
 */
@Serializable
data class BangumiRatingDto(
    val score: Double? = null,
    val rank: Int? = null,
    val total: Int? = null,
)
