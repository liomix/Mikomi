package com.liomix.mikomi.data.anime.internal

import android.util.Log
import androidx.room.withTransaction
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.MikomiDatabase
import com.liomix.mikomi.core.database.dao.ScheduleDao
import com.liomix.mikomi.core.database.entity.ScheduleEntity
import com.liomix.mikomi.core.model.AniRpbScheduleItem
import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.data.anime.AniRpbRepository
import com.liomix.mikomi.data.anime.ScheduleRepository
import com.liomix.mikomi.data.anime.mapper.toScheduleEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 时间表：AniRpb `calendar.videos` 为主数据源。
 *
 * AniRpb 按单日返回更新表，因此一次刷新会依次请求本周周一至周日，再把结果写入
 * Room；离线时继续展示本地缓存。单日请求失败时保留该日旧数据，只有整周全部
 * 失败才向 UI 报错。
 */
internal class ScheduleRepositoryImpl @Inject constructor(
    private val aniRpbRepository: AniRpbRepository,
    private val scheduleDao: ScheduleDao,
    private val database: MikomiDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ScheduleRepository {

    override fun observeSchedule(): Flow<List<ScheduleEntry>> =
        scheduleDao.observeSchedule().map { list -> list.map { it.toScheduleEntry() } }

    override suspend fun refreshSchedule(): Unit = withContext(ioDispatcher) {
        val dates = currentWeekDates()
        val windowStartMs = currentWeekStartMs()
        val entities = mutableListOf<ScheduleEntity>()
        val successfulDates = mutableListOf<String>()
        var successfulDays = 0
        var lastFailure: Exception? = null

        for (date in dates) {
            try {
                val items = aniRpbRepository.fetchCalendar(date)
                entities += items.map { it.toScheduleEntity() }
                successfulDates += date
                successfulDays++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fetch AniRpb calendar failed, date=$date", e)
                lastFailure = e
            }
        }

        // 整周都失败才报错；部分日期失败时继续保留已有缓存并展示成功日期。
        if (successfulDays == 0 && lastFailure != null) throw lastFailure

        // 在同一事务里替换成功抓取日期的数据：避免旧 AniList 行与新 AniRpb 行并存，
        // 同时保留失败日期此前缓存的数据。
        database.withTransaction {
            scheduleDao.deleteNotFromSource(ANIRPB_ID_PREFIX)
            successfulDates.forEach { date ->
                val dayStartMs = parseAiringAt(date, updateTime = "")
                val dayEndMs = nextDayStartMs(dayStartMs)
                scheduleDao.deleteBySourceAndWindow(ANIRPB_ID_PREFIX, dayStartMs, dayEndMs)
            }
            scheduleDao.upsertAll(entities)
            scheduleDao.deleteOutsideWindow(windowStartMs, weekEndMs(windowStartMs))
        }
    }

    private fun AniRpbScheduleItem.toScheduleEntity(): ScheduleEntity = ScheduleEntity(
        id = "$ANIRPB_ID_PREFIX$videoId:$playDate:$updateTime:$episode",
        animeId = "$ANIRPB_ID_PREFIX$videoId",
        title = title,
        coverUrl = coverUrl,
        synopsis = synopsis,
        rating = null,
        airingAtMillis = parseAiringAt(playDate, updateTime),
        episode = episode,
        episodeText = episodeText,
        updateText = updateText,
        channelId = channelId,
    )

    /** 本周周一 ~ 周日（本地时区，yyyy-MM-dd）。 */
    private fun currentWeekDates(): List<String> {
        val calendar = Calendar.getInstance()
        val mondayBasedToday = (calendar.get(Calendar.DAY_OF_WEEK) + MONDAY_OFFSET) % DAYS_IN_WEEK
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_MONTH, -mondayBasedToday)

        val formatter = SimpleDateFormat(DATE_PATTERN, Locale.US)
        return List(DAYS_IN_WEEK) {
            formatter.format(calendar.time).also {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    /** 本地时区本周窗口起点：周一 00:00:00.000。 */
    private fun currentWeekStartMs(): Long {
        val calendar = Calendar.getInstance()
        val mondayBasedToday = (calendar.get(Calendar.DAY_OF_WEEK) + MONDAY_OFFSET) % DAYS_IN_WEEK
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.DAY_OF_MONTH, -mondayBasedToday)
        return calendar.timeInMillis
    }

    /** 窗口终点：下周一 00:00:00.000（用 Calendar 加天数，规避夏令时偏移）。 */
    private fun weekEndMs(weekStartMs: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = weekStartMs
        calendar.add(Calendar.DAY_OF_MONTH, DAYS_IN_WEEK)
        return calendar.timeInMillis
    }

    private fun nextDayStartMs(dayStartMs: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dayStartMs
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis
    }

    private fun parseAiringAt(playDate: String, updateTime: String): Long {
        val hasTime = TIME_REGEX.matches(updateTime)
        val pattern = if (hasTime) "$DATE_PATTERN HH:mm" else DATE_PATTERN
        val value = if (hasTime) "$playDate $updateTime" else playDate
        return runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getDefault()
            }.parse(value)?.time
        }.getOrNull() ?: 0L
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
        const val MONDAY_OFFSET = 5
        const val DATE_PATTERN = "yyyy-MM-dd"
        const val ANIRPB_ID_PREFIX = "anirpb:"
        const val TAG = "ScheduleRepository"

        val TIME_REGEX = Regex("\\d{2}:\\d{2}")
    }
}
