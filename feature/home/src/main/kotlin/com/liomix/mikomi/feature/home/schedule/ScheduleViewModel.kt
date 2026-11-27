package com.liomix.mikomi.feature.home.schedule

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.anime.ScheduleDay
import com.liomix.mikomi.core.model.anime.ScheduleEntry
import com.liomix.mikomi.data.anime.repository.ScheduleRepository
import com.liomix.mikomi.feature.home.components.RefreshState
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
 *
 * ## 首载不亮刷新图标
 *
 * `isRefreshing` 取的是 [RefreshState.showsIndicator]（在途 **且** 用户发起），不是 `inFlight`。
 * 进页面那趟是静默的：它由骨架屏表达，顶栏图标一直不动 —— 图标转起来的意思是
 * 「我点了它、正在响应我」，自动刷新不该占用这个信号。
 *
 * ## 「本周」只在这里算一次
 *
 * 日期、星期、今天这三个量必须来自**同一刻**的时钟，所以它们在同一处产生
 * （[buildWeek]），界面只拿到结果。界面再读一次 `Calendar` 的后果不是崩溃，
 * 而是跨零点时标签栏出现「『今』在这一格、日期却是昨天的周」—— 一年只在
 * 23:59:59 之后那一秒复现。
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    /**
     * 进页面就有一趟刷新在途，所以初值即「在途」—— 首帧直接是骨架屏。
     *
     * `userVisible` 保持 false：这趟是自动的，顶栏图标不动。
     */
    private val refreshState = MutableStateFlow(RefreshState(inFlight = true))

    /** 在途刷新任务：同一时刻只允许一趟，错误态连点重试不叠加请求。 */
    private var refreshJob: Job? = null

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleRepository.observeSchedule(),
        refreshState,
    ) { entries, refresh ->
        val content = when {
            // 本地已有数据时优先展示（离线优先），刷新失败不再覆盖为错误态。
            entries.isNotEmpty() -> ScheduleContent.Success(week = buildWeek(entries))

            refresh.inFlight -> ScheduleContent.Loading
            refresh.failed -> ScheduleContent.Error
            else -> ScheduleContent.Empty
        }
        // 「在途」是跨态字段：出错重试、顶栏刷新、首载都靠它决定 `content`；
        // 而「要不要转图标」是另一个事实，见 [ScheduleUiState.isRefreshing]。
        ScheduleUiState(isRefreshing = refresh.showsIndicator, content = content)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleUiState(),
    )

    init {
        // 进页面先看缓存新旧：刚拉过就不发请求（时间表一次刷新要并发打三四个源）。
        refreshSchedule(userInitiated = false)
    }

    /** 用户点顶栏刷新：无视缓存新旧。 */
    fun refresh() = refreshSchedule(userInitiated = true)

    /**
     * 刷新时间表。
     *
     * [userInitiated] 同时回答两件事，因为它们本来就是同一个入口决定的：
     * 要不要问 TTL（用户点了就无视缓存）、要不要点亮顶栏图标。
     * 拆成两个参数只会让「用户点了但图标不亮」这种组合存在。
     *
     * 与首页 `startRefresh(userInitiated)` 同一口径 —— 抄成两段协程的代价，
     * 是以后改其中一处就会忘掉另一处。
     */
    private fun refreshSchedule(userInitiated: Boolean) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            // 新的一次尝试：清掉上一次的失败结论。
            refreshState.value = RefreshState(inFlight = true, userVisible = userInitiated)
            try {
                if (userInitiated) {
                    scheduleRepository.refreshSchedule()
                } else {
                    scheduleRepository.refreshScheduleIfStale()
                }
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
     * 把条目铺到「本周七天」上，索引 0 = 周一。
     *
     * ## 按日期分桶，不按星期
     *
     * 旧实现先解析条目日期取出 weekday，再往 `List(7)` 里塞 —— 上周或下周的同 weekday
     * 条目会被放进本周。数据层虽然说只留本周，但「只留本周」是数据层的保证，
     * 界面不该靠它才不会串行。日期字符串相等才是「同一天」。
     *
     * ## 时钟只读一次
     *
     * 一个 `Calendar` 实例生成七天日期，同时用它判断 [ScheduleDay.isToday]。
     */
    private fun buildWeek(entries: List<ScheduleEntry>): List<ScheduleDay> {
        val calendar = Calendar.getInstance()
        val todayDate = DATE_FORMATTER.format(calendar.time)
        // 回到本周周一：DAY_OF_WEEK 周日 = 1，减 1 再模 7 得到「距周一几天」。
        calendar.add(Calendar.DAY_OF_MONTH, -mondayDistance(calendar.get(Calendar.DAY_OF_WEEK)))

        val entriesByDate = entries
            .mapNotNull { entry -> entry.airTime.date?.let { date -> date to entry } }
            .groupBy({ it.first }, { it.second })

        return List(DAYS_IN_WEEK) {
            val date = DATE_FORMATTER.format(calendar.time)
            val day = ScheduleDay(
                date = date,
                entries = entriesByDate[date].orEmpty().sortedWith(ENTRY_ORDER),
                isToday = date == todayDate,
            )
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            day
        }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7

        /** `Calendar.DAY_OF_WEEK` 的周日 = 1，换算成「距本周周一几天」。 */
        const val MONDAY_DISTANCE_OFFSET = 5

        /** 没有时刻的条目排在当天末尾；排序只影响读起来的顺序，不影响正确性。 */
        const val UNKNOWN_TIME_ORDER = "99:99"

        const val TAG = "ScheduleViewModel"

        val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        /** 组内按「时刻 → 标题」稳定排序。 */
        val ENTRY_ORDER = compareBy<ScheduleEntry>(
            { it.airTime.time ?: UNKNOWN_TIME_ORDER },
            { it.anime.title },
        )

        fun mondayDistance(dayOfWeek: Int): Int =
            (dayOfWeek + MONDAY_DISTANCE_OFFSET) % DAYS_IN_WEEK
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
        /** 本周七天，索引 0 = 周一；每一项自带日期、当天条目与「是不是今天」。 */
        val week: List<ScheduleDay>,
    ) : ScheduleContent

    data object Empty : ScheduleContent

    data object Error : ScheduleContent
}
