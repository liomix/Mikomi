package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 番剧详情页（`GET /v0/subjects/{id}`）。
 *
 * 与基础 [BangumiDto] 的关系是「同一个条目的完整版」：这里多出来的是**只在详情接口才给**
 * 的那几项 —— 集数、信息框、标签、收藏统计。首页/搜索不需要它们，所以不进基类。
 *
 * 不用「DetailDto 继承 BaseDto」那种写法：Kotlin 的 data class 不能继承 data class，
 * 用组合又会多一层 `base.xxx` 的跳转。两份字段各写一遍，换来的是每处都能直接用。
 */
@Serializable
data class BangumiDetailDto(
    val id: Int,
    val name: String,
    @SerialName("name_cn") val nameCn: String? = null,
    val type: Int = 0,
    val images: BangumiImagesDto? = null,
    val rating: BangumiRatingDto? = null,
    val summary: String? = null,
    val date: String? = null,
    /** 平台（TV / OVA / 剧场版…）。 */
    val platform: String? = null,
    /** 话数（已播出的集数）。 */
    val eps: Int = 0,
    /** 计划总话数。与 [eps] 不同：连载中的番两者不相等。 */
    @SerialName("total_episodes") val totalEpisodes: Int = 0,
    /** 卷数（漫画/小说条目才有）。 */
    val volumes: Int = 0,
    /** 是否为受限内容。 */
    val nsfw: Boolean = false,
    val tags: List<BangumiTagDto> = emptyList(),
    /** 信息框：制作公司、别名、放送开始等，服务端不给固定字段名，只能按 key 读。 */
    val infobox: List<BangumiInfoboxItemDto> = emptyList(),
    val collection: BangumiCollectionDto? = null,
) {
    /** 展示名：中文名优先，回退原名。 */
    val displayName: String
        get() = nameCn?.takeIf { it.isNotBlank() } ?: name
}

/**
 * 标签。
 *
 * [count] 是「多少人打了这个标签」：它决定标签的排序与是否值得展示 ——
 * 一个人打的冷门标签和三千人打的「TV」不该并列。
 */
@Serializable
data class BangumiTagDto(
    val name: String,
    /** 总计数（部分接口只给 `count`）。 */
    val count: Int = 0,
)

/**
 * 信息框条目。
 *
 * `value` 是 `JsonElement` 而不是 `String`：同一个字段有时是字符串、有时是字符串数组、
 * 有时是带 `v` 的对象。解析成 String 会在数组那几种情况直接抛异常，而这个字段整体只是
 * 「展示用的补充信息」，不值得为它让整页失败。
 */
@Serializable
data class BangumiInfoboxItemDto(
    val key: String? = null,
    val value: JsonElement? = null,
)

/**
 * 收藏统计：想看 / 在看 / 看过 / 搁置 / 抛弃。
 *
 * 五项都是「不同状态的人数」，不是「总数分解」—— 它们相加不等于收藏总数（同一人可多次改状态）。
 */
@Serializable
data class BangumiCollectionDto(
    val wish: Int = 0,
    val collect: Int = 0,
    val doing: Int = 0,
    val onHold: Int = 0,
    val dropped: Int = 0,
)
