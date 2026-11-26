package com.liomix.mikomi.data.anime.remote.anirpb

import android.util.Log
import com.liomix.mikomi.core.model.anime.AirTime
import com.liomix.mikomi.core.model.AniRpbScheduleItem
import com.liomix.mikomi.data.anime.remote.anirpb.AniRpbSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import com.liomix.mikomi.data.anime.processor.schedule.ScheduleContentFilter
import com.liomix.mikomi.data.anime.processor.schedule.ScheduleFeedItem
import com.liomix.mikomi.data.anime.processor.schedule.ScheduleSource
import com.liomix.mikomi.data.anime.processor.schedule.ScheduleWeek
import com.liomix.mikomi.data.anime.processor.schedule.SourcePayload

/**
 * AniRpb 更新表源，优先级 2。
 *
 * 更新表按单日返回，因此一次抓取要依次请求周一至周日。部分日期失败时用成功的日期继续
 * （未覆盖的日期会在编排层用该源缓存补位）；整周全部失败才判定该源不可用。
 */
@Singleton
internal class AniRpbScheduleSource @Inject constructor(
    private val source: AniRpbSource,
) : ScheduleSource {

    override val id: String = SOURCE_ID
    override val displayName: String = "AniRpb"
    override val priority: Int = ScheduleSource.PRIORITY_ANIRPB

    override suspend fun fetch(week: ScheduleWeek): SourcePayload {
        val items = mutableListOf<ScheduleFeedItem>()
        val coveredDates = mutableSetOf<String>()
        var failedDays = 0
        var lastFailure: Exception? = null

        week.dates.forEach { date ->
            try {
                val dayItems = fetchCalendar(date)
                items += dayItems.map { it.toFeedItem(date) }
                coveredDates += date
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "AniRpb 更新表抓取失败，date=$date", e)
                failedDays++
                lastFailure = e
            }
        }

        if (coveredDates.isEmpty() && lastFailure != null) {
            throw IOException("AniRpb 整周更新表抓取失败（$failedDays/7 天）", lastFailure)
        }

        return SourcePayload.Dated(
            sourceId = id,
            items = items.distinctBy { it.entryId },
            coveredDates = coveredDates,
        )
    }

    /**
     * AniRpb 条目 → 时间表条目。
     *
     * 时刻有两处来源：结构化字段 `updateTime`，以及更新文案里的 `4#周三21:00更`。
     * 后者原先被整段当成展示文案，导致「21:00」这种具体时刻只出现在文案里、进不了
     * [AirTime]，base 缺时刻时也白丢一条可补的数据。现在统一走 [ScheduleContentFilter]：
     * 时刻取 `updateTime`，缺失时后退到文案里的时刻；展示文案只留时刻，冗余的
     * 集数/星期/后缀丢掉（完结文案原样保留，交给物化阶段整条过滤）。
     */
    private fun AniRpbScheduleItem.toFeedItem(playDate: String): ScheduleFeedItem = ScheduleFeedItem(
        sourceId = id,
        entryId = "$videoId|$playDate|$updateTime|$episode",
        title = title,
        coverUrl = coverUrl,
        synopsis = synopsis,
        airTime = AirTime.of(
            date = playDate,
            time = ScheduleContentFilter.parseClock(updateTime)
                ?: ScheduleContentFilter.parseClock(updateText),
        ),
        episode = episode,
        episodeText = episodeText,
        updateText = ScheduleContentFilter.filterUpdateText(updateText),
        channelId = channelId,
    )

    companion object {
        const val SOURCE_ID = "anirpb"
        private const val TAG = "AniRpbScheduleSource"

        private const val FIRST_PAGE = 1
        private const val CALENDAR_PAGE_SIZE = 20
        private const val MAX_CALENDAR_PAGES = 4
    }

    /**
     * 某天的更新表（翻页直到没有下一页）。
     *
     * 放在源实现里而不是数据源：`AniRpbSource` 只回答「第 N 页是什么」，
     * 「翻到什么时候停」是取数策略。
     */
    private suspend fun fetchCalendar(date: String): List<AniRpbScheduleItem> {
        val items = mutableListOf<AniRpbScheduleItem>()
        var page = FIRST_PAGE
        var totalPages = 0

        do {
            val result = source.calendarPage(playDate = date, page = page, pageSize = CALENDAR_PAGE_SIZE)
            items += result.items
            totalPages = result.totalPages
            page++
        } while (page <= totalPages && page <= MAX_CALENDAR_PAGES)

        return items.distinctBy { item ->
            listOf(item.videoId, item.playDate, item.updateTime, item.episode).joinToString("|")
        }
    }
}
