package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 番剧吐槽（`/p1/subjects/{id}/comments` 的 data 元素）。 */
@Serializable
data class BangumiSubjectCommentDto(
    val id: Int = 0,
    val user: BangumiCommentUserDto? = null,
    /** 评分 0-10；0 表示未评分。 */
    val rate: Int = 0,
    val comment: String? = null,
    @SerialName("createdAt") val createdAt: Long = 0,
    @SerialName("updatedAt") val updatedAt: Long = 0,
)

/** 番剧吐槽分页响应（`/p1/subjects/{id}/comments`）。 */
@Serializable
data class BangumiSubjectCommentsResponseDto(
    val data: List<BangumiSubjectCommentDto> = emptyList(),
    val total: Int = 0,
)
