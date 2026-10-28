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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 排行榜 ViewModel：国漫 / 日漫两份综合评分榜。
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
    val refreshing: StateFlow<Boolean> = manualRefreshing.asStateFlow()

    /** 在途刷新任务：同一时刻只允许一个刷新，避免连点叠加请求。 */
    private var refreshJob: Job? = null

    val uiState: StateFlow<RankUiState> = combine(
        rankRepository.observeRank(RankRegion.CHINESE),
        rankRepository.observeRank(RankRegion.JAPANESE),
        isRefreshing,
        refreshFailed,
    ) { chinese, japanese, refreshing, failed ->
        when {
            // 本地已有数据时优先展示（离线优先），刷新失败不再覆盖为错误态。
            chinese.isNotEmpty() || japanese.isNotEmpty() -> RankUiState.Success(
                chinese = chinese,
                japanese = japanese,
            )

            refreshing -> RankUiState.Loading
            failed -> RankUiState.Error
            else -> RankUiState.Empty
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RankUiState.Loading,
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

/** 加载状态：骨架 / 数据 / 空 / 错误。 */
sealed interface RankUiState {
    data object Loading : RankUiState

    data class Success(
        val chinese: List<RankEntry>,
        val japanese: List<RankEntry>,
    ) : RankUiState

    data object Empty : RankUiState

    data object Error : RankUiState
}
