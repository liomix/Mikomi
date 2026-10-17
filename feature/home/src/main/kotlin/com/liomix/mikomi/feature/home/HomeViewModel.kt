package com.liomix.mikomi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = animeRepository.observeAnimeList()
        .map<List<Anime>, HomeUiState> { anime ->
            HomeUiState.Success(
                banners = anime.take(BANNER_COUNT),
                anime = anime,
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
            runCatching { animeRepository.refreshAnimeList() }
        }
    }

    private companion object {
        /** 轮播图数量上限；数据源接入后由服务端返回的 banner 列表替代。 */
        const val BANNER_COUNT = 5
    }
}
