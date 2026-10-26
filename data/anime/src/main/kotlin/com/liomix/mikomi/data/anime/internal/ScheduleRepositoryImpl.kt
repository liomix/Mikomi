package com.liomix.mikomi.data.anime.internal

import android.util.Log
import com.liomix.mikomi.core.common.dispatcher.IoDispatcher
import com.liomix.mikomi.core.database.dao.ScheduleDao
import com.liomix.mikomi.core.database.entity.ScheduleEntity
import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.data.anime.ScheduleRepository
import com.liomix.mikomi.data.anime.mapper.toScheduleEntity
import com.liomix.mikomi.data.anime.mapper.toScheduleEntry
import com.liomix.mikomi.data.anime.remote.anilist.AniListApi
import com.liomix.mikomi.data.anime.remote.anilist.AniListQueries
import com.liomix.mikomi.data.anime.remote.anilist.dto.AniListGraphqlRequest
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class ScheduleRepositoryImpl @Inject constructor(
    private val anilistApi: AniListApi,
    private val scheduleDao: ScheduleDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ScheduleRepository {

    override fun observeSchedule(): Flow<List<ScheduleEntry>> =
        scheduleDao.observeSchedule().map { list -> list.map { it.toScheduleEntry() } }

    override suspend fun refreshSchedule(): Unit = withContext(ioDispatcher) {
        val windowStartMs = currentWeekStartMs()
        val windowEndMs = weekEndMs(windowStartMs)
        val entities = fetchWeekEntities(windowStartMs, windowEndMs)
        // 先写入新数据再清理窗口外旧数据，刷新过程不出现空档。
        scheduleDao.upsertAll(entities)
        scheduleDao.deleteOutsideWindow(windowStartMs, windowEndMs)
    }

    /** 分页拉取整周放送计划，直到没有下一页或达到页数上限。 */
    private suspend fun fetchWeekEntities(windowStartMs: Long, windowEndMs: Long): List<ScheduleEntity> {
        val entities = mutableListOf<ScheduleEntity>()
        var page = FIRST_PAGE
        while (page <= MAX_PAGES) {
            val response = anilistApi.execute(
                AniListGraphqlRequest(
                    query = AniListQueries.AIRING_SCHEDULE,
                    variables = buildJsonObject {
                        put("weekStart", windowStartMs / MILLIS_PER_SECOND)
                        put("weekEnd", windowEndMs / MILLIS_PER_SECOND)
                        put("page", page)
                        put("perPage", PAGE_SIZE)
                    },
                ),
            )
            val pageData = response.data?.page
            if (pageData == null) {
                Log.w(TAG, "AniList airing schedule response missing Page data")
                break
            }
            entities += pageData.airingSchedules.mapNotNull { it.toScheduleEntity() }
            if (pageData.pageInfo?.hasNextPage != true) break
            page++
        }
        return entities
    }

    /** 本地时区本周窗口起点：周一 00:00:00.000。Calendar 的 DAY_OF_WEEK 周日=1。 */
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

    private companion object {
        const val FIRST_PAGE = 1
        const val MAX_PAGES = 4
        const val PAGE_SIZE = 50
        const val DAYS_IN_WEEK = 7
        const val MONDAY_OFFSET = 5
        const val MILLIS_PER_SECOND = 1_000L
        const val TAG = "ScheduleRepository"
    }
}
