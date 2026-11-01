package com.liomix.mikomi.data.anime.schedule

import com.liomix.mikomi.core.model.AirTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** 时间表的一周窗口（本地时区，周一为第 0 天）。 */
internal data class ScheduleWeek(
    /** 本周周一 `yyyy-MM-dd`。 */
    val monday: String,
    /** 今天 `yyyy-MM-dd`。 */
    val today: String,
    /** 周一 ~ 周日共 7 个日期。 */
    val dates: List<String>,
) {

    /** 今天在本周中的索引（周一 = 0）。 */
    val todayIndex: Int
        get() = dates.indexOf(today).takeIf { it >= 0 } ?: 0

    fun dateAt(index: Int): String? = dates.getOrNull(index)

    /** 周内偏移（可为负/越界），用于日历源的天索引锚定与窗口裁剪。 */
    fun shift(index: Int, offsetDays: Int): String? {
        val base = dates.getOrNull(index) ?: return null
        if (offsetDays == 0) return base
        return addDays(base, offsetDays)
    }

    companion object {
        private const val DAYS_IN_WEEK = 7
        private const val MONDAY_OFFSET = 5
        private const val DATE_PATTERN = "yyyy-MM-dd"

        fun current(now: Calendar = Calendar.getInstance()): ScheduleWeek {
            val cursor = Calendar.getInstance().apply { timeInMillis = now.timeInMillis }
            val mondayBasedToday = (cursor.get(Calendar.DAY_OF_WEEK) + MONDAY_OFFSET) % DAYS_IN_WEEK
            cursor.set(Calendar.HOUR_OF_DAY, 0)
            cursor.set(Calendar.MINUTE, 0)
            cursor.set(Calendar.SECOND, 0)
            cursor.set(Calendar.MILLISECOND, 0)
            val today = format(cursor)
            cursor.add(Calendar.DAY_OF_MONTH, -mondayBasedToday)

            val formatter = SimpleDateFormat(DATE_PATTERN, Locale.US)
            val dates = List(DAYS_IN_WEEK) {
                formatter.format(cursor.time).also { cursor.add(Calendar.DAY_OF_MONTH, 1) }
            }
            return ScheduleWeek(monday = dates.first(), today = today, dates = dates)
        }

        /** 日期加减（用 [Calendar] 加天数，规避夏令时偏移）。 */
        fun addDays(date: String, days: Int): String? {
            val parsed = parse(date) ?: return null
            val calendar = Calendar.getInstance().apply {
                timeInMillis = parsed
                add(Calendar.DAY_OF_MONTH, days)
            }
            return format(calendar)
        }

        /** 两个日期相差的天数（同一天为 0）。 */
        fun daysBetween(from: String, to: String): Int? {
            val start = parse(from) ?: return null
            val end = parse(to) ?: return null
            val diffMs = end - start
            return (diffMs / MILLIS_PER_DAY).toInt()
        }

        fun parse(date: String): Long? = runCatching {
            SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getDefault()
            }.parse(date)?.time
        }.getOrNull()

        fun format(calendar: Calendar): String =
            SimpleDateFormat(DATE_PATTERN, Locale.US).format(calendar.time)

        /** 日期 + 时刻 → Unix 毫秒；只有日期时取当天 00:00。 */
        fun toMillis(date: String?, time: String?): Long {
            val parsed = parse(date ?: return 0L) ?: return 0L
            val minutes = parseMinutes(time) ?: return parsed
            return parsed + minutes * MILLIS_PER_MINUTE
        }

        private fun parseMinutes(time: String?): Int? {
            val value = time?.trim().orEmpty()
            if (!TIME_REGEX.matches(value)) return null
            val parts = value.split(":")
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }

        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val MILLIS_PER_MINUTE = 60L * 1000L
        private val TIME_REGEX = Regex("\\d{2}:\\d{2}")
    }
}

/** 单个源一次抓取的条目（尚未判重）。 */
internal data class ScheduleFeedItem(
    val sourceId: String,
    /** 源内条目唯一键：AniRpb 为 `videoId|date|time|episode`，AniCyk 为 `detailId|date`。 */
    val entryId: String,
    val title: String,
    /** 该源已知的别名（会与身份映射表合并后一起参与判重）。 */
    val aliases: List<String> = emptyList(),
    /** 共享外部 id（bangumi / mal / anilist），跨源最可靠的判重依据。 */
    val externalIds: Map<String, String> = emptyMap(),
    val coverUrl: String? = null,
    val synopsis: String? = null,
    val airTime: AirTime,
    val episode: Int = 0,
    val episodeText: String = "",
    val updateText: String = "",
    val channelId: String = "",
    val detailLink: String = "",
)

/** 一个源的一次成功抓取结果（日期已确定）。 */
internal data class ScheduleFeed(
    val sourceId: String,
    val displayName: String,
    val priority: Int,
    val items: List<ScheduleFeedItem>,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * 源抓取的原始产物。
 *
 * 分两种形态是因为「日期从哪来」不同：
 * - [Dated]：条目自带日期（AniRpb 按日请求），可以直接物化；
 * - [Calendar]：报文只有按天分组、不带日期（AniCyk），必须先做锚点校准才能落库。
 *
 * 网络拉取全部并行，锚定与物化放在本地串行完成，互不阻塞。
 */
internal sealed interface SourcePayload {
    val sourceId: String

    data class Dated(
        override val sourceId: String,
        val items: List<ScheduleFeedItem>,
        /** 本次真正抓到的日期，用于决定哪些旧缓存日期需要补位。 */
        val coveredDates: Set<String>,
    ) : SourcePayload

    data class Calendar(
        override val sourceId: String,
        val days: List<CalendarDay>,
    ) : SourcePayload
}

/** 日历源的一天；条目的 [ScheduleFeedItem.airTime] 只有时刻，日期待锚定。 */
internal data class CalendarDay(
    val index: Int,
    val items: List<ScheduleFeedItem>,
)

/**
 * 时间表数据源。
 *
 * 实现者只负责「把自家数据映射成 [ScheduleFeedItem]」，不做判重、不做降级、不猜日期；
 * 失败直接抛异常、空数据返回空 payload，由编排层标记不可用并跳过。
 */
internal interface ScheduleSource {
    val id: String
    val displayName: String

    /** 数字越小越可信；合并时排序后第一个可用源即 base。 */
    val priority: Int

    /** 按 [week] 抓取本周数据（纯网络 + 解析，不做任何跨源推断）。 */
    suspend fun fetch(week: ScheduleWeek): SourcePayload

    companion object {
        /** AniCyk：日历信息密度更高（含具体时刻），作为首选。 */
        const val PRIORITY_ANICYK = 1

        /** AniRpb：官方更新表，日期明确，作为次选与日期校准基准。 */
        const val PRIORITY_ANIRPB = 2
    }
}

