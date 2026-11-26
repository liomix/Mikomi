package com.liomix.mikomi.data.anime.internal

import android.util.Log
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.AniRpbScheduleItem
import com.liomix.mikomi.core.model.anime.RankRegion
import com.liomix.mikomi.data.anime.AniRpbRepository
import com.liomix.mikomi.data.anime.remote.anirpb.AniRpbSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * AniRpb 门面：**只决定「要什么」**。
 *
 * 请求、连接串行化、频道 id 解析、报文解析都在 [AniRpbSource]；这里只剩三条策略：
 *
 * 1. 整榜攒够 `limit` 条就停 —— 每一页都读 `pageinfo.pages`，翻完就收，不做无用请求；
 * 2. 整榜失败退回 `home.load` 热播模块 —— 短榜胜过空榜；
 * 3. 日历翻页直到没有下一页，再按「视频 + 日期 + 时刻 + 集数」去重。
 *
 * 这三件都是「业务要什么」，不是「怎么取」，所以留在这一层。
 */
@Singleton
internal class AniRpbRepositoryImpl @Inject constructor(
    private val source: AniRpbSource,
) : AniRpbRepository {

    override suspend fun fetchHotRanking(region: RankRegion): List<AniRpbHotItem> =
        source.hotRanking(region)

    override suspend fun fetchRanking(region: RankRegion, limit: Int): List<AniRpbHotItem> {
        val ranked = try {
            fetchRankPages(region, limit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 整榜接口不可用时退回首页 hot 模块：短榜胜过空榜。
            Log.w(TAG, "AniRpb video.categoryList 整榜失败，回退 home.load 热播榜", e)
            emptyList()
        }
        return ranked.ifEmpty { source.hotRanking(region).take(limit) }
    }

    override suspend fun fetchCalendar(playDate: String): List<AniRpbScheduleItem> {
        val items = mutableListOf<AniRpbScheduleItem>()
        var page = FIRST_PAGE
        var totalPages = 0

        do {
            val result = source.calendarPage(
                playDate = playDate,
                page = page,
                pageSize = CALENDAR_PAGE_SIZE,
            )
            items += result.items
            totalPages = result.totalPages
            page++
        } while (page <= totalPages && page <= MAX_CALENDAR_PAGES)

        return items.distinctBy { item ->
            listOf(item.videoId, item.playDate, item.updateTime, item.episode).joinToString("|")
        }
    }

    /**
     * 整榜分页：攒够 [limit] 条、服务端没有下一页、或达到页数上限时停。
     *
     * 服务端对 `pageSize` 的实际上限可能低于请求值，所以不能假设「一页就够」。
     */
    private suspend fun fetchRankPages(region: RankRegion, limit: Int): List<AniRpbHotItem> {
        val items = mutableListOf<AniRpbHotItem>()
        var page = FIRST_PAGE

        while (items.size < limit && page <= MAX_RANK_PAGES) {
            val result = source.rankingPage(
                region = region,
                page = page,
                pageSize = RANK_PAGE_SIZE,
            )
            if (result.items.isEmpty()) break
            items += result.items
            if (result.totalPages in 1..page) break
            page++
        }

        // id 缺失时用标题兜底：否则 distinctBy 会把所有空 id 的条目并成一条。
        return items.distinctBy { it.id.ifBlank { it.title } }.take(limit)
    }

    private companion object {
        const val TAG = "AniRpbRepository"

        const val FIRST_PAGE = 1
        const val CALENDAR_PAGE_SIZE = 20

        /** 整榜单页条数：服务端可能按更小的上限裁剪，靠翻页补足。 */
        const val RANK_PAGE_SIZE = 100

        /** 整榜翻页上限（100 条 × 10 页远大于目标，纯属防御）。 */
        const val MAX_RANK_PAGES = 10
        const val MAX_CALENDAR_PAGES = 4
    }
}
