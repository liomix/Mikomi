package com.liomix.mikomi.data.anime.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 番剧评论（`GET /p1/subjects/{id}/comments`）的一条。
 *
 * 命名为 `AnimeComment` 而不是跟接口叫 `subject`：`subject` 是 Bangumi 的术语（条目），
 * 它同时包括书籍、音乐、游戏；而这个项目只处理动画。名字跟着项目走，不跟着上游接口走 ——
 * 否则代码里会冒出「subject 评论」「subject 主创」，读者得先知道 Bangumi 的术语才看得懂。
 */
@Serializable
data class BangumiAnimeCommentDto(
    val id: Int = 0,
    val user: BangumiAnimeCommentUserDto? = null,
    /** 评分 0~10；0 表示这条评论没有打分。 */
    val rate: Int = 0,
    val comment: String? = null,
    @SerialName("createdAt") val createdAt: Long = 0,
    @SerialName("updatedAt") val updatedAt: Long = 0,
)

/** 评论作者。只留展示需要的三个字段：id（跳转用）、名字、头像。 */
@Serializable
data class BangumiAnimeCommentUserDto(
    val id: Int = 0,
    val username: String? = null,
    val nickname: String? = null,
    val avatar: BangumiCreatorImageDto? = null,
) {
    /** 展示名：昵称优先，回退用户名。 */
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() } ?: username.orEmpty()
}

/**
 * 评论分页响应。
 *
 * [total] 独立于 `data.size`：一页 20 条、总数可能上千，页面靠它决定要不要显示「加载更多」。
 */
@Serializable
data class BangumiAnimeCommentResponseDto(
    val data: List<BangumiAnimeCommentDto> = emptyList(),
    val total: Int = 0,
)
