package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.database.entity.EpisodeEntity
import com.liomix.mikomi.core.database.entity.RankEntity
import com.liomix.mikomi.core.database.entity.ScheduleEntity
import com.liomix.mikomi.core.model.AirTime
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.core.model.Episode
import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.data.anime.remote.dto.AnimeDto
import com.liomix.mikomi.data.anime.schedule.ScheduleContentFilter

internal fun AnimeDto.toEntity(): AnimeEntity = AnimeEntity(
    id = id,
    title = title,
    coverUrl = coverUrl,
    synopsis = synopsis,
    rating = rating,
    status = status ?: "unknown",
)

internal fun AnimeDto.toEpisodeEntities(): List<EpisodeEntity> = episodes.map { episode ->
    EpisodeEntity(
        id = episode.id,
        animeId = id,
        number = episode.number,
        title = episode.title,
        durationSec = episode.durationSec,
    )
}

internal fun AnimeEntity.toDomain(): Anime = Anime(
    id = id,
    title = title,
    coverUrl = coverUrl,
    synopsis = synopsis,
    rating = rating,
    status = AnimeStatus.from(status),
    popularity = popularity,
    airDate = airDate.takeIf { it.isNotBlank() },
    rank = rank,
    ratingCount = ratingCount,
    genres = genres.split(GENRES_SEPARATOR).filter { it.isNotBlank() },
)

internal fun RankEntity.toDomain(): Anime = Anime(
    id = id,
    title = title,
    coverUrl = coverUrl,
    synopsis = synopsis,
    rating = rating,
    status = AnimeStatus.from(status),
    popularity = popularity,
    genres = genres.split(GENRES_SEPARATOR).filter { it.isNotBlank() },
)

internal fun ScheduleEntity.toScheduleEntry(): ScheduleEntry = ScheduleEntry(
    anime = Anime(
        id = animeId,
        title = title,
        coverUrl = coverUrl,
        synopsis = synopsis,
        rating = rating,
    ),
    airTime = AirTime(
        date = airTimeDate.takeIf { it.isNotBlank() },
        time = airTime.takeIf { it.isNotBlank() },
        precision = precision.toAirTimePrecision(),
        inferred = inferred,
    ),
    airingAtMillis = airingAtMillis,
    episode = episode,
    episodeText = episodeText,
    // 兜底再过一遍过滤规则：升级前落库的行还留着 `4#周三21:00更` 原文，读的时候统一收敛成
    // 「只有具体时刻」，不必等下一次刷新（规则幂等）。
    updateText = ScheduleContentFilter.filterUpdateText(updateText),
    channelId = channelId,
    detailLink = detailLink,
    canonicalKey = canonicalKey,
    sourceId = sourceId,
)

internal fun EpisodeEntity.toDomain(): Episode = Episode(
    id = id,
    animeId = animeId,
    number = number,
    title = title,
    durationSec = durationSec,
)
