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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val isRefreshing = MutableStateFlow(true)
    private val refreshFailed = MutableStateFlow(false)

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

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
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
