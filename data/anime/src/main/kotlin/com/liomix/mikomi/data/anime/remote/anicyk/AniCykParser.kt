package com.liomix.mikomi.data.anime.remote.anicyk

import java.util.Locale
import kotlinx.serialization.json.Json

/** AniCyk 报文的解析规则（章节文案 → 时刻/集数/详情 id）。 */
internal object AniCykParser {

    fun parseDays(body: String, json: Json): List<CykScheduleDay> {
        val days = json.decodeFromString<List<List<CykScheduleItemDto>>>(body)
        return days.mapIndexed { index, items ->
            CykScheduleDay(
                index = index,
                items = items.mapNotNull { it.toItem() },
            )
        }
    }

    private fun CykScheduleItemDto.toItem(): CykScheduleItem? {
        val safeTitle = title.trim()
        if (safeTitle.isEmpty()) return null
        return CykScheduleItem(
            detailId = parseDetailId(link),
            title = safeTitle,
            coverUrl = normalizeImageUrl(cover),
            chapterText = cleanChapter(chapter),
            airTime = parseAirTime(chapter),
            episode = parseEpisode(chapter),
            detailLink = link.trim(),
        )
    }

    /** 展示用章节文案：去掉 `⏰09:00` 这段（时刻单独用 AirTime 建模）与多余空白。 */
    fun cleanChapter(chapter: String): String =
        chapter.replace(CLOCK_WITH_ICON, " ").replace(MULTI_SPACE, " ").trim()

    /** `更新至32话   ⏰09:00` → `09:00`；`更新至705话` → null。 */
    fun parseAirTime(chapter: String): String? {
        val match = CLOCK_WITH_ICON.find(chapter) ?: CLOCK_PLAIN.find(chapter) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    /** `更新至32话` → 32；`更新至第5集` → 5；解析不出返回 0。 */
    fun parseEpisode(chapter: String): Int =
        EPISODE_NUMBER.find(chapter)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

    /** `https://www.lmm85.com/detail/8220.html` → `8220`；缺失时退回链接本身。 */
    fun parseDetailId(link: String): String {
        val trimmed = link.trim()
        DETAIL_ID.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        return trimmed
    }

    /**
     * 封面统一升级到 HTTPS：明文图床会触发 cleartext 拦截，而实测同一图床支持 HTTPS。
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

    private const val HTTP_PREFIX_LENGTH = 7

    private val CLOCK_WITH_ICON = Regex("⏰\\s*(\\d{1,2}):(\\d{2})")
    private val CLOCK_PLAIN = Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)")
    private val EPISODE_NUMBER = Regex("(\\d+)")
    private val DETAIL_ID = Regex("/detail/(\\d+)\\.html")
    private val MULTI_SPACE = Regex("\\s+")
}
