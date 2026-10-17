package com.liomix.mikomi.data.anime.internal

import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.dao.AnimeDao
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.HomeRepository
import com.liomix.mikomi.data.anime.mapper.toDomain
import com.liomix.mikomi.data.anime.mapper.toEntity
import com.liomix.mikomi.data.anime.remote.anilist.AniListApi
import com.liomix.mikomi.data.anime.remote.anilist.AniListQueries
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlRequest
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListPageVariables
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class HomeRepositoryImpl @Inject constructor(
    private val api: AniListApi,
    private val animeDao: AnimeDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HomeRepository {

    override fun observeBanners(): Flow<List<Anime>> =
        animeDao.observeTopAnime(BANNER_COUNT).map { list -> list.map { it.toDomain() } }

    override fun observeRecommended(): Flow<List<Anime>> =
        animeDao.observeTopAnime(HOME_PAGE_SIZE).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshHome() = withContext(ioDispatcher) {
        val response = api.fetchTrending(
            AniListGraphqlRequest(
                query = AniListQueries.TRENDING,
                variables = AniListPageVariables(page = 1, perPage = HOME_PAGE_SIZE),
            ),
        )
        val media = response.data?.page?.media.orEmpty()
        animeDao.upsertAll(media.map { it.toEntity() })
    }

    private companion object {
        const val BANNER_COUNT = 5
        const val HOME_PAGE_SIZE = 20
    }
}
