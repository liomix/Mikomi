package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 角色头像尺寸集合（角色/人物通用）。 */
@Serializable
data class BangumiCreditImageDto(
    val small: String? = null,
    val grid: String? = null,
    val large: String? = null,
    val medium: String? = null,
)

/** 配音演员。 */
@Serializable
data class BangumiVoiceActorDto(
    val id: Int,
    val name: String,
    val images: BangumiCreditImageDto? = null,
)

/** 番剧角色（`/v0/subjects/{id}/characters` 返回的元素）。 */
@Serializable
data class BangumiCharacterDto(
    val id: Int,
    val name: String,
    val relation: String? = null,
    val images: BangumiCreditImageDto? = null,
    val actors: List<BangumiVoiceActorDto> = emptyList(),
)

/** 制作人员职位类型。 */
@Serializable
data class BangumiPositionTypeDto(
    val cn: String? = null,
    val name: String? = null,
)

/** 制作人员职位。 */
@Serializable
data class BangumiPositionDto(
    val type: BangumiPositionTypeDto? = null,
)

/** 制作人员（staff 对象）。 */
@Serializable
data class BangumiStaffDto(
    val id: Int,
    val name: String,
    @SerialName("nameCN") val nameCn: String? = null,
    @SerialName("name_cn") val nameCnNext: String? = null,
    val images: BangumiCreditImageDto? = null,
) {
    /** 中文名（本地化名），优先 nameCN，回退 name_cn。 */
    val localizedName: String?
        get() = nameCn?.takeIf { it.isNotBlank() }
            ?: nameCnNext?.takeIf { it.isNotBlank() }
}

/** 制作人员条目（`/p1/subjects/{id}/staffs/persons` 的 data 元素）。 */
@Serializable
data class BangumiStaffInfoDto(
    val staff: BangumiStaffDto? = null,
    val positions: List<BangumiPositionDto> = emptyList(),
)

/** 制作人员响应（`/p1/subjects/{id}/staffs/persons`）。 */
@Serializable
data class BangumiStaffResponseDto(
    val data: List<BangumiStaffInfoDto> = emptyList(),
)
