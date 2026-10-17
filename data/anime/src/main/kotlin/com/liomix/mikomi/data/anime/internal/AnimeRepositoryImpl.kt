package com.liomix.mikomi.data.anime.internal

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.dao.AnimeDao
import com.liomix.mikomi.core.database.dao.EpisodeDao
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.Episode
import com.liomix.mikomi.data.anime.AnimeRepository
import com.liomix.mikomi.data.anime.mapper.toDomain
import com.liomix.mikomi.data.anime.mapper.toEntity
import com.liomix.mikomi.data.anime.mapper.toEpisodeEntities
import com.liomix.mikomi.data.anime.remote.AnimeApi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class AnimeRepositoryImpl @Inject constructor(
    private val api: AnimeApi,
    private val animeDao: AnimeDao,
    private val episodeDao: EpisodeDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AnimeRepository {

    override fun observeAnimeList(): Flow<List<Anime>> =
        animeDao.observeAnime().map { list -> list.map { it.toDomain() } }

    override fun observeAnime(id: String): Flow<Anime?> =
        animeDao.observeAnime(id).map { it?.toDomain() }

    override fun observeEpisodes(animeId: String): Flow<List<Episode>> =
        episodeDao.observeEpisodes(animeId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshAnimeList() = withContext(ioDispatcher) {
        val dtos = api.getAnimeList()
        animeDao.upsertAll(dtos.map { it.toEntity() })
        dtos.forEach { dto -> episodeDao.upsertAll(dto.toEpisodeEntities()) }
    }
}
