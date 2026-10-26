package com.liomix.mikomi.data.anime.internal

import android.util.Log
import androidx.room.withTransaction
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.MikomiDatabase
import com.liomix.mikomi.core.database.dao.RankDao
import com.liomix.mikomi.core.database.entity.RankEntity
import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.model.RankRegion
import com.liomix.mikomi.data.anime.RankRepository
import com.liomix.mikomi.data.anime.mapper.GENRES_SEPARATOR
import com.liomix.mikomi.data.anime.mapper.toDomain
import com.liomix.mikomi.data.anime.mapper.toRankEntity
import com.liomix.mikomi.data.anime.remote.anilist.AniListApi
import com.liomix.mikomi.data.anime.remote.anilist.AniListQueries
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlRequest
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiApi
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchFilter
import com.liomix.mikomi.data.anime.remote.bangumi.dto.BangumiSearchRequest
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 综合评分榜：Bangumi 主（标签过滤产地 + 综合评分降序），失败降级 AniList。
 *
 * Bangumi 条目带中文名（name_cn）与站内综合评分（rating.score），是榜单的首选源；
 * 降级路径沿用 AniList countryOfOrigin 查询。离线优先由 Room SSOT 保证。
 */
internal class RankRepositoryImpl @Inject constructor(
    private val bangumiApi: BangumiApi,
    private val anilistApi: AniListApi,
    private val rankDao: RankDao,
    private val database: MikomiDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RankRepository {

    override fun observeRank(region: RankRegion): Flow<List<RankEntry>> =
        rankDao.observeByRegion(region.storageValue()).map { list ->
            list.map { it.toRankEntry() }
        }

    override suspend fun refreshRank(): Unit = withContext(ioDispatcher) {
        val chinese = fetchRank(RankRegion.CHINESE)
        val japanese = fetchRank(RankRegion.JAPANESE)
        // 先拉到两榜数据，再在同一事务里整体替换，Room Flow 只发射一次：
        // 刷新过程中榜单不会闪一帧空数据（骨架屏）。
        database.withTransaction {
            rankDao.clearAll()
            rankDao.upsertAll(chinese + japanese)
        }
    }

    /** Bangumi 主、失败降级 AniList（与首页同策略）。 */
    private suspend fun fetchRank(region: RankRegion): List<RankEntity> =
        try {
            fetchBangumiRank(region)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Bangumi rank failed, fallback to AniList, region=$region", e)
            fetchAniListRank(region)
        }

    /**
     * Bangumi 评分榜：`tag` 过滤产地（国产动画 / 日本），
     * `rank > 0` 只保留进过站内排名的条目（剔除几人评分的虚高分），
     * `sort=score` 按综合评分降序。
     */
    private suspend fun fetchBangumiRank(region: RankRegion): List<RankEntity> {
        val entities = mutableListOf<RankEntity>()
        var offset = 0
        var page = 0
        while (page < MAX_PAGES) {
            val response = bangumiApi.search(
                request = BangumiSearchRequest(
                    keyword = "",
                    sort = SORT_BY_SCORE,
                    filter = BangumiSearchFilter(
                        tag = listOf(region.tagName()),
                        rank = listOf(RANK_POSITIVE),
                    ),
                ),
                limit = PAGE_SIZE,
                offset = offset,
            )
            entities += response.data.map { it.toRankEntity(region.storageValue()) }
            offset += response.data.size
            if (response.data.isEmpty() || offset >= response.total) break
            page++
        }
        return entities
    }

    /** AniList 降级：按产地（countryOfOrigin）取评分榜。 */
    private suspend fun fetchAniListRank(region: RankRegion): List<RankEntity> {
        val entities = mutableListOf<RankEntity>()
        var page = FIRST_PAGE
        while (page <= MAX_PAGES) {
            val response = anilistApi.execute(
                AniListGraphqlRequest(
                    query = AniListQueries.RANK_BY_SCORE,
                    variables = buildJsonObject {
                        put("page", page)
                        put("perPage", PAGE_SIZE)
                        put("country", region.countryCode())
                    },
                ),
            )
            val pageData = response.data?.page
            if (pageData == null) {
                Log.w(TAG, "AniList rank response missing Page data, region=$region")
                break
            }
            entities += pageData.media.map { it.toRankEntity(region.storageValue()) }
            if (pageData.pageInfo?.hasNextPage != true) break
            page++
        }
        return entities
    }

    private fun RankEntity.toRankEntry(): RankEntry = RankEntry(
        anime = toDomain(),
        region = when (region) {
            REGION_CHINESE -> RankRegion.CHINESE
            else -> RankRegion.JAPANESE
        },
        genres = genres.split(GENRES_SEPARATOR).filter { it.isNotBlank() },
    )

    private fun RankRegion.storageValue(): String = when (this) {
        RankRegion.CHINESE -> REGION_CHINESE
        RankRegion.JAPANESE -> REGION_JAPANESE
    }

    /** Bangumi 产地标签（实测：国产动画 743 条 / 日本 1000+ 条，均为有效过滤）。 */
    private fun RankRegion.tagName(): String = when (this) {
        RankRegion.CHINESE -> TAG_CHINESE
        RankRegion.JAPANESE -> TAG_JAPANESE
    }

    /** AniList CountryCode。 */
    private fun RankRegion.countryCode(): String = when (this) {
        RankRegion.CHINESE -> "CN"
        RankRegion.JAPANESE -> "JP"
    }

    private companion object {
        /** 产地存储值（[RankEntity.region]）。 */
        const val REGION_CHINESE = "cn"
        const val REGION_JAPANESE = "jp"

        /** Bangumi 产地标签。 */
        const val TAG_CHINESE = "国产动画"
        const val TAG_JAPANESE = "日本"

        /** Bangumi 排名过滤表达式：仅保留有站内排名的条目。 */
        const val RANK_POSITIVE = ">0"
        const val SORT_BY_SCORE = "score"

        const val FIRST_PAGE = 1
        const val MAX_PAGES = 2
        const val PAGE_SIZE = 25
        const val TAG = "RankRepository"
    }
}
