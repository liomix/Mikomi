package com.liomix.mikomi.data.anime.schedule

import com.liomix.mikomi.core.model.AirTime
import com.liomix.mikomi.core.model.AirTimePrecision
import com.liomix.mikomi.data.anime.AniCykRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 次元库（AniCyk）时间表源，优先级 1。
 *
 * 报文按「天」分组但不带日期，因此返回 [SourcePayload.Calendar]，由编排层用 AniRpb 的
 * 明确日期做锚点校准后再物化。章节文案里能解析出时刻的条目视为 EXACT，其余为 DATE_ONLY。
 */
@Singleton
internal class AniCykScheduleSource @Inject constructor(
    private val repository: AniCykRepository,
) : ScheduleSource {

    override val id: String = SOURCE_ID
    override val displayName: String = "次元库"
    override val priority: Int = ScheduleSource.PRIORITY_ANICYK

    override suspend fun fetch(week: ScheduleWeek): SourcePayload {
        val days = repository.fetchSchedule()
        // 保留空天：天的「索引」是锚定算法的输入，过滤掉空天会让位置与索引错位。
        val calendarDays = days.map { day ->
            CalendarDay(
                index = day.index,
                items = day.items.map { item ->
                    ScheduleFeedItem(
                        sourceId = id,
                        // 日期未定，先用详情 id 作为源内键，物化时再拼上日期。
                        entryId = item.detailId,
                        title = item.title,
                        coverUrl = item.coverUrl,
                        synopsis = null,
                        airTime = AirTime(
                            date = null,
                            time = item.airTime,
                            precision = if (item.airTime != null) {
                                AirTimePrecision.EXACT
                            } else {
                                AirTimePrecision.UNKNOWN
                            },
                        ),
                        episode = item.episode,
                        episodeText = item.chapterText,
                        detailLink = item.detailLink,
                    )
                },
            )
        }
        return SourcePayload.Calendar(sourceId = id, days = calendarDays)
    }

    companion object {
        const val SOURCE_ID = "anicyk"
    }
}
