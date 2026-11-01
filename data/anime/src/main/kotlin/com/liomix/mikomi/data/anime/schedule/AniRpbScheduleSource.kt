package com.liomix.mikomi.data.anime.schedule

import android.util.Log
import com.liomix.mikomi.core.model.AirTime
import com.liomix.mikomi.core.model.AniRpbScheduleItem
import com.liomix.mikomi.data.anime.AniRpbRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * AniRpb 更新表源，优先级 2。
 *
 * 更新表按单日返回，因此一次抓取要依次请求周一至周日。部分日期失败时用成功的日期继续
 * （未覆盖的日期会在编排层用该源缓存补位）；整周全部失败才判定该源不可用。
 */
@Singleton
internal class AniRpbScheduleSource @Inject constructor(
    private val repository: AniRpbRepository,
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
                val dayItems = repository.fetchCalendar(date)
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

    private fun AniRpbScheduleItem.toFeedItem(playDate: String): ScheduleFeedItem = ScheduleFeedItem(
        sourceId = id,
        entryId = "$videoId|$playDate|$updateTime|$episode",
        title = title,
        coverUrl = coverUrl,
        synopsis = synopsis,
        airTime = AirTime.of(date = playDate, time = updateTime.takeIf { it.isNotBlank() }),
        episode = episode,
        episodeText = episodeText,
        updateText = updateText,
        channelId = channelId,
    )

    companion object {
        const val SOURCE_ID = "anirpb"
        private const val TAG = "AniRpbScheduleSource"
    }
}
