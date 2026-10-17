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
    private var currentOffset = 0

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
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { homeRepository.refreshHome() }
                .onSuccess { result ->
                    currentOffset = result.loadedCount
                    _paging.value = PagingState(isLoadingMore = false, hasMore = result.hasMore)
                }
        }
    }

    fun loadMore() {
        val paging = _paging.value
        if (paging.isLoadingMore || !paging.hasMore) return

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
