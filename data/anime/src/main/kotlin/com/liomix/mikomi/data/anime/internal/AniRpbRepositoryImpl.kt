package com.liomix.mikomi.data.anime.internal

import android.content.Context
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.model.AniRpbHotItem
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
 * AniRpb 热播榜实现。
 *
 * 原生传输只有一条 UDS 长连接，因此用 [mutex] 串行化所有请求；频道 id 只在
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

                val coverUrl = item.optString("coverUrl")
                    .ifBlank { item.optString("posterUrl") }
                    .trim()
                    .ifBlank { null }

                add(
                    AniRpbHotItem(
                        id = item.optString("id"),
                        rank = size + 1,
                        title = title,
                        coverUrl = coverUrl,
                        updateText = firstNonBlank(
                            item.optString("customText"),
                            item.optString("updateSchedule"),
                        ),
                        currentEpisodes = item.optInt("currentEpisodes", 0).coerceAtLeast(0),
                        score = item.optScore(),
                        playCount = item.optLong("playCount", 0L).coerceAtLeast(0L),
                    ),
                )
            }
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

        const val HOT_MODULE_KEY = "hot"
        const val HOT_MODULE_ID = "MOD_SYS_HOT"

        const val HTTP_OK = 200
        const val API_CODE_OK = 200
        const val API_CODE_OK_ALT = 0
        const val PAGE_SIZE = 20

        /** 实测兜底值；正常路径始终优先使用 `type.listActive` 的动态结果。 */
        const val FALLBACK_JAPANESE_CHANNEL_ID = "316207249407696896"
        const val FALLBACK_CHINESE_CHANNEL_ID = "316207250154283008"
    }
}

