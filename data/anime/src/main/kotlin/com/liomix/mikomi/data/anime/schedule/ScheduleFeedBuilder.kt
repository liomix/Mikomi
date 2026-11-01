package com.liomix.mikomi.data.anime.schedule

import com.liomix.mikomi.core.model.AirTime

/**
 * 把源抓取的原始产物物化成带日期的 [ScheduleFeed]。
 *
 * - 日期源（AniRpb）：用本次抓到的日期覆盖，未抓到的日期用该源上次成功的缓存补位；
 * - 日历源（AniCyk）：按锚点把「第 N 天」换算成本周真实日期，越出本周的条目丢弃
 *   （时间表就是一周视图，不往下周溢出）。
 */
internal object ScheduleFeedBuilder {

    data class Built(val feed: ScheduleFeed, val droppedOutOfWeek: Int = 0)

    fun dated(
        payload: SourcePayload.Dated,
        source: ScheduleSource,
        cachedItems: List<ScheduleFeedItem>,
        week: ScheduleWeek,
    ): Built {
        val fresh = payload.items
        val freshDates = payload.coveredDates
        // 本次没抓到的日期，用缓存里**本周**的数据补位，避免一天失败就整天消失；
        // 跨周的旧缓存一律丢弃（缓存按源整批覆盖，但周与周之间可能残留）。
        val fillers = cachedItems.filter { item ->
            val date = item.airTime.date ?: return@filter false
            date in week.dates && date !in freshDates
        }
        val merged = (fresh + fillers)
            .distinctBy { it.entryId }
            .sortedWith(FEED_ORDER)
        return Built(
            feed = ScheduleFeed(
                sourceId = source.id,
                displayName = source.displayName,
                priority = source.priority,
                items = merged,
            ),
        )
    }

    fun calendar(
        payload: SourcePayload.Calendar,
        source: ScheduleSource,
        week: ScheduleWeek,
        startIndex: Int,
    ): Built {
        val items = mutableListOf<ScheduleFeedItem>()
        var dropped = 0
        payload.days.forEach { day ->
            val date = week.dateAt(startIndex + day.index)
            if (date == null) {
                dropped += day.items.size
                return@forEach
            }
            day.items.forEach { item ->
                items += item.copy(
                    entryId = "${item.entryId}|$date",
                    airTime = AirTime.of(date = date, time = item.airTime.time),
                )
            }
        }
        return Built(
            feed = ScheduleFeed(
                sourceId = source.id,
                displayName = source.displayName,
                priority = source.priority,
                items = items.distinctBy { it.entryId }.sortedWith(FEED_ORDER),
            ),
            droppedOutOfWeek = dropped,
        )
    }

    /** 源内排序固定，保证同一批数据的输出顺序稳定（合并与落库都幂等）。 */
    private val FEED_ORDER = compareBy<ScheduleFeedItem>(
        { it.airTime.date ?: "9999-12-31" },
        { it.airTime.time ?: "99:99" },
        { it.entryId },
    )
}
