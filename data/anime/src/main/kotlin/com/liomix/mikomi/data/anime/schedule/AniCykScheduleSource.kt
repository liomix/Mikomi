package com.liomix.mikomi.data.anime.schedule

import android.util.Log
import com.liomix.mikomi.core.model.AirTime
import com.liomix.mikomi.core.model.AirTimePrecision
import com.liomix.mikomi.data.anime.AniCykCalendar
import com.liomix.mikomi.data.anime.AniCykRepository
import com.liomix.mikomi.data.anime.remote.anicyk.CykScheduleDay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 次元库（AniCyk）时间表源，优先级 1。
 *
 * 次元库的 `/mobile/schedule` 有两条线路（`type`），对应**两家不同上游站点的片库**：
 * 超清（`lmm85.com`，7 天，约 6/7 条目带 `⏰HH:mm`）与樱花（`yinhuadm.cc`，11 天，全部没有时刻）。
 *
 * 对时间表而言它们仍是**同一个源**：这里并行抓两条线路，按「超清为主、樱花补漏」合成
 * 一份 7 天日历再交给编排层，所以对外只有一个 `sourceId`、一个优先级，判重与 patch 行为
 * 与只用单线路时完全一致。混合只发生在时间表这条链路上，[AniCykRepository] 本身仍是
 * 「一次一条线路」的原子能力，其它场景不会被动到。
 */
@Singleton
internal class AniCykScheduleSource @Inject constructor(
    private val repository: AniCykRepository,
) : ScheduleSource {

    override val id: String = SOURCE_ID
    override val displayName: String = "次元库"
    override val priority: Int = ScheduleSource.PRIORITY_ANICYK

    override suspend fun fetch(week: ScheduleWeek): SourcePayload = coroutineScope {
        val hq = async { fetchRoute(AniCykCalendar.HQ, entryIdPrefix = "") }
        val sakura = async { fetchRoute(AniCykCalendar.SAKURA, entryIdPrefix = SAKURA_ID_PREFIX) }
        val hqDays = hq.await()
        val sakuraDays = sakura.await()

        // 一条线路挂掉不影响源可用性：另一条能出数据就还算「次元库可用」。
        if (hqDays == null && sakuraDays == null) {
            throw IOException("次元库两条线路均不可用")
        }

        val days = when {
            hqDays == null -> sakuraDays.orEmpty()
            sakuraDays == null -> hqDays
            else -> CykCalendarMixer.mix(primary = hqDays, extra = sakuraDays)
        }
        SourcePayload.Calendar(sourceId = id, days = days)
    }

    /** 单条线路抓取。失败只记日志并返回 null，让另一条线路继续生效。 */
    private suspend fun fetchRoute(
        calendar: AniCykCalendar,
        entryIdPrefix: String,
    ): List<CalendarDay>? = try {
        repository.fetchSchedule(calendar)
            .toCalendarDays(
                sourceId = id,
                keepDays = WEEK_DAYS,
                entryIdPrefix = entryIdPrefix,
            )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "次元库线路不可用：${calendar.apiType}", e)
        null
    }

    companion object {
        const val SOURCE_ID = "anicyk"

        private const val TAG = "AniCykScheduleSource"

        /** 时间表只展示本周：樱花线路报文有 11 天，第 8 天往后是下周数据。 */
        private const val WEEK_DAYS = 7

        /**
         * 详情 id 只保证站内唯一，两条线路的数字可能撞车（`/detail/316` vs `/v/316`），
         * 合成同一个源之后主键会冲突，因此樱花条目的源内键加前缀区分。
         */
        private const val SAKURA_ID_PREFIX = "sakura:"
    }
}

/**
 * 次元库日历报文 → [CalendarDay] 列表。
 *
 * 两条线路字段完全一致（`title/cover/chapter/link`），只有天数与有无时刻不同，映射共用一份。
 *
 * @param keepDays 只保留前 N 天。日历索引是锚定算法的输入，**不能先过滤空天**，
 *   否则后面的天整体前移，日期会错位。
 * @param entryIdPrefix 源内键前缀，用于区分同源下不同线路的详情 id。
 */
internal fun List<CykScheduleDay>.toCalendarDays(
    sourceId: String,
    keepDays: Int,
    entryIdPrefix: String = "",
): List<CalendarDay> = asSequence()
    .filter { it.index < keepDays }
    .map { day ->
        CalendarDay(
            index = day.index,
            items = day.items.map { item ->
                ScheduleFeedItem(
                    sourceId = sourceId,
                    // 日期未定，先用站点详情 id 作为源内键，物化时再拼上日期。
                    entryId = entryIdPrefix + item.detailId,
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
    .toList()
