package com.liomix.mikomi.data.anime.remote.anirpb

import android.content.Context
import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.AniRpbScheduleItem
import com.liomix.mikomi.core.model.anime.RankRegion
import com.liomix.mikomi.data.anime.remote.anirpb.client.ZhuqueTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * AniRpb 数据源的**唯一出口**，与 `BangumiSource` / `AniListSource` 同构。
 *
 * ## 与那两个源的差别
 *
 * 它们走 HTTP + retrofit，这里走**原生 UDS 长连接**（`ZhuqueTransport`）。单连接意味着
 * 请求天然要排队，所以 [requestMutex] 在这里而不是在 Repository —— 这是连接层的属性，
 * 不是业务规则。
 *
 * ## 频道 id
 *
 * 榜单、热播、搜索都要一个 `categoryId`，它由 `type.listActive` 动态查询后缓存。
 * 动态查不到时退回实测常量：拿不到动态列表不该把所有 AniRpb 能力一起拖死。
 * 失败结果**不入缓存**，下次请求会重新尝试。
 *
 * ## 只做请求与解析
 *
 * 翻页策略（攒够多少条、翻到第几页停）、回退（整榜失败改热播）、去重，都在
 * Repository —— 那些是「要什么」，不是「怎么取」。
 */
@Singleton
internal class AniRpbSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val transport = ZhuqueTransport(context)
    private val requestMutex = Mutex()
    private val channelIds = mutableMapOf<RankRegion, String>()

    /** `home.load` 的热播模块（首屏热播榜，条数由服务端模块决定）。 */
    suspend fun hotRanking(region: RankRegion): List<AniRpbHotItem> = withContext(ioDispatcher) {
        requestMutex.withLock {
            val data = AniRpbMapper.decodeEnvelope(
                op = OP_HOME_LOAD,
                response = transport.hotRanking(
                    categoryId = resolveChannelId(region),
                    pageSize = PAGE_SIZE,
                ),
            )
            AniRpbMapper.parseHotItems(data)
        }
    }

    /** 整榜的一页，同时给出总页数（0 或缺失表示未知）。 */
    suspend fun rankingPage(
        region: RankRegion,
        page: Int,
        pageSize: Int,
    ): Page<List<AniRpbHotItem>> = withContext(ioDispatcher) {
        requestMutex.withLock {
            val data = AniRpbMapper.decodeEnvelope(
                op = OP_VIDEO_CATEGORY_LIST,
                response = transport.ranking(
                    categoryId = resolveChannelId(region),
                    pageIndex = page,
                    pageSize = pageSize,
                ),
            )
            Page(
                items = AniRpbMapper.parseRankingItems(data),
                totalPages = AniRpbMapper.totalPages(data),
            )
        }
    }

    /** 日历的一页（某天的更新）。 */
    suspend fun calendarPage(
        playDate: String,
        page: Int,
        pageSize: Int,
    ): Page<List<AniRpbScheduleItem>> = withContext(ioDispatcher) {
        requestMutex.withLock {
            val data = AniRpbMapper.decodeEnvelope(
                op = OP_CALENDAR_VIDEOS,
                response = transport.calendar(
                    playDate = playDate,
                    pageIndex = page,
                    pageSize = pageSize,
                ),
            )
            Page(
                items = AniRpbMapper.parseCalendarItems(data, requestedDate = playDate),
                totalPages = AniRpbMapper.totalPages(data),
            )
        }
    }

    /** 一页结果：[totalPages] 为 0 表示服务端没给，调用方按「本页是否为空」判断。 */
    data class Page<T>(val items: T, val totalPages: Int)

    /**
     * 产地 → 频道 id。
     *
     * `type.listActive` 是 `replay_safe` 类 op，签名引导未完成时服务端会直接回
     * `op dormant`。频道 id 只是**入参**，所以解析失败时退回实测常量，
     * 请求本身仍照常发出。
     */
    private fun resolveChannelId(region: RankRegion): String {
        channelIds[region]?.let { return it }

        val data = try {
            AniRpbMapper.decodeEnvelope(op = OP_TYPE_LIST_ACTIVE, response = transport.channels())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "type.listActive 失败，退回内置频道 id（region=$region）", e)
            return region.fallbackChannelId()
        }

        val types = data.optJSONArray("types") ?: JSONArray()
        for (index in 0 until types.length()) {
            val type = types.optJSONObject(index) ?: continue
            val id = type.optString("id").trim()
            if (id.isEmpty()) continue
            when (type.optString("name")) {
                "日漫" -> channelIds[RankRegion.JAPANESE] = id
                "国漫" -> channelIds[RankRegion.CHINESE] = id
            }
        }

        // 动态列表缺少目标频道时仍保证可用；下次请求会重新尝试动态解析。
        return channelIds[region] ?: region.fallbackChannelId()
    }

    private fun RankRegion.fallbackChannelId(): String = when (this) {
        RankRegion.CHINESE -> FALLBACK_CHINESE_CHANNEL_ID
        RankRegion.JAPANESE -> FALLBACK_JAPANESE_CHANNEL_ID
    }

    private companion object {
        const val TAG = "AniRpbSource"

        const val OP_TYPE_LIST_ACTIVE = "type.listActive"
        const val OP_HOME_LOAD = "home.load"
        const val OP_VIDEO_CATEGORY_LIST = "video.categoryList"
        const val OP_CALENDAR_VIDEOS = "calendar.videos"

        const val PAGE_SIZE = 20

        /** 实测兜底值；正常路径始终优先使用 `type.listActive` 的动态结果。 */
        const val FALLBACK_JAPANESE_CHANNEL_ID = "316207249407696896"
        const val FALLBACK_CHINESE_CHANNEL_ID = "316207250154283008"
    }
}
