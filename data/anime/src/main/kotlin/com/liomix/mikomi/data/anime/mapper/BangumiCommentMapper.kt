package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.model.CommentUser
import com.liomix.mikomi.core.model.TeasingComment
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSubjectCommentDto

/** 番剧吐槽 DTO → 领域模型。 */
internal fun BangumiSubjectCommentDto.toDomain(): TeasingComment = TeasingComment(
    id = id,
    user = CommentUser(
        id = user?.id ?: 0,
        username = user?.username.orEmpty(),
        nickname = user?.nickname.orEmpty(),
        avatarUrl = user?.avatar?.large ?: user?.avatar?.medium,
    ),
    content = comment.orEmpty(),
    score = rate.coerceIn(0, 10),
    createdAt = updatedAt.takeIf { it > 0 } ?: createdAt,
)
