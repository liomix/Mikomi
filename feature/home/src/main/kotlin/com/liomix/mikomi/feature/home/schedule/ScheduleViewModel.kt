package com.liomix.mikomi.feature.home.schedule

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.core.model.ScheduleProvenance
import com.liomix.mikomi.data.anime.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(true)
    private val refreshFailed = MutableStateFlow(false)

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleRepository.observeSchedule(),
        scheduleRepository.observeProvenance(),
        isRefreshing,
        refreshFailed,
    ) { entries, provenance, refreshing, failed ->
        when {
            // 本地已有数据时优先展示（离线优先），刷新失败不再覆盖为错误态。
            entries.isNotEmpty() -> ScheduleUiState.Success(
                days = groupByWeekday(entries),
                dates = currentWeekDates(),
                provenance = provenance,
            )

            refreshing -> ScheduleUiState.Loading
            failed -> ScheduleUiState.Error
            else -> ScheduleUiState.Empty
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState.Loading,
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                scheduleRepository.refreshSchedule()
                refreshFailed.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh schedule failed", e)
                refreshFailed.value = true
            } finally {
                isRefreshing.value = false
            }
        }
    }

    /**
     * 按周一 ~ 周日分组（索引 0 = 周一），组内按「时刻 → 标题」稳定排序。
     *
     * 分组以归一化后的放送日期为准：跨源合并后 `airingAtMillis` 只是排序辅助，
     * 日期才是权威（仅日期精度时毫秒值为当天 00:00，不能用来反推星期）。
     */
    private fun groupByWeekday(entries: List<ScheduleEntry>): List<List<ScheduleEntry>> {
        val days = List(DAYS_IN_WEEK) { mutableListOf<ScheduleEntry>() }
        entries.forEach { entry ->
            val date = entry.airTime.date ?: return@forEach
            val index = weekdayIndex(date) ?: return@forEach
            days[index].add(entry)
        }
        days.forEach { day ->
            day.sortWith(
                compareBy(
                    { it.airTime.time ?: UNKNOWN_TIME_ORDER },
                    { it.anime.title },
                ),
            )
        }
        return days
    }

    private fun weekdayIndex(date: String): Int? {
        val parsed = runCatching { DATE_FORMATTER.parse(date) }.getOrNull() ?: return null
        val calendar = Calendar.getInstance().apply { timeInMillis = parsed.time }
        return (calendar.get(Calendar.DAY_OF_WEEK) + MONDAY_OFFSET) % DAYS_IN_WEEK
    }

    /**
     * 本周周一到周日的日期（`yyyy-MM-dd`），供日历标签栏显示「月-日」。
     *
     * 与 `ScheduleWeek.current()` 的口径一致（本地时区、周一为首日），
     * 仓库只保留本周数据，因此这里不需要再按条目反推。
     */
    private fun currentWeekDates(): List<String> {
        val calendar = Calendar.getInstance()
        val todayIndex = (calendar.get(Calendar.DAY_OF_WEEK) + MONDAY_OFFSET) % DAYS_IN_WEEK
        calendar.add(Calendar.DAY_OF_MONTH, -todayIndex)
        return List(DAYS_IN_WEEK) {
            DATE_FORMATTER.format(calendar.time).also { calendar.add(Calendar.DAY_OF_MONTH, 1) }
        }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
        const val MONDAY_OFFSET = 5
        const val UNKNOWN_TIME_ORDER = "99:99"
        const val TAG = "ScheduleViewModel"

        val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}

/** 时间表页面的展示状态。 */
sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState

    data class Success(
        val days: List<List<ScheduleEntry>>,
        /** 与 [days] 一一对应的本周日期（`yyyy-MM-dd`，索引 0 = 周一）。 */
        val dates: List<String>,
        val provenance: ScheduleProvenance,
    ) : ScheduleUiState

    data object Empty : ScheduleUiState

    data object Error : ScheduleUiState
}
