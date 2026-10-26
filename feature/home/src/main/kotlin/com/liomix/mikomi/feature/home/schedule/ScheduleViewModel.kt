package com.liomix.mikomi.feature.home.schedule

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.data.anime.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
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
        isRefreshing,
        refreshFailed,
    ) { entries, refreshing, failed ->
        when {
            // 本地已有数据时优先展示（离线优先），刷新失败不再覆盖为错误态。
            entries.isNotEmpty() -> ScheduleUiState.Success(days = groupByWeekday(entries))
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

    /** 按周一 ~ 周日分组（索引 0 = 周一），组内保持放送时间升序。 */
    private fun groupByWeekday(entries: List<ScheduleEntry>): List<List<ScheduleEntry>> {
        val days = List(DAYS_IN_WEEK) { mutableListOf<ScheduleEntry>() }
        val calendar = Calendar.getInstance()
        entries.forEach { entry ->
            calendar.timeInMillis = entry.airingAtMillis
            val index = (calendar.get(Calendar.DAY_OF_WEEK) + MONDAY_OFFSET) % DAYS_IN_WEEK
            days[index].add(entry)
        }
        return days
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
        const val MONDAY_OFFSET = 5
        const val TAG = "ScheduleViewModel"
    }
}
