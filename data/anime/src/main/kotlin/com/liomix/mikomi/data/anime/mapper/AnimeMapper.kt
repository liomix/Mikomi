package com.liomix.mikomi.data.anime.mapper

import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.database.entity.EpisodeEntity
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.AnimeStatus
import com.liomix.mikomi.core.model.Episode
import com.liomix.mikomi.data.anime.remote.dto.AnimeDto

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
)

internal fun EpisodeEntity.toDomain(): Episode = Episode(
    id = id,
    animeId = animeId,
    number = number,
    title = title,
    durationSec = durationSec,
)
