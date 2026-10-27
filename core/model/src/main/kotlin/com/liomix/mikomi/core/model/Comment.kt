package com.liomix.mikomi.core.model

/** 评论用户（Bangumi 用户的精简信息）。 */
data class CommentUser(
    val id: Int,
    val username: String,
    val nickname: String,
    val avatarUrl: String? = null,
) {
    /** 展示名：昵称优先，回退用户名。 */
    val displayName: String
        get() = nickname.takeIf { it.isNotBlank() } ?: username
}

/** 主创吐槽/评论（可含回复）。 */
data class CreditComment(
    val id: Int,
    val user: CommentUser,
    val content: String,
    /** Unix 时间戳（秒）。 */
    val createdAt: Long,
    val replies: List<CreditComment> = emptyList(),
)

/**
 * 番剧吐槽（Bangumi 短评，带评分）。
 *
 * 对应 `GET /p1/subjects/{id}/comments` 的单条数据。
 */
data class TeasingComment(
    val id: Int,
    val user: CommentUser,
    val content: String,
    /** 评分 0-10（Bangumi rate）；0 表示未评分。 */
    val score: Int,
    /** Unix 时间戳（秒）。 */
    val createdAt: Long,
)
