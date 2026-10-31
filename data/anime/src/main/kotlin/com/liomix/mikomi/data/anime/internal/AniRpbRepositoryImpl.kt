package com.liomix.mikomi.data.anime.internal

import android.content.Context
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
            requestMutex.withLock {
                val categoryId = resolveChannelId(region)
                val data = decodeResponse(
                    op = OP_HOME_LOAD,
                    response = transport.hotRanking(categoryId = categoryId, pageSize = PAGE_SIZE),
                )
                parseHotItems(data)
            }
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

    private fun resolveChannelId(region: RankRegion): String {
        channelIds[region]?.let { return it }

        val data = decodeResponse(
            op = OP_TYPE_LIST_ACTIVE,
            response = transport.channels(),
        )
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
        val frameStatus = response.optInt("frameStatus", -1)
        if (frameStatus != HTTP_OK) {
            throw IOException("AniRpb $op frameStatus=$frameStatus")
        }
        if (!response.optBoolean("tokenMatch", false)) {
            throw IOException("AniRpb $op token mismatch")
        }

        val rawBody = response.optString("body")
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
        val items = module.optJSONArray("items") ?: return emptyList()

        return buildList {
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
        const val OP_TYPE_LIST_ACTIVE = "type.listActive"
        const val OP_HOME_LOAD = "home.load"
        const val OP_CALENDAR_VIDEOS = "calendar.videos"

        const val HOT_MODULE_KEY = "hot"
        const val HOT_MODULE_ID = "MOD_SYS_HOT"

        const val HTTP_OK = 200
        const val API_CODE_OK = 200
        const val API_CODE_OK_ALT = 0
        const val PAGE_SIZE = 20
        const val CALENDAR_PAGE_SIZE = 20
        const val FIRST_PAGE = 1
        const val MAX_CALENDAR_PAGES = 4
        const val HTTP_PREFIX_LENGTH = 7

        val EPISODE_NUMBER_REGEX = Regex("(\\d+)")

        /** 实测兜底值；正常路径始终优先使用 `type.listActive` 的动态结果。 */
        const val FALLBACK_JAPANESE_CHANNEL_ID = "316207249407696896"
        const val FALLBACK_CHINESE_CHANNEL_ID = "316207250154283008"
    }
}

