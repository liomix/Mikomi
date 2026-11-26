package com.liomix.mikomi.data.anime.remote.anicyk

import com.liomix.mikomi.data.anime.remote.anicyk.dto.CykScheduleItemDto
import java.util.Locale
import kotlinx.serialization.json.Json

/**
 * AniCyk 报文 → 领域模型的解析规则（章节文案 → 时刻 / 集数 / 详情 id）。
 *
 * 之所以是「解析」而不是简单映射：服务端只给一段混排文案（`更新至32话 ⏰09:00`），
 * 时刻、集数、详情 id 都得从里面抠出来，抠不出来时按「未知」降级而不是丢条目。
 *
 * 同文件里放着解析产物 [CykScheduleDay] / [CykScheduleItem]：它们不是 DTO，
 * 也不该被上层当领域模型用（上游还要做多源合并），放在这里最贴近产出它的地方。
 */
internal object AniCykMapper {

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

    /**
     * 详情页链接 → 站点内 id；解析不出时退回链接本身。
     *
     * 两条线路的路径格式不同，但都以 `<数字>.html` 结尾：
     * 超清 `https://www.lmm85.com/detail/8220.html` → `8220`；
     * 樱花 `https://www.yinhuadm.cc/v/29505.html` → `29505`。
     */
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
    private val DETAIL_ID = Regex("(\\d+)\\.html")
    private val MULTI_SPACE = Regex("\\s+")
}

/** 单日日历条目（已解析出时刻/集数/详情 id）。 */
internal data class CykScheduleItem(
    val detailId: String,
    val title: String,
    val coverUrl: String?,
    /** 章节文案原文，例如 `更新至32话`。 */
    val chapterText: String,
    /** 章节文案里解析出的时刻 `HH:mm`，没有则为 null。 */
    val airTime: String?,
    /** 从章节文案解析出的集数；无法解析时为 0。 */
    val episode: Int,
    val detailLink: String,
)

/** 一天的日历条目；[index] 从 0 开始，与报文顺序一致。 */
internal data class CykScheduleDay(
    val index: Int,
    val items: List<CykScheduleItem>,
)
