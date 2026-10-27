package com.liomix.mikomi.feature.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.data.anime.AnimeCreditsRepository
import com.liomix.mikomi.data.anime.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 详情页 ViewModel：观察番剧详情（Room SSOT），主创数据懒加载。
 *
 * [animeId] 由 Route 经 [bind] 注入（Navigation3 的 key 参数传递）。
 * 绑定后异步触发 Bangumi 详情刷新；主创（角色/制作人员）在进入主创 Tab 时按需拉取。
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val animeRepository: AnimeRepository,
    private val creditsRepository: AnimeCreditsRepository,
) : ViewModel() {

    private val animeId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DetailUiState> = animeId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(DetailUiState.Loading)
            } else {
                animeRepository.observeAnime(id).map { anime: Anime? ->
                    when {
                        anime != null -> DetailUiState.Success(anime = anime)
                        else -> DetailUiState.Loading
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

    private val _creditsState = MutableStateFlow<AnimeCreditsUiState>(AnimeCreditsUiState.Idle)
    val creditsState: StateFlow<AnimeCreditsUiState> = _creditsState

    fun bind(animeId: String) {
        if (this.animeId.value == animeId) return
        this.animeId.value = animeId
        _creditsState.value = AnimeCreditsUiState.Idle
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

    /** 懒加载主创（角色 + 制作人员）；进入主创 Tab 时调用。 */
    fun loadCredits() {
        val current = _creditsState.value
        if (current is AnimeCreditsUiState.Loaded || current is AnimeCreditsUiState.Loading) return
        val id = animeId.value ?: return
        _creditsState.value = AnimeCreditsUiState.Loading
        viewModelScope.launch {
            try {
                val credits = creditsRepository.fetchCredits(id)
                _creditsState.value = AnimeCreditsUiState.Loaded(
                    characters = credits.characters,
                    staff = credits.staff,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load credits failed", e)
                _creditsState.value = AnimeCreditsUiState.Error
            }
        }
    }

    private companion object {
        const val TAG = "DetailViewModel"
    }
}
