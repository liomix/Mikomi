package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListMediaDto

/** AniList 评分 0–100，统一换算为 0–10 的领域评分，与其他数据源口径一致。 */
private const val ANILIST_SCORE_DIVISOR = 10.0

internal fun AniListMediaDto.toEntity(): AnimeEntity = AnimeEntity(
    id = id.toString(),
    title = title?.romaji
        ?: title?.english
        ?: title?.nativeTitle
        ?: "",
    coverUrl = coverImage?.large ?: coverImage?.medium,
    synopsis = description,
    rating = averageScore?.toDouble()?.div(ANILIST_SCORE_DIVISOR),
    status = status.toInternalStatus(),
    popularity = popularity ?: 0,
)

/** AniList 枚举值 → 内部状态字符串（对应 [com.liomix.mikomi.core.model.AnimeStatus] 语义）。 */
internal fun String?.toInternalStatus(): String = when (this) {
    "RELEASING" -> "ongoing"
    "FINISHED" -> "completed"
    "NOT_YET_RELEASED" -> "upcoming"
    else -> "unknown"
}
