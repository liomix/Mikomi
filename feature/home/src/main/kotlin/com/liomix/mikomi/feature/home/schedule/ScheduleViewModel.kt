package com.liomix.mikomi.feature.home.schedule

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.ScheduleEntry
import com.liomix.mikomi.data.anime.ScheduleRepository
import com.liomix.mikomi.feature.home.RefreshState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 时间表 ViewModel：一周七天 × 多源合并后的本周放送表。
 *
 * 离线优先，与首页、排行榜同样的口径：本地有数据就展示，刷新失败只置失败位。
 * 刷新状态共用 [RefreshState]；顶栏的刷新按钮读 `isRefreshing`，只有一个入口
 * （用户点了才刷新），所以 `userVisible` 恒为 false。
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    /** 进页面就有一趟刷新在途，所以初值即「在途」——首帧直接是骨架屏。 */
    private val refreshState = MutableStateFlow(RefreshState(inFlight = true))

    /** 在途刷新任务：同一时刻只允许一趟，错误态连点重试不叠加请求。 */
    private var refreshJob: Job? = null

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleRepository.observeSchedule(),
        refreshState,
    ) { entries, refresh ->
        val content = when {
            // 本地已有数据时优先展示（离线优先），刷新失败不再覆盖为错误态。
            entries.isNotEmpty() -> ScheduleContent.Success(
                days = groupByWeekday(entries),
                dates = currentWeekDates(),
            )

            refresh.inFlight -> ScheduleContent.Loading
            refresh.failed -> ScheduleContent.Error
            else -> ScheduleContent.Empty
        }
        // 「在途」是跨态字段：出错重试、顶栏刷新、首载都靠它点按钮上的进度环。
        ScheduleUiState(isRefreshing = refresh.inFlight, content = content)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(),
    )

    init {
        // 进页面先看缓存新旧：刚拉过就不发请求（时间表一次刷新要并发打三四个源）。
        refreshIfStale()
    }

    /** 静默刷新：缓存新鲜时什么都不做，也不点亮顶栏按钮。 */
    fun refreshIfStale() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshState.value = RefreshState(inFlight = true)
            try {
                scheduleRepository.refreshScheduleIfStale()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh schedule failed", e)
                refreshState.value = refreshState.value.copy(failed = true)
            } finally {
                refreshState.value = refreshState.value.copy(inFlight = false)
            }
        }
    }

    /** 用户点顶栏刷新：无视缓存新旧。 */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            // 新的一次尝试：清掉上一次的失败结论。
            refreshState.value = RefreshState(inFlight = true)
            try {
                scheduleRepository.refreshSchedule()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh schedule failed", e)
                refreshState.value = refreshState.value.copy(failed = true)
            } finally {
                refreshState.value = refreshState.value.copy(inFlight = false)
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

/**
 * 时间表页面的展示状态。
 *
 * 外层的 [isRefreshing] 是**跨态**的：骨架屏、内容、空态、错误态都可以同时在刷新，
 * 所以它不能塞进内容态里 —— 塞进去之后「错误页刷新中」就无处安放。
 */
data class ScheduleUiState(
    val isRefreshing: Boolean = false,
    val content: ScheduleContent = ScheduleContent.Loading,
)

/** 内容态：同一时刻只会是其中一种。 */
sealed interface ScheduleContent {
    data object Loading : ScheduleContent

    data class Success(
        val days: List<List<ScheduleEntry>>,
        /** 与 [days] 一一对应的本周日期（`yyyy-MM-dd`，索引 0 = 周一）。 */
        val dates: List<String>,
    ) : ScheduleContent

    data object Empty : ScheduleContent

    data object Error : ScheduleContent
}
