package com.liomix.mikomi.feature.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.Episode
import com.liomix.mikomi.data.anime.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 详情页 ViewModel：观察单个番剧详情与剧集列表（Room SSOT）。
 *
 * [animeId] 由 Route 经 [bind] 注入（Navigation3 的 key 参数传递）。
 * 绑定后异步触发 Bangumi 详情刷新，刷新结果回写本地，由观察流自动发射。
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
) : ViewModel() {

    private val animeId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DetailUiState> = animeId
        .flatMapLatest { id ->
            if (id == null) {
                kotlinx.coroutines.flow.flowOf(DetailUiState.Loading)
            } else {
                combine(
                    animeRepository.observeAnime(id),
                    animeRepository.observeEpisodes(id),
                ) { anime: Anime?, episodes: List<Episode> ->
                    when {
                        anime != null -> DetailUiState.Success(anime = anime, episodes = episodes)
                        episodes.isEmpty() -> DetailUiState.Loading
                        else -> DetailUiState.Error
                    }
                }
            }
        }
        .catch { emit(DetailUiState.Error) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailUiState.Loading,
        )

    fun bind(animeId: String) {
        if (this.animeId.value == animeId) return
        this.animeId.value = animeId
        refreshDetail(animeId)
    }

    /** 拉取 Bangumi 完整详情并回写本地；失败静默（本地已有数据优先）。 */
    private fun refreshDetail(animeId: String) {
        viewModelScope.launch {
            try {
                animeRepository.refreshAnimeDetail(animeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh anime detail failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "DetailViewModel"
    }
}
