package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 番剧主创：**角色 + 制作人员**，详情页「制作」那一栏的全部内容。
 *
 * 两个接口形状不同（角色带 `relation` 与配音演员，职员带职位列表），但页面把它们排在一起，
 * 所以两者共用 [BangumiCreatorDto] 的字段，用 [BangumiCreatorKind] 区分是哪一类。
 * 好处是列表只有一种元素类型，排序、分组、点击都只写一遍。
 *
 * 分开两个类（`CharacterDto` / `StaffDto`）的话，页面得把它们转成一个中间模型才能同列展示 ——
 * 那层转换没有任何业务含义，只是在补偿 DTO 的形状。
 */
@Serializable
data class BangumiCreatorDto(
    val id: Int = 0,
    val name: String = "",
    /** 中文名（Next 是 `nameCN`，v0 是 `name_cn`）。 */
    @SerialName("nameCN") val nameCn: String? = null,
    @SerialName("name_cn") val nameCnNext: String? = null,
    /** 关系：角色是「主角 / 配角」，职员是空（职位在 [positions] 里）。 */
    val relation: String? = null,
    val images: BangumiCreatorImageDto? = null,
    /** 配音演员（仅角色条目有）。 */
    val actors: List<BangumiVoiceActorDto> = emptyList(),
    /** 职位（仅职员条目有）。 */
    val positions: List<BangumiCreatorPositionDto> = emptyList(),
    val kind: BangumiCreatorKind = BangumiCreatorKind.Character,
) {
    /** 展示名：中文优先。 */
    val displayName: String
        get() = nameCn?.takeIf { it.isNotBlank() }
            ?: nameCnNext?.takeIf { it.isNotBlank() }
            ?: name

    /** 职位文案：职员取第一个职位的中文名，角色取关系（主角/配角）。 */
    val roleLabel: String
        get() = positions.firstOrNull()?.type?.cn?.takeIf { it.isNotBlank() }
            ?: positions.firstOrNull()?.type?.name?.takeIf { it.isNotBlank() }
            ?: relation.orEmpty()
}

/** 主创类别。页面据此决定「这一条该显示配音演员还是职位」。 */
@Serializable
enum class BangumiCreatorKind {
    Character,
    Staff,
}

/** 主创头像尺寸集合。与条目封面不是同一套字段名，所以单独一份。 */
@Serializable
data class BangumiCreatorImageDto(
    val small: String? = null,
    val grid: String? = null,
    val large: String? = null,
    val medium: String? = null,
)

/** 配音演员。 */
@Serializable
data class BangumiVoiceActorDto(
    val id: Int = 0,
    val name: String = "",
    val images: BangumiCreatorImageDto? = null,
)

/** 职位类型（中日双名，如 `cn=导演` / `name=Director`）。 */
@Serializable
data class BangumiCreatorPositionTypeDto(
    val cn: String? = null,
    val name: String? = null,
)

/** 职位。 */
@Serializable
data class BangumiCreatorPositionDto(
    val type: BangumiCreatorPositionTypeDto? = null,
)

/**
 * 主创响应（`GET /v0/subjects/{id}/characters` 与 `/p1/subjects/{id}/staffs/persons`）。
 *
 * 两个接口都是 `{ data: [...] }`，但一个是角色、一个是职员。合成一个响应类型是因为
 * 调用方拿到的永远是「一组 [BangumiCreatorDto]」，不需要知道它来自哪个接口。
 */
@Serializable
data class BangumiCreatorResponseDto(
    val data: List<BangumiCreatorDto> = emptyList(),
)

/**
 * 主创详情：点进某个角色后的那一页（`GET /p1/characters/{id}`）。
 *
 * 与 [BangumiCreatorDto] 的区别是「列表里的一行」与「一整页」：这里多了简介与信息框。
 * 放在同一个文件里，因为它们属于同一套东西 —— 列表点进去就是这个，改字段时不会漏。
 */
@Serializable
data class BangumiCreatorCharacterDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("nameCN") val nameCn: String? = null,
    @SerialName("name_cn") val nameCnNext: String? = null,
    val images: BangumiCreatorImageDto? = null,
    val summary: String? = null,
    val info: String? = null,
) {
    val displayName: String
        get() = nameCn?.takeIf { it.isNotBlank() }
            ?: nameCnNext?.takeIf { it.isNotBlank() }
            ?: name
}

/** 人物详情：点进导演/编剧后的那一页（`GET /p1/persons/{id}`）。 */
@Serializable
data class BangumiCreatorPersonDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("nameCN") val nameCn: String? = null,
    @SerialName("name_cn") val nameCnNext: String? = null,
    val images: BangumiCreatorImageDto? = null,
    val summary: String? = null,
    /** 信息框（出生地、生日、参与作品…），key 由服务端决定。 */
    val infobox: List<BangumiInfoboxItemDto> = emptyList(),
) {
    val displayName: String
        get() = nameCn?.takeIf { it.isNotBlank() }
            ?: nameCnNext?.takeIf { it.isNotBlank() }
            ?: name
}

/**
 * 主创评论：主创详情页下方的吐槽。
 *
 * 与 [BangumiAnimeCommentDto] 分开，因为**评论对象不同**：那边评的是番剧，这边评的是人。
 * 两者字段也很不一样 —— 主创评论没有评分（`rate`），但有 [replies]：Bangumi 的人物评论
 * 是一层楼一条回复的结构，而番剧评论是平铺的。
 *
 * [replies] 自引用而不是引用别的评论类型：回复的回复也存在，深度由服务端决定，
 * 用一个类型递归表达比「两层不同类型」更贴近真实数据。
 */
@Serializable
data class BangumiCreatorCommentDto(
    val id: Int = 0,
    val user: BangumiAnimeCommentUserDto? = null,
    val content: String? = null,
    @SerialName("createdAt") val createdAt: Long = 0,
    val replies: List<BangumiCreatorCommentDto> = emptyList(),
)
