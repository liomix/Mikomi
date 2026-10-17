package com.liomix.mikomi.data.anime.internal

import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.dao.AnimeDao
import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.HomeRepository
import com.liomix.mikomi.data.anime.mapper.ANILIST_ID_PREFIX
import com.liomix.mikomi.data.anime.mapper.BANGUMI_ID_PREFIX
import com.liomix.mikomi.data.anime.mapper.toDomain
import com.liomix.mikomi.data.anime.mapper.toEntity
import com.liomix.mikomi.data.anime.remote.anilist.AniListApi
import com.liomix.mikomi.data.anime.remote.anilist.AniListQueries
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlRequest
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiTrendApi
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class HomeRepositoryImpl @Inject constructor(
    private val bangumiApi: BangumiTrendApi,
    private val anilistApi: AniListApi,
    private val animeDao: AnimeDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HomeRepository {

    override fun observeBanners(): Flow<List<Anime>> =
        animeDao.observeTopAnime(BANNER_COUNT).map { list -> list.map { it.toDomain() } }

    override fun observeRecommended(): Flow<List<Anime>> =
        animeDao.observeTopAnime(HOME_PAGE_SIZE).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshHome() = withContext(ioDispatcher) {
        // 主数据源 Bangumi；失败（网络/解析）降级到 AniList。此处为数据源容错边界，
        // 捕获 Exception 后记录诊断日志再降级；协程取消不吞掉，直接向上传播。
        val entities = try {
            fetchBangumiTrending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Bangumi trending failed, fallback to AniList", e)
            fetchAniListTrending()
        }
        // 先清空首页数据源（bangumi:/anilist: 前缀）旧数据，再写入本次结果，
        // 避免多源/多版本数据混杂，以及 popularity 量级不同导致的排序错乱。
        animeDao.deleteByPrefixes(BANGUMI_ID_PREFIX, ANILIST_ID_PREFIX)
        animeDao.upsertAll(entities)
    }

    private suspend fun fetchBangumiTrending(): List<AnimeEntity> =
        bangumiApi.fetchTrending(type = TYPE_ANIME, limit = HOME_PAGE_SIZE, offset = 0)
            .data
            .map { it.toEntity() }

    private suspend fun fetchAniListTrending(): List<AnimeEntity> =
        anilistApi.execute(
            AniListGraphqlRequest(
                query = AniListQueries.TRENDING,
                variables = buildJsonObject {
                    put("page", 1)
                    put("perPage", HOME_PAGE_SIZE)
                },
            ),
        ).data?.page?.media.orEmpty().map { it.toEntity() }

    private companion object {
        const val TYPE_ANIME = 2
        const val BANNER_COUNT = 5
        const val HOME_PAGE_SIZE = 20
        const val TAG = "HomeRepository"
    }
}
