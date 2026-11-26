package com.liomix.mikomi.core.model.anime

/**
 * 详情页的模型。
 *
 * 为什么不复用 [com.liomix.mikomi.core.model.Anime]：那个是「列表里的一行」，字段就够画一张
 * 卡片；详情页要多得多的东西 —— 已播出集数、总集数、平台、收藏分布、角色、职员。
 * 塞进同一个类，列表页会背着一堆永远用不上的字段，而且「这个 eps 是总集数还是已播」这种
 * 问题会永远存在。
 */
data class AnimeDetail(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val synopsis: String? = null,
    /** 评分（0~10，未知为 null）。 */
    val score: Double? = null,
    /** 站内排名，0 表示没有排名。 */
    val rank: Int = 0,
    /** 评分人数。 */
    val ratingCount: Int = 0,
    /** 首播日期（`yyyy-MM-dd`，可能只到年或月）。 */
    val date: String? = null,
    /** 平台：TV / OVA / 剧场版。 */
    val platform: String? = null,
    /** 已播出集数。 */
    val episodes: Int = 0,
    /** 计划总集数。连载中时与 [episodes] 不相等。 */
    val totalEpisodes: Int = 0,
    /** 类型标签（按热度排序）。 */
    val genres: List<String> = emptyList(),
    val collection: AnimeCollection = AnimeCollection(),
    val characters: List<DetailCharacter> = emptyList(),
    val staff: List<DetailStaff> = emptyList(),
)

/**
 * 收藏分布。
 *
 * 五项是**不同状态的人数**，不是「总数的分解」—— 同一个人可以多次改状态，
 * 所以它们相加不等于收藏数。
 */
data class AnimeCollection(
    val wish: Int = 0,
    val collect: Int = 0,
    val doing: Int = 0,
    val onHold: Int = 0,
    val dropped: Int = 0,
)

/** 详情页的角色。 */
data class DetailCharacter(
    val id: Int,
    val name: String,
    /** 关系：主角 / 配角 / 客串。 */
    val relation: String = "",
    val imageUrl: String? = null,
    val voiceActors: List<DetailVoiceActor> = emptyList(),
)

/** 配音演员。 */
data class DetailVoiceActor(
    val id: Int = 0,
    val name: String = "",
    val imageUrl: String? = null,
)

/**
 * 详情页的制作人员。
 *
 * [position] 是单个职位文案而不是列表：详情页那一行只放得下一个职位，多职位的情况
 * （「原作 / 导演」）在映射时就按顺序取第一个 —— 完整列表留给主创详情页。
 */
data class DetailStaff(
    val id: Int,
    val name: String,
    /** 中文名；为空时展示 [name]。 */
    val localizedName: String? = null,
    val position: String = "",
    val imageUrl: String? = null,
) {
    val displayName: String
        get() = localizedName?.takeIf { it.isNotBlank() } ?: name
}

/**
 * 主创详情（点某个角色或职员后展开的那一层）。
 *
 * 角色与人物共用一个模型：页面形态完全一样（头像 + 名字 + 中日文名 + 简介），
 * 区别只在数据来源，而那属于数据层的事。
 */
data class CreatorDetail(
    val id: Int,
    val name: String,
    /** 中文名；为空时展示 [name]。 */
    val localizedName: String? = null,
    val imageUrl: String? = null,
    /** 基本信息（生日、身高、血型…），多行文本。 */
    val info: String? = null,
    val summary: String? = null,
) {
    val displayName: String
        get() = localizedName?.takeIf { it.isNotBlank() } ?: name
}
