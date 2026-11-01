package com.liomix.mikomi.data.anime.internal

import android.content.Context
import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.AniRpbScheduleItem
import com.liomix.mikomi.core.model.RankRegion
import com.liomix.mikomi.data.anime.AniRpbRepository
import com.liomix.mikomi.data.anime.remote.anirpb.ZhuqueTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * AniRpb 数据源实现。
 *
 * 原生传输只有一条 UDS 长连接，因此用 [requestMutex] 串行化所有请求；频道 id 只在
 * 首次需要时查询一次并缓存。`type.listActive` 偶尔变更 id，所以优先动态解析，
 * 仅在返回列表缺少目标频道时使用实测常量兜底。
 */
@Singleton
internal class AniRpbRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AniRpbRepository {

    private val transport = ZhuqueTransport(context)
    private val requestMutex = Mutex()
    private val channelIds = mutableMapOf<RankRegion, String>()

    override suspend fun fetchHotRanking(region: RankRegion): List<AniRpbHotItem> =
        withContext(ioDispatcher) {
            requestMutex.withLock { fetchHotLocked(resolveChannelId(region)) }
        }

    override suspend fun fetchRanking(region: RankRegion, limit: Int): List<AniRpbHotItem> =
        withContext(ioDispatcher) {
            requestMutex.withLock {
                val categoryId = resolveChannelId(region)
                val ranked = try {
                    fetchRankPages(categoryId, limit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 整榜接口不可用时退回首页 hot 模块：短榜胜过空榜。
                    Log.w(TAG, "AniRpb video.categoryList 整榜失败，回退 home.load 热播榜", e)
                    emptyList()
                }
                ranked.ifEmpty { fetchHotLocked(categoryId).take(limit) }
            }
        }

    /** `home.load` 的 hot 模块（首屏热播榜，条数由服务端模块决定）。 */
    private fun fetchHotLocked(categoryId: String): List<AniRpbHotItem> {
        val data = decodeResponse(
            op = OP_HOME_LOAD,
            response = transport.hotRanking(categoryId = categoryId, pageSize = PAGE_SIZE),
        )
        return parseHotItems(data)
    }

    /**
     * 整榜分页：攒够 [limit] 条、服务端没有下一页、或达到页数上限时停。
     *
     * 服务端对 `pageSize` 的实际上限可能低于请求值，所以不能假设「一页就够」；
     * 每页都读 `pageinfo.pages`，翻完就收，不做无用请求。
     */
    private fun fetchRankPages(categoryId: String, limit: Int): List<AniRpbHotItem> {
        val items = mutableListOf<AniRpbHotItem>()
        var page = FIRST_PAGE
        while (items.size < limit && page <= MAX_RANK_PAGES) {
            val data = decodeResponse(
                op = OP_VIDEO_CATEGORY_LIST,
                response = transport.ranking(
                    categoryId = categoryId,
                    pageIndex = page,
                    pageSize = RANK_PAGE_SIZE,
                ),
            )
            val pageItems = parseRankingItems(data)
            if (pageItems.isEmpty()) break
            items += pageItems
            val pages = data.optJSONObject("pageinfo")?.optInt("pages", 0) ?: 0
            if (pages in 1..page) break
            page++
        }
        // id 缺失时用标题兜底：否则 distinctBy 会把所有空 id 的条目并成一条。
        return items.distinctBy { it.id.ifBlank { it.title } }.take(limit)
    }

    override suspend fun fetchCalendar(playDate: String): List<AniRpbScheduleItem> =
        withContext(ioDispatcher) {
            requestMutex.withLock {
                val items = mutableListOf<AniRpbScheduleItem>()
                var page = FIRST_PAGE
                do {
                    val data = decodeResponse(
                        op = OP_CALENDAR_VIDEOS,
                        response = transport.calendar(
                            playDate = playDate,
                            pageIndex = page,
                            pageSize = CALENDAR_PAGE_SIZE,
                        ),
                    )
                    items += parseCalendarItems(data, playDate)
                    val pages = data.optJSONObject("pageinfo")?.optInt("pages", 0) ?: 0
                    page++
                } while (page <= pages && page <= MAX_CALENDAR_PAGES)

                items.distinctBy { item ->
                    listOf(item.videoId, item.playDate, item.updateTime, item.episode).joinToString("|")
                }
            }
        }

    /**
     * 解析产地对应的频道（categoryId）。
     *
     * `type.listActive` 是 `replay_safe` 类 op，服务端在签名引导未完成时会直接回
     * `op dormant (no signSecret; bootstrap first)`。频道 id 只是**入参**，不该因为
     * 拿不到动态列表就把所有 AniRpb 能力（榜单 / 时间表 / 搜索）一起拖死：
     * 解析失败时退回实测常量，请求本身仍会照常发出，能用的 op 继续能用。
     * 失败结果不入缓存，下一次请求会重新尝试动态解析。
     */
    private fun resolveChannelId(region: RankRegion): String {
        channelIds[region]?.let { return it }

        val data = try {
            decodeResponse(
                op = OP_TYPE_LIST_ACTIVE,
                response = transport.channels(),
            )
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
        channelIds[region]?.let { return it }

        // 动态列表缺少目标频道时仍保证可用；下一次请求会重新尝试动态解析。
        return region.fallbackChannelId()
    }

    private fun decodeResponse(op: String, response: JSONObject): JSONObject {
        // 帧状态非 200 时服务端仍会带一小段 body（错误 JSON），一并打出来才能定位原因。
        val rawBody = response.optString("body")
        val frameStatus = response.optInt("frameStatus", -1)
        if (frameStatus != HTTP_OK) {
            val snippet = rawBody.take(MAX_ERROR_BODY_CHARS)
            throw IOException("AniRpb $op frameStatus=$frameStatus body=$snippet")
        }
        if (!response.optBoolean("tokenMatch", false)) {
            throw IOException("AniRpb $op token mismatch")
        }

        if (rawBody.isBlank()) {
            throw IOException("AniRpb $op returned an empty body")
        }

        val body = runCatching { JSONObject(rawBody) }.getOrElse { cause ->
            throw IOException("AniRpb $op returned invalid JSON", cause)
        }
        val code = body.optInt("code", -1)
        if (code != API_CODE_OK && code != API_CODE_OK_ALT) {
            val message = body.optString("msg").ifBlank { body.optString("message") }
            throw IOException("AniRpb $op code=$code ${message}".trim())
        }
        return body.optJSONObject("data")
            ?: throw IOException("AniRpb $op response has no data object")
    }

    private fun parseHotItems(data: JSONObject): List<AniRpbHotItem> {
        val modules = data.optJSONArray("list")
            ?: throw IOException("AniRpb home.load response has no module list")
        var hotModule: JSONObject? = null
        for (index in 0 until modules.length()) {
            val module = modules.optJSONObject(index) ?: continue
            if (module.optString("k") == HOT_MODULE_KEY || module.optString("id") == HOT_MODULE_ID) {
                hotModule = module
                break
            }
        }
        val module = hotModule
            ?: throw IOException("AniRpb home.load response has no hot module")
        return parseVideoArray(module.optJSONArray("items") ?: return emptyList())
    }

    /** 整榜报文：`data.videos`（`video.categoryList` / 搜索用 `items` 兜底）。 */
    private fun parseRankingItems(data: JSONObject): List<AniRpbHotItem> {
        val videos = data.optJSONArray("videos") ?: data.optJSONArray("items") ?: return emptyList()
        return parseVideoArray(videos)
    }

    /** 榜单条目解析：热播榜与整榜的字段完全一致，共用一份实现。 */
    private fun parseVideoArray(items: JSONArray): List<AniRpbHotItem> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val title = item.optString("title").trim()
            if (title.isEmpty()) continue

            val coverUrl = normalizeImageUrl(
                item.optString("coverUrl").ifBlank { item.optString("posterUrl") },
            )

            add(
                AniRpbHotItem(
                    id = item.optString("id"),
                    rank = size + 1,
                    title = title,
                    coverUrl = coverUrl,
                    updateText = firstNonBlank(
                        item.optString("customText"),
                        item.optString("updateSchedule"),
                    ).decodeHtmlEntities(),
                    currentEpisodes = item.optInt("currentEpisodes", 0).coerceAtLeast(0),
                    score = item.optScore(),
                    playCount = item.optLong("playCount", 0L).coerceAtLeast(0L),
                ),
            )
        }
    }

    private fun parseCalendarItems(data: JSONObject, requestedDate: String): List<AniRpbScheduleItem> {
        val groups = data.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(groupIndex) ?: continue
                val playDate = group.optString("playDate").trim().ifBlank { requestedDate }
                val updateTime = group.optString("updateTime").trim()
                val videos = group.optJSONArray("videoCalendarData") ?: continue
                for (videoIndex in 0 until videos.length()) {
                    val video = videos.optJSONObject(videoIndex) ?: continue
                    val videoId = video.optString("videoId").trim()
                    val title = video.optString("videoName").trim()
                    if (videoId.isEmpty() || title.isEmpty()) continue

                    val episodeText = video.optString("episodeNum").trim()
                    val coverUrl = normalizeImageUrl(video.optString("coverUrl"))
                    val synopsis = firstNonBlank(
                        video.optString("description"),
                        video.optString("subTitle"),
                    ).decodeHtmlEntities().ifBlank { null }

                    add(
                        AniRpbScheduleItem(
                            videoId = videoId,
                            title = title,
                            coverUrl = coverUrl,
                            synopsis = synopsis,
                            playDate = playDate,
                            updateTime = updateTime,
                            episodeText = episodeText,
                            episode = parseEpisodeNumber(episodeText),
                            updateText = video.optString("updateDesc").trim().decodeHtmlEntities(),
                            channelId = video.optString("channelId").trim(),
                        ),
                    )
                }
            }
        }
    }

    private fun parseEpisodeNumber(text: String): Int =
        EPISODE_NUMBER_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    private fun String.decodeHtmlEntities(): String = replace("&#124;", "|")
        .replace("&amp;", "&")

    /**
     * AniRpb 的部分封面是明文 HTTP，而 Android 默认禁止 cleartext，Coil 会直接失败。
     * 实测这些图床同时支持 HTTPS，这里统一升级，避免为了封面放开全应用 cleartext。
     */
    private fun normalizeImageUrl(raw: String): String? {
        val url = raw.trim()
        if (url.isEmpty()) return null
        return if (url.startsWith("http://", ignoreCase = true)) {
            "https://" + url.substring(HTTP_PREFIX_LENGTH)
        } else {
            url
        }
    }

    private fun JSONObject.optScore(): Double? {
        if (!has("score") || isNull("score")) return null
        val value = optDouble("score", Double.NaN)
        return value.takeIf { !it.isNaN() && it >= 0.0 }
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    private fun RankRegion.fallbackChannelId(): String = when (this) {
        RankRegion.CHINESE -> FALLBACK_CHINESE_CHANNEL_ID
        RankRegion.JAPANESE -> FALLBACK_JAPANESE_CHANNEL_ID
    }

    private companion object {
        const val TAG = "AniRpbRepository"

        const val OP_TYPE_LIST_ACTIVE = "type.listActive"
        const val OP_HOME_LOAD = "home.load"
        const val OP_VIDEO_CATEGORY_LIST = "video.categoryList"
        const val OP_CALENDAR_VIDEOS = "calendar.videos"

        const val HOT_MODULE_KEY = "hot"
        const val HOT_MODULE_ID = "MOD_SYS_HOT"

        const val HTTP_OK = 200
        const val API_CODE_OK = 200
        const val API_CODE_OK_ALT = 0
        const val PAGE_SIZE = 20
        const val CALENDAR_PAGE_SIZE = 20

        /** 整榜单页条数：服务端可能按更小的上限裁剪，靠翻页补足。 */
        const val RANK_PAGE_SIZE = 100

        /** 整榜翻页上限（100 条 × 10 页远大于目标，纯属防御）。 */
        const val MAX_RANK_PAGES = 10
        const val FIRST_PAGE = 1
        const val MAX_CALENDAR_PAGES = 4
        const val HTTP_PREFIX_LENGTH = 7

        /** 错误日志里保留的 body 长度上限，避免把整包 JSON 写进日志。 */
        const val MAX_ERROR_BODY_CHARS = 200

        val EPISODE_NUMBER_REGEX = Regex("(\\d+)")

        /** 实测兜底值；正常路径始终优先使用 `type.listActive` 的动态结果。 */
        const val FALLBACK_JAPANESE_CHANNEL_ID = "316207249407696896"
        const val FALLBACK_CHINESE_CHANNEL_ID = "316207250154283008"
    }
}

