package com.liomix.mikomi.data.anime.internal

import android.util.Log
import androidx.room.withTransaction
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.MikomiDatabase
import com.liomix.mikomi.core.database.dao.RankDao
import com.liomix.mikomi.core.database.entity.RankEntity
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.model.RankRegion
import com.liomix.mikomi.data.anime.AniRpbRepository
import com.liomix.mikomi.data.anime.RankRepository
import com.liomix.mikomi.data.anime.mapper.toDomain
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 热播榜：AniRpb `home.load` 为唯一数据源（国漫 / 日漫各一个频道）。
 *
 * AniRpb 的频道 id 在数据层按 `type.listActive` 动态解析，这里只按产地请求榜单；
 * 名次直接沿用服务端返回顺序（[RankEntity.position]），不再按评分重排。
 * 离线优先由 Room SSOT 保证：刷新失败时继续展示本地缓存。
 */
internal class RankRepositoryImpl @Inject constructor(
    private val aniRpbRepository: AniRpbRepository,
    private val rankDao: RankDao,
    private val database: MikomiDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RankRepository {

    override fun observeRank(region: RankRegion): Flow<List<RankEntry>> =
        rankDao.observeByRegion(region.storageValue()).map { list ->
            list.map { it.toRankEntry() }
        }

    override suspend fun refreshRank(): Unit = withContext(ioDispatcher) {
        val entitiesByRegion = mutableMapOf<RankRegion, List<RankEntity>>()
        var successCount = 0
        var lastFailure: Exception? = null

        for (region in RankRegion.entries) {
            try {
                entitiesByRegion[region] = fetchRank(region)
                successCount++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fetch AniRpb rank failed, region=$region", e)
                lastFailure = e
            }
        }

        // 两榜都失败才向 UI 报错；单榜失败时保留该榜本地缓存。
        if (successCount == 0) {
            lastFailure?.let { throw it }
            return@withContext
        }

        // 空结果不等于「榜上确实没有条目」，更可能是限额 / 空响应。
        // 按产地逐个替换：哪一榜拿到数据才更新哪一榜，不把已展示的榜单抹成骨架屏。
        // 两榜在同一事务里替换，Room Flow 只发射一次，刷新过程中不会闪空数据。
        database.withTransaction {
            entitiesByRegion.forEach { (region, entities) ->
                if (entities.isEmpty()) return@forEach
                rankDao.deleteByRegion(region.storageValue())
                rankDao.upsertAll(entities)
            }
        }
    }

    /** AniRpb 热播榜：名次取接口返回顺序（第 N 条 = position N-1）。 */
    private suspend fun fetchRank(region: RankRegion): List<RankEntity> =
        aniRpbRepository.fetchHotRanking(region).mapIndexed { index, item ->
            item.toRankEntity(region.storageValue(), position = index)
        }

    private fun AniRpbHotItem.toRankEntity(region: String, position: Int): RankEntity = RankEntity(
        id = "$ANIRPB_ID_PREFIX$id",
        region = region,
        title = title,
        coverUrl = coverUrl,
        // AniRpb 只提供评分与更新文案，没有简介 / 类型标签，留空由 UI 自行降级。
        synopsis = null,
        rating = score,
        status = "unknown",
        genres = "",
        position = position,
    )

    private fun RankEntity.toRankEntry(): RankEntry {
        val anime = toDomain()
        return RankEntry(
            anime = anime,
            region = when (region) {
                REGION_CHINESE -> RankRegion.CHINESE
                else -> RankRegion.JAPANESE
            },
            genres = anime.genres,
        )
    }

    private fun RankRegion.storageValue(): String = when (this) {
        RankRegion.CHINESE -> REGION_CHINESE
        RankRegion.JAPANESE -> REGION_JAPANESE
    }

    private companion object {
        /** 产地存储值（[RankEntity.region]）。 */
        const val REGION_CHINESE = "cn"
        const val REGION_JAPANESE = "jp"

        /** 主键前缀：AniRpb 视频 id 与 Bangumi / AniList 条目 id 不在同一命名空间。 */
        const val ANIRPB_ID_PREFIX = "anirpb:"

        const val TAG = "RankRepository"
    }
}
