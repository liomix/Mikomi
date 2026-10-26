package com.liomix.mikomi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
) : ViewModel() {

    private val _paging = MutableStateFlow(PagingState())
    private val isRefreshInFlight = MutableStateFlow(false)
    private val _isPullRefreshing = MutableStateFlow(false)
    private var currentOffset = 0

    /** 下拉刷新指示器：仅用户主动下拉时为真，自动首载不显示。 */
    val isRefreshing: StateFlow<Boolean> = _isPullRefreshing

    val uiState: StateFlow<HomeUiState> = combine<List<Anime>, List<Anime>, PagingState, HomeUiState>(
        homeRepository.observeBanners(),
        homeRepository.observeRecommended(),
        _paging,
    ) { banners, recommended, paging ->
        HomeUiState.Success(
            banners = banners,
            anime = recommended,
            isLoadingMore = paging.isLoadingMore,
            hasMore = paging.hasMore,
        )
    }
        .catch { emit(HomeUiState.Error(it.message.orEmpty())) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    init {
        doRefresh(showIndicator = false)
    }

    fun refresh() {
        // 已有刷新在途（如首载自动刷新）：不重复发起，只点亮下拉指示器，
        // 由在途刷新结束时统一熄灭（doRefresh 的 finally）。
        if (isRefreshInFlight.value) {
            _isPullRefreshing.value = true
            return
        }
        doRefresh(showIndicator = true)
    }

    private fun doRefresh(showIndicator: Boolean) {
        // 任一刷新进行中忽略重复触发（首载未完成时用户又下拉等）。
        if (isRefreshInFlight.value) return
        viewModelScope.launch {
            isRefreshInFlight.value = true
            if (showIndicator) _isPullRefreshing.value = true
            try {
                runCatching { homeRepository.refreshHome() }
                    .onSuccess { result ->
                        currentOffset = result.loadedCount
                        _paging.value = PagingState(isLoadingMore = false, hasMore = result.hasMore)
                    }
            } finally {
                isRefreshInFlight.value = false
                _isPullRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        val paging = _paging.value
        // 刷新进行中不加载下一页：并发写入会与刷新的事务清理竞态，
        // 且进入主页自动刷新时底部转圈会被误触发。
        if (paging.isLoadingMore || !paging.hasMore || isRefreshInFlight.value) return

        viewModelScope.launch {
            _paging.value = paging.copy(isLoadingMore = true)
            runCatching { homeRepository.loadMore(currentOffset) }
                .onSuccess { result ->
                    currentOffset += result.loadedCount
                    _paging.value = PagingState(isLoadingMore = false, hasMore = result.hasMore)
                }
                .onFailure {
                    _paging.value = _paging.value.copy(isLoadingMore = false)
                }
        }
    }
}

private data class PagingState(
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)
