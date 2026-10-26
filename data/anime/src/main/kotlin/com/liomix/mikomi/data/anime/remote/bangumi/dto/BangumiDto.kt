package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** trending 响应（next.bgm.tv /p1/trending/subjects）。 */
@Serializable
data class BangumiTrendResponse(
    val data: List<BangumiTrendItem> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class BangumiTrendItem(
    val subject: BangumiSubjectDto,
    /** 趋势计数，用作首页热度排序。 */
    val count: Int = 0,
)

/** 搜索请求体（api.bgm.tv /v0/search/subjects）。 */
@Serializable
data class BangumiSearchRequest(
    val keyword: String,
    val sort: String = "rank",
    val filter: BangumiSearchFilter = BangumiSearchFilter(),
)

@Serializable
data class BangumiSearchFilter(
    /** 条目类型（2 = 动画）。默认值需始终编码，榜单过滤依赖它排除书籍/游戏等条目。 */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: List<Int> = listOf(2),
    /** 标签过滤（如产地标签「国产动画」「日本」）；空表示不过滤。 */
    val tag: List<String> = emptyList(),
    /** 排名过滤表达式（如 `">0"` 表示仅保留有站内排名的条目）；空表示不过滤。 */
    val rank: List<String> = emptyList(),
)

/** 搜索响应。 */
@Serializable
data class BangumiSearchResponse(
    val data: List<BangumiSubjectDto> = emptyList(),
    val total: Int = 0,
)

/**
 * 条目。v0 用 `name_cn`，p1 用 `nameCN`，两者语义相同，分别映射到 [nameCn] / [nameCnNext]。
 */
@Serializable
data class BangumiSubjectDto(
    val id: Int,
    val name: String,
    @SerialName("name_cn") val nameCn: String? = null,
    @SerialName("nameCN") val nameCnNext: String? = null,
    val type: Int = 0,
    val images: BangumiImagesDto? = null,
    val rating: BangumiRatingDto? = null,
    val summary: String? = null,
    val date: String? = null,
    /** 标准标签（v0 搜索响应为下划线风格 `meta_tags`；Next trending 的 `metaTags` 由 [tags] 之外的字段忽略）。 */
    @SerialName("meta_tags") val metaTags: List<String> = emptyList(),
) {
    /** 展示名：中文名优先，回退原名。 */
    val displayName: String
        get() = nameCn?.takeIf { it.isNotBlank() }
            ?: nameCnNext?.takeIf { it.isNotBlank() }
            ?: name
}

@Serializable
data class BangumiImagesDto(
    val large: String? = null,
    val common: String? = null,
    val medium: String? = null,
    val small: String? = null,
    val grid: String? = null,
)

@Serializable
data class BangumiRatingDto(
    val score: Double? = null,
    val rank: Int? = null,
    val total: Int? = null,
)
