package com.liomix.mikomi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    /**
     * 首页唯一状态：内容 + 下拉指示器。
     *
     * 指示器曾经是单独一条 `StateFlow<Boolean>`，Route 因此收集两次。代价不是两次重组
     * （Compose 会把同一帧的失效合并），而是**一次业务动作发两个状态**：刷新结束时
     * 「指示器熄灭」与「数据到达」是两条独立的流，页面要等它们都落地才稳定，
     * 中间那一帧就是「转圈没了、列表还是旧的」。合成一条之后，一次刷新只发一个对象。
     */
    val uiState: StateFlow<HomeUiState> = combine(
        homeRepository.observeBanners(),
        homeRepository.observeRecommended(),
        _paging,
        _isPullRefreshing,
    ) { banners, recommended, paging, pullRefreshing ->
        HomeUiState(
            isRefreshing = pullRefreshing,
            content = HomeContent.Success(
                banners = banners,
                anime = recommended,
                isLoadingMore = paging.isLoadingMore,
                hasMore = paging.hasMore,
            ),
        )
    }
        .catch { emit(HomeUiState(content = HomeContent.Error(it.message.orEmpty()))) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
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
