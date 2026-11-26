package com.liomix.mikomi.data.anime.remote.anirpb

import com.liomix.mikomi.core.model.AniRpbHotItem
import com.liomix.mikomi.core.model.AniRpbScheduleItem
import com.liomix.mikomi.core.model.anime.HotRanking
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/**
 * AniRpb 报文 → 模型。
 *
 * ## 为什么没有 DTO 类
 *
 * Bangumi / AniList 有 `dto/`，这里没有 —— 不是漏了。AniRpb 的传输层交回来的是
 * 裸 JSON 字符串，字段名带业务前缀（`customText` / `videoCalendarData`），而且同一份
 * 数据在不同 op 里出现的位置不同（`videos` 或 `items`）。用 `@Serializable` 建 DTO
 * 只能建一堆「和 JSON 长得一样但没被任何地方使用」的中间类，解析时还得再转一次。
 * 直接读 `JSONObject` 少一层，代价是解析代码要自己兜空值 —— 那正好集中在这个文件里。
 *
 * ## 解析 vs 映射
 *
 * AniCyk 只有一层「文案要抠字」的解析；这里的区别是**多了一步包封校验**：
 * 响应外面套了 `frameStatus` / `tokenMatch` / `code` 三层，任何一层不对都要在
 * [decodeEnvelope] 里带着 op 名报错，否则排查时只知道「失败了」，不知道是哪一步。
 */
internal object AniRpbMapper {

    /**
     * 校验响应外壳并取出 `data`。
     *
     * 帧状态非 200 时服务端仍会带一小段 body（错误 JSON），一并打出来才能定位原因；
     * 日志只截前 [MAX_ERROR_BODY_CHARS] 个字符，避免把整包 JSON 写进日志。
     */
    fun decodeEnvelope(op: String, response: JSONObject): JSONObject {
        val rawBody = response.optString("body")
        val frameStatus = response.optInt("frameStatus", -1)
        if (frameStatus != HTTP_OK) {
            throw IOException("AniRpb $op frameStatus=$frameStatus body=${rawBody.take(MAX_ERROR_BODY_CHARS)}")
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

    /** `home.load` 的 hot 模块。按播放量降序 —— 报文顺序是推荐位，不是「谁在热」。 */
    fun parseHotItems(data: JSONObject): List<AniRpbHotItem> {
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

        // 播放量相同时 `sortedWith` 稳定，保持报文里的先后。
        return parseVideoArray(
            items = module.optJSONArray("items") ?: return emptyList(),
            order = compareByDescending { it.playCount },
        )
    }

    /** 整榜报文：`data.videos`（`video.categoryList`；搜索用 `items` 兜底）。 */
    fun parseRankingItems(data: JSONObject): List<AniRpbHotItem> {
        val videos = data.optJSONArray("videos") ?: data.optJSONArray("items") ?: return emptyList()
        return parseVideoArray(videos)
    }

    /** 日历报文：按「天」分组，组内是当天更新的视频。 */
    fun parseCalendarItems(data: JSONObject, requestedDate: String): List<AniRpbScheduleItem> {
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
                    add(
                        AniRpbScheduleItem(
                            videoId = videoId,
                            title = title,
                            coverUrl = normalizeImageUrl(video.optString("coverUrl")),
                            synopsis = firstNonBlank(
                                video.optString("description"),
                                video.optString("subTitle"),
                            ).decodeHtmlEntities().ifBlank { null },
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

    /** 报文里的 `pageinfo.pages`：0 或缺失表示未知，由调用方按「本页是否为空」判断。 */
    fun totalPages(data: JSONObject): Int = data.optJSONObject("pageinfo")?.optInt("pages", 0) ?: 0

    /**
     * 视频条目解析：热播榜与整榜字段完全一致，共用一份。
     *
     * 名次在这里统一赋值，而不是构造时用 `size + 1`：那样「排序」必须发生在构造之前，
     * 两段逻辑互相牵制。先解析、再排序、最后编号，每步只看上一步的输出。
     *
     * @param order 决定列表顺序；null 表示保持报文顺序（整榜用）。
     */
    private fun parseVideoArray(
        items: JSONArray,
        order: Comparator<AniRpbHotItem>? = null,
    ): List<AniRpbHotItem> {
        val parsed = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                if (title.isEmpty()) continue

                add(
                    AniRpbHotItem(
                        id = item.optString("id"),
                        title = title,
                        coverUrl = normalizeImageUrl(
                            item.optString("coverUrl").ifBlank { item.optString("posterUrl") },
                        ),
                        updateText = firstNonBlank(
                            item.optString("customText"),
                            item.optString("updateSchedule"),
                        ).decodeHtmlEntities(),
                        currentEpisodes = item.optInt("currentEpisodes", 0).coerceAtLeast(0),
                        score = item.optScore(),
                        playCount = item.optLong("playCount", 0L).coerceAtLeast(0L),
                        tags = item.optStringArray("tags"),
                    ),
                )
            }
        }
        val ordered = if (order == null) parsed else parsed.sortedWith(order)
        return ordered.mapIndexed { index, entry -> entry.copy(rank = index + 1) }
    }

    /**
     * 读一个字符串数组；缺失、为空、元素不是字符串都退回空列表。
     *
     * 不做「猜键名」：报文里没有 `tags` 就是没有，界面上那一行不出现，
     * 而不是去试 `tagList` / `keywords` 之类的名字碰运气。
     */
    private fun JSONObject.optStringArray(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private fun JSONObject.optScore(): Double? {
        if (!has("score") || isNull("score")) return null
        val value = optDouble("score", Double.NaN)
        return value.takeIf { !it.isNaN() && it >= 0.0 }
    }

    private fun parseEpisodeNumber(text: String): Int =
        EPISODE_NUMBER_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    private fun String.decodeHtmlEntities(): String = replace("&#124;", "|").replace("&amp;", "&")

    /**
     * 部分封面是明文 HTTP，而 Android 默认禁止 cleartext，Coil 会直接失败。
     * 实测这些图床同时支持 HTTPS，统一升级 —— 避免为了一张封面放开全应用的 cleartext。
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

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    private const val HTTP_OK = 200
    private const val API_CODE_OK = 200
    private const val API_CODE_OK_ALT = 0
    private const val HOT_MODULE_KEY = "hot"
    private const val HOT_MODULE_ID = "MOD_SYS_HOT"
    private const val HTTP_PREFIX_LENGTH = 7
    private const val MAX_ERROR_BODY_CHARS = 200
    private val EPISODE_NUMBER_REGEX = Regex("(\\d+)")

    /**
     * 报文条目 → 热播榜条目。
     *
     * 字段一一对应，看起来像多余的搬运 —— 但两边会分头演化：报文模型可能被别的 op 复用，
     * 而榜单条目要跟着 UI 走（比如将来加「在追」标记）。直接复用 `AniRpbHotItem`
     * 会让「这条为什么多了个 UI 字段」变成没人答得出的问题。
     */
    fun AniRpbHotItem.toHotRanking(): HotRanking = HotRanking(
        id = id,
        rank = rank,
        title = title,
        coverUrl = coverUrl,
        updateText = updateText,
        currentEpisodes = currentEpisodes,
        score = score,
        playCount = playCount,
        tags = tags,
    )
}
