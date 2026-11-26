package com.liomix.mikomi.data.anime.internal

import android.util.Log
import androidx.room.withTransaction
import com.liomix.mikomi.core.datastore.cache.CacheKey
import com.liomix.mikomi.core.datastore.cache.CacheStore
import com.liomix.mikomi.core.datastore.cache.CacheTtl
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.MikomiDatabase
import com.liomix.mikomi.core.database.dao.AnimeDao
import com.liomix.mikomi.core.database.entity.AnimeEntity
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.HomeRepository
import com.liomix.mikomi.data.anime.LoadMoreResult
import com.liomix.mikomi.data.anime.remote.anilist.ANILIST_ID_PREFIX
import com.liomix.mikomi.data.anime.mapper.BANGUMI_ID_PREFIX
import com.liomix.mikomi.data.anime.mapper.toDomain
import com.liomix.mikomi.data.anime.mapper.toEntity
import com.liomix.mikomi.data.anime.remote.anilist.AniListSource
import com.liomix.mikomi.data.anime.remote.anilist.client.AniListQueries
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlRequest
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiSource
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class HomeRepositoryImpl @Inject constructor(
    private val bangumiSource: BangumiSource,
    private val anilistSource: AniListSource,
    private val animeDao: AnimeDao,
    private val database: MikomiDatabase,
    private val cacheStore: CacheStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HomeRepository {

    override fun observeBanners(): Flow<List<Anime>> =
        animeDao.observeTopRated(BANNER_COUNT).map { list -> list.map { it.toDomain() } }

    override fun observeRecommended(): Flow<List<Anime>> =
        animeDao.observeAnimeByPopularity().map { list -> list.map { it.toDomain() } }

    override suspend fun refreshHomeIfStale() {
        if (!cacheStore.isStale(CacheKey.HOME, CacheTtl.HOME)) return
        refreshHome()
        // 成功才盖时间戳：失败时下次进来还要重试。
        cacheStore.touch(CacheKey.HOME)
    }

    override suspend fun refreshHome(): LoadMoreResult = withContext(ioDispatcher) {
        val page = fetchPage(offset = 0)
        // 先拉到新页，再在同一事务里清理旧数据并写入，Room Flow 只发射一次：
        // 下拉刷新过程中轮播图与列表不会闪一帧空数据（骨架屏）。
        database.withTransaction {
            animeDao.deleteByPrefixes(BANGUMI_ID_PREFIX, ANILIST_ID_PREFIX)
            animeDao.upsertAll(page.entities.withRank(offset = 0))
        }
        LoadMoreResult(hasMore = page.hasMore, loadedCount = page.entities.size)
    }

    override suspend fun loadMore(offset: Int): LoadMoreResult = withContext(ioDispatcher) {
        val page = fetchPage(offset)
        // 位次接在已加载内容之后：新一页只追加，不插队。
        animeDao.upsertAll(page.entities.withRank(offset))
        LoadMoreResult(hasMore = page.hasMore, loadedCount = page.entities.size)
    }

    private suspend fun fetchPage(offset: Int): HomePage =
        try {
            fetchBangumiPage(offset)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Bangumi trending failed, fallback to AniList", e)
            fetchAniListPage(offset)
        }

    private suspend fun fetchBangumiPage(offset: Int): HomePage {
        val items = bangumiSource.trending(limit = PAGE_SIZE, offset = offset)
        return HomePage(
            entities = items.map { it.toEntity() },
            // 用「本页是否填满」判断，不读 total：趋势接口的 total 与可翻页条数并不总一致。
            hasMore = items.size >= PAGE_SIZE,
        )
    }

    private suspend fun fetchAniListPage(offset: Int): HomePage {
        // 兜底也走统一出口：请求组装（page/perPage）与 DTO 解析都不该散在 Repository 里。
        val page = anilistSource.trending(page = offset / PAGE_SIZE + 1, perPage = PAGE_SIZE)
        return HomePage(
            entities = page.items.map { it.toEntity() },
            hasMore = page.hasNextPage,
        )
    }

    /** 按接口返回顺序写入固定位次（第 N 条 = offset + N）。 */
    private fun List<AnimeEntity>.withRank(offset: Int): List<AnimeEntity> =
        mapIndexed { index, entity -> entity.copy(homeRank = offset + index) }

    private data class HomePage(
        val entities: List<AnimeEntity>,
        val hasMore: Boolean,
    )

    private companion object {
        const val TYPE_ANIME = 2
        const val BANNER_COUNT = 5
        const val PAGE_SIZE = 20
        const val TAG = "HomeRepository"
    }
}
