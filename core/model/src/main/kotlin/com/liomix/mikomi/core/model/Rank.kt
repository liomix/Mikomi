package com.liomix.mikomi.core.model

/** 榜单产地维度：国漫 / 日漫。 */
enum class RankRegion {
    CHINESE,
    JAPANESE,
}

/** 综合评分榜条目。 */
data class RankEntry(
    val anime: Anime,
    /** 所属产地榜单。 */
    val region: RankRegion,
    /** 展示用类型标签（如 奇幻 / 冒险）。 */
    val genres: List<String> = emptyList(),
)
