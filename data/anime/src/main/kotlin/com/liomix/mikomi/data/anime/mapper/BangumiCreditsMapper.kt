package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.model.AnimeCharacter
import com.liomix.mikomi.core.model.CharacterImageSet
import com.liomix.mikomi.core.model.CommentUser
import com.liomix.mikomi.core.model.CreditComment
import com.liomix.mikomi.core.model.CreditDetail
import com.liomix.mikomi.core.model.StaffMember
import com.liomix.mikomi.core.model.VoiceActor
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCharacterDetailDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCharacterDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCommentDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiCreditImageDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiPersonDetailDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiStaffDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiStaffInfoDto
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiVoiceActorDto

/** 角色 DTO → 领域模型。 */
internal fun BangumiCharacterDto.toDomain(): AnimeCharacter = AnimeCharacter(
    id = id,
    name = name,
    relation = relation.orEmpty(),
    images = images?.toDomain(),
    voiceActors = actors.map { it.toDomain() },
)

/** 配音演员 DTO → 领域模型。 */
internal fun BangumiVoiceActorDto.toDomain(): VoiceActor = VoiceActor(
    id = id,
    name = name,
    images = images?.toDomain(),
)

/** 制作人员条目 DTO → 领域模型。 */
internal fun BangumiStaffInfoDto.toDomain(): StaffMember {
    val staff = this.staff
    return StaffMember(
        id = staff?.id ?: 0,
        name = staff?.name.orEmpty(),
        localizedName = staff?.localizedName,
        images = staff?.images?.toDomain(),
        positions = positions.mapNotNull { it.type?.cn ?: it.type?.name },
    )
}

/** 头像尺寸 DTO → 领域模型。 */
internal fun BangumiCreditImageDto.toDomain(): CharacterImageSet = CharacterImageSet(
    small = small,
    grid = grid,
    large = large,
    medium = medium,
)

/** 角色详情 DTO → 领域模型。 */
internal fun BangumiCharacterDetailDto.toCreditDetail(): CreditDetail = CreditDetail(
    id = id,
    name = name.orEmpty(),
    localizedName = localizedName,
    imageUrl = images?.large,
    info = info,
    summary = summary,
)

/** 人物详情 DTO → 领域模型（infobox 拼接为基本信息文本）。 */
internal fun BangumiPersonDetailDto.toCreditDetail(): CreditDetail = CreditDetail(
    id = id,
    name = name.orEmpty(),
    localizedName = localizedName,
    imageUrl = images?.large,
    info = infobox.joinToString("\n") { item ->
        val key = item.key.orEmpty()
        if (key.isEmpty()) return@joinToString ""
        val value = item.value?.let { element ->
            when {
                element is kotlinx.serialization.json.JsonPrimitive && element.isString ->
                    element.content

                element is kotlinx.serialization.json.JsonArray ->
                    element.joinToString(", ") { e ->
                        (e as? kotlinx.serialization.json.JsonObject)
                            ?.get("v")
                            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                            ?: e.toString()
                    }

                else -> element.toString()
            }
        }.orEmpty().trim()
        // 值为空（如「简体中文名: 」）时跳过该行，避免空键值对。
        if (value.isEmpty()) return@joinToString ""
        "$key: $value"
    }.takeIf { it.isNotBlank() },
    summary = summary,
)

/** 评论 DTO → 领域模型（递归回复）。 */
internal fun BangumiCommentDto.toDomain(): CreditComment = CreditComment(
    id = id,
    user = CommentUser(
        id = user?.id ?: 0,
        username = user?.username.orEmpty(),
        nickname = user?.nickname.orEmpty(),
        avatarUrl = user?.avatar?.large ?: user?.avatar?.medium,
    ),
    content = content.orEmpty(),
    createdAt = createdAt,
    replies = replies.map { it.toDomain() },
)

/** 角色关系排序权重：主角 > 配角 > 客串 > 其他。 */
private val RELATION_ORDER = mapOf(
    "主角" to 1,
    "配角" to 2,
    "客串" to 3,
)

/** 制作人员职位排序权重：原作 > 导演 > 系列构成 > 角色设计 > 音乐 > 其他。 */
private val POSITION_ORDER = mapOf(
    "原作" to 1,
    "导演" to 2,
    "系列构成" to 3,
    "角色设计" to 4,
    "音乐" to 5,
)

/** 角色排序：主角 > 配角 > 客串 > 其他（对齐 Dart 版）。 */
internal fun List<AnimeCharacter>.sortedByRelation(): List<AnimeCharacter> =
    sortedBy { RELATION_ORDER[it.relation] ?: 4 }

/** 制作人员排序：原作 > 导演 > 系列构成 > 角色设计 > 音乐 > 其他（对齐 Dart 版）。 */
internal fun List<StaffMember>.sortedByPosition(): List<StaffMember> =
    sortedBy { staff ->
        POSITION_ORDER[staff.positions.firstOrNull().orEmpty()] ?: 99
    }
