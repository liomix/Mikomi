package com.liomix.mikomi.data.anime.internal

import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.dao.AnimeDao
import com.liomix.mikomi.core.database.dao.EpisodeDao
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.Episode
import com.liomix.mikomi.data.anime.AnimeIdMapper
import com.liomix.mikomi.data.anime.AnimeRepository
import com.liomix.mikomi.data.anime.mapper.ANILIST_ID_PREFIX
import com.liomix.mikomi.data.anime.mapper.BANGUMI_ID_PREFIX
import com.liomix.mikomi.data.anime.mapper.toDetailEntity
import com.liomix.mikomi.data.anime.mapper.toDomain
import com.liomix.mikomi.data.anime.mapper.toEntity
import com.liomix.mikomi.data.anime.mapper.toEpisodeEntities
import com.liomix.mikomi.data.anime.remote.AnimeApi
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiApi
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class AnimeRepositoryImpl @Inject constructor(
    private val api: AnimeApi,
    private val bangumiApi: BangumiApi,
    private val animeIdMapper: AnimeIdMapper,
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

    override suspend fun refreshAnimeDetail(animeId: String) = withContext(ioDispatcher) {
        val bangumiId = resolveBangumiId(animeId) ?: return@withContext
        val detail = bangumiApi.getSubject(bangumiId)
        animeDao.upsertAll(listOf(detail.toDetailEntity()))
    }

    /** 解析领域 id → Bangumi 数字 id；AniList 前缀先做 id 映射。 */
    private suspend fun resolveBangumiId(animeId: String): Int? {
        val bangumiRaw = animeId.removePrefix(BANGUMI_ID_PREFIX)
        if (bangumiRaw != animeId) {
            // 原 id 已是 bangumi: 前缀，直接解析数字。
            return bangumiRaw.toIntOrNull()
        }
        val anilistRaw = animeId.removePrefix(ANILIST_ID_PREFIX)
        if (anilistRaw != animeId) {
            // anilist: 前缀，映射到 Bangumi。
            val anilistId = anilistRaw.toIntOrNull() ?: return null
            return try {
                animeIdMapper.aniListToBangumi(anilistId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "AniList→Bangumi id mapping failed", e)
                null
            }
        }
        return null
    }

    private companion object {
        const val TAG = "AnimeRepository"
    }
}
