package com.liomix.mikomi.data.anime.remote.bangumi.mapper

import com.liomix.mikomi.core.model.anime.CreatorComment
import com.liomix.mikomi.core.model.anime.CreatorDetail
import com.liomix.mikomi.core.model.anime.DetailCharacter
import com.liomix.mikomi.core.model.anime.DetailStaff
import com.liomix.mikomi.core.model.anime.DetailVoiceActor
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorCharacterDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorCommentDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorKind
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreatorPersonDto

/**
 * 主创 → 详情页的角色行。
 *
 * `kind` 决定走哪条：角色带配音演员，职员带职位。两者在页面上排在同一栏，但字段来源
 * 不同 —— 所以分成两个函数，返回可空，由调用方按 kind 各取一条。合并成一个函数会让
 * 「关系」和「职位」抢同一个字段。
 */
internal fun BangumiCreatorDto.toDetailCharacter(): DetailCharacter? {
    if (kind != BangumiCreatorKind.Character) return null
    return DetailCharacter(
        id = id,
        name = name,
        relation = relation.orEmpty(),
        imageUrl = images?.large ?: images?.medium,
        voiceActors = actors.map {
            DetailVoiceActor(id = it.id, name = it.name, imageUrl = it.images?.grid)
        },
    )
}

/**
 * 主创 → 详情页的职员行。
 *
 * 职位只取第一个：详情页那一行放不下「原作 / 导演 / 分镜」，完整列表留给主创详情页。
 */
internal fun BangumiCreatorDto.toDetailStaff(): DetailStaff? {
    if (kind != BangumiCreatorKind.Staff) return null
    val position = positions.firstOrNull()?.type
        ?.let { it.cn?.takeIf(String::isNotBlank) ?: it.name }
        .orEmpty()
    return DetailStaff(
        id = id,
        name = name,
        localizedName = displayName.takeIf { it != name },
        position = position,
        imageUrl = images?.large ?: images?.medium,
    )
}

/** 角色详情 → 主创详情页。 */
internal fun BangumiCreatorCharacterDto.toCreatorDetail(): CreatorDetail = CreatorDetail(
    id = id,
    name = name,
    localizedName = displayName.takeIf { it != name },
    imageUrl = images?.large ?: images?.medium,
    info = info,
    summary = summary,
)

/** 人物详情 → 主创详情页。信息框是结构化数据，这里只取大图与简介。 */
internal fun BangumiCreatorPersonDto.toCreatorDetail(): CreatorDetail = CreatorDetail(
    id = id,
    name = name,
    localizedName = displayName.takeIf { it != name },
    imageUrl = images?.large ?: images?.medium,
    summary = summary,
)

/**
 * 主创评论 → [CreatorComment]，递归处理楼中楼。
 *
 * 作者缺失时返回 null：没有作者的评论在页面上连一行头部都画不出来。
 * 作者名昵称优先 —— 用户名是登录名（可能是拼音+数字），昵称才是他自己起的。
 */
internal fun BangumiCreatorCommentDto.toCreatorComment(): CreatorComment? {
    val author = user ?: return null
    return CreatorComment(
        id = id,
        user = CreatorComment.User(
            id = author.id,
            name = author.nickname?.takeIf(String::isNotBlank) ?: author.username.orEmpty(),
            avatarUrl = author.avatar?.medium ?: author.avatar?.large,
        ),
        content = content.orEmpty(),
        createdAt = createdAt,
        replies = replies.mapNotNull { it.toCreatorComment() },
    )
}
