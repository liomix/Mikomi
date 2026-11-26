package com.liomix.mikomi.data.anime.remote.bangumi.mapper

import com.liomix.mikomi.core.model.anime.AnimeComment
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiAnimeCommentDto

/**
 * 番剧评论 → [AnimeComment]。
 *
 * 作者缺失时返回 null：评论没有作者，页面上连一行头部都画不出来。
 *
 * `score` 的 0 表示「没打分」而不是「0 分」—— Bangumi 的有效评分是 1~10，
 * 映射时不要把它改写成 null，否则「没打分」和「未知」会被混为一谈。
 */
internal fun BangumiAnimeCommentDto.toAnimeComment(): AnimeComment? {
    val author = user ?: return null
    return AnimeComment(
        id = id,
        user = AnimeComment.User(
            id = author.id,
            name = author.nickname?.takeIf(String::isNotBlank) ?: author.username.orEmpty(),
            // 头像用 medium：评论区一行只有 32dp，large 是白白多下几十 KB。
            avatarUrl = author.avatar?.medium ?: author.avatar?.large,
        ),
        score = rate,
        // 空串而不是 null：模型里 `content` 非空，页面直接渲染，不必再判空。
        content = comment.orEmpty(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
