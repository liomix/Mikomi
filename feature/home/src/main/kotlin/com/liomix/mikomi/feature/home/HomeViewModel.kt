package com.liomix.mikomi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

    val uiState: StateFlow<HomeUiState> = combine<List<Anime>, List<Anime>, HomeUiState>(
        homeRepository.observeBanners(),
        homeRepository.observeRecommended(),
    ) { banners, recommended ->
        HomeUiState.Success(banners = banners, anime = recommended)
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
        }
    }
}
