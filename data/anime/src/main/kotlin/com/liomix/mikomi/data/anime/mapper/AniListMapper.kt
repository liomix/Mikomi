package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.database.entity.ScheduleEntity
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListAiringScheduleDto
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListMediaDto
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListTitleDto

/** AniList 评分 0–100，统一换算为 0–10 的领域评分，与其他数据源口径一致。 */
private const val ANILIST_SCORE_DIVISOR = 10.0

private val HTML_TAG_REGEX = Regex("<[^>]*>")
private val HTML_BR_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

/**
 * AniList 简介为 HTML 富文本（`<i>`、`<br>` 等标记），清洗为纯文本供 UI 直接展示；
 * 换行标记替换为空格，避免剥除后单词粘连。
 */
internal fun stripHtml(html: String): String = html
    .replace(HTML_BR_REGEX, " ")
    .replace(HTML_TAG_REGEX, "")
    .trim()

private const val MILLIS_PER_SECOND = 1_000L

internal fun AniListMediaDto.toEntity(): AnimeEntity = AnimeEntity(
    id = "$ANILIST_ID_PREFIX$id",
    title = title.displayTitle(),
    coverUrl = coverImage?.large ?: coverImage?.medium,
    synopsis = description?.let(::stripHtml),
    rating = averageScore?.toDouble()?.div(ANILIST_SCORE_DIVISOR),
    status = status.toInternalStatus(),
    popularity = popularity ?: 0,
)

/** 标题回退顺序：romaji → english → native。 */
internal fun AniListTitleDto?.displayTitle(): String =
    this?.romaji ?: this?.english ?: this?.nativeTitle ?: ""

/** 放送计划 → 时间表实体；media 缺失的残留条目返回 null，由调用方丢弃。 */
internal fun AniListAiringScheduleDto.toScheduleEntity(): ScheduleEntity? {
    val media = media ?: return null
    return ScheduleEntity(
        id = "$ANILIST_ID_PREFIX${media.id}:$episode",
        animeId = "$ANILIST_ID_PREFIX${media.id}",
        title = media.title.displayTitle(),
        coverUrl = media.coverImage?.large ?: media.coverImage?.medium,
        synopsis = media.description?.let(::stripHtml),
        rating = media.averageScore?.toDouble()?.div(ANILIST_SCORE_DIVISOR),
        airingAtMillis = airingAt * MILLIS_PER_SECOND,
        episode = episode,
    )
}

/** 主键前缀，与 Bangumi 等数据源的 id 空间隔离，避免落同一张表时碰撞。 */
internal const val ANILIST_ID_PREFIX = "anilist:"

/** AniList 枚举值 → 内部状态字符串（对应 [com.liomix.mikomi.core.model.AnimeStatus] 语义）。 */
internal fun String?.toInternalStatus(): String = when (this) {
    "RELEASING" -> "ongoing"
    "FINISHED" -> "completed"
    "NOT_YET_RELEASED" -> "upcoming"
    else -> "unknown"
}
