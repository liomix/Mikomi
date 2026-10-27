package com.liomix.mikomi.core.model

/** 角色头像尺寸集合。 */
data class CharacterImageSet(
    val small: String? = null,
    val grid: String? = null,
    val large: String? = null,
    val medium: String? = null,
)

/** 配音演员。 */
data class VoiceActor(
    val id: Int,
    val name: String,
    val images: CharacterImageSet? = null,
)

/** 番剧角色。 */
data class AnimeCharacter(
    val id: Int,
    val name: String,
    /** 角色关系：主角 / 配角 / 客串 等。 */
    val relation: String,
    val images: CharacterImageSet? = null,
    val voiceActors: List<VoiceActor> = emptyList(),
)

/** 制作人员。 */
data class StaffMember(
    val id: Int,
    val name: String,
    /** 中文名（本地化名），可能为空。 */
    val localizedName: String? = null,
    val images: CharacterImageSet? = null,
    /** 职位标签（如「原作」「导演」），按顺序。 */
    val positions: List<String> = emptyList(),
) {
    /** 展示名：中文名优先，回退原名。 */
    val displayName: String
        get() = localizedName?.takeIf { it.isNotBlank() } ?: name

    /** 职位文案：多个职位以「、」连接。 */
    val positionText: String
        get() = positions.joinToString("、")
}
