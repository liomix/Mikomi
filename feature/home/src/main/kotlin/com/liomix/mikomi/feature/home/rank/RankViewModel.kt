package com.liomix.mikomi.feature.home.rank

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.RankEntry
import com.liomix.mikomi.core.model.RankRegion
import com.liomix.mikomi.data.anime.RankRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * 排行榜 ViewModel：日漫 / 国漫两份 AniRpb 整榜（各 100 条）。
 *
 * 离线优先：本地已有数据时优先展示，刷新失败不覆盖为错误态。
 */
@HiltViewModel
class RankViewModel @Inject constructor(
    private val rankRepository: RankRepository,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    private val refreshFailed = MutableStateFlow(false)

    /**
     * 用户在顶栏点了刷新（且该次刷新仍在进行）。
     *
     * 与 [isRefreshing] 分开：进入页面时的自动加载只走页面骨架屏，
     * 顶栏按钮保持静止；只有真正点了按钮，按钮才换成进度环。
     */
    private val manualRefreshing = MutableStateFlow(false)

    /** 在途刷新任务：同一时刻只允许一个刷新，避免连点叠加请求。 */
    private var refreshJob: Job? = null

    /**
     * 排行榜唯一状态：内容 + 顶栏刷新指示。
     *
     * 刷新指示曾经是单独一条 `StateFlow<Boolean>`。它不能塞进 `Success` 分支 ——
     * 空数据时页面停在骨架屏，用户这时点刷新，按钮也该转；塞进 `Success` 就丢了。
     * 所以外层是 data class（放跨态字段），内层 sealed 只表达内容态。
     */
    val uiState: StateFlow<RankUiState> = combine(
        rankRepository.observeRank(RankRegion.CHINESE),
        rankRepository.observeRank(RankRegion.JAPANESE),
        isRefreshing,
        refreshFailed,
        manualRefreshing,
    ) { chinese, japanese, refreshing, failed, manual ->
        RankUiState(
            refreshing = manual,
            content = when {
                // 本地已有数据时优先展示（离线优先），刷新失败不再覆盖为错误态。
                chinese.isNotEmpty() || japanese.isNotEmpty() -> RankContent.Success(
                    chinese = chinese,
                    japanese = japanese,
                )

                // 空数据没有独立的「空态」：一直停在骨架屏，看起来就是还在加载。
                // 只有「刷新失败且已不在重试」才切错误态，让重试入口有地方可放。
                failed && !refreshing -> RankContent.Error
                else -> RankContent.Loading
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RankUiState(),
    )

    init {
        refresh()
    }

    /** 静默刷新（进入页面时自动执行）：不点亮顶栏按钮。 */
    fun refresh() {
        startRefresh(manual = false)
    }

    /** 用户点击顶栏刷新：按钮在请求期间换成进度环。 */
    fun refreshByUser() {
        startRefresh(manual = true)
    }

    private fun startRefresh(manual: Boolean) {
        // 已有刷新在途：只把按钮点亮，复用同一趟请求，不再叠加网络调用。
        if (refreshJob?.isActive == true) {
            if (manual) manualRefreshing.value = true
            return
        }
        refreshJob = viewModelScope.launch {
            isRefreshing.value = true
            if (manual) manualRefreshing.value = true
            try {
                rankRepository.refreshRank()
                refreshFailed.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh rank failed", e)
                refreshFailed.value = true
            } finally {
                isRefreshing.value = false
                // 无论这趟是不是用户点的，结束时按钮都要恢复静止。
                manualRefreshing.value = false
            }
        }
    }

    private companion object {
        const val TAG = "RankViewModel"
    }
}

/**
 * 排行榜的**唯一**状态。
 *
 * 外层 data class 放跨态字段（[refreshing]：骨架屏上点刷新时按钮也要转），
 * 内层 sealed 只表达内容态。与首页、详情页同一个形状 —— 一个页面一份状态，
 * Route 只订阅一次，Screen 只收一个参数。
 */
data class RankUiState(
    /** 用户在顶栏点了刷新且该次刷新仍在进行。 */
    val refreshing: Boolean = false,
    val content: RankContent = RankContent.Loading,
)

/**
 * 内容态：骨架 / 数据 / 错误。
 *
 * 没有「空态」：一个数据都没有时保持 [RankContent.Loading]，界面就是继续加载的骨架屏。
 * 空数据本身不代表可展示的信息，把页面切成一块空占位只会让人以为坏了。
 */
sealed interface RankContent {
    data object Loading : RankContent

    data class Success(
        val chinese: List<RankEntry>,
        val japanese: List<RankEntry>,
    ) : RankContent

    data object Error : RankContent
}
