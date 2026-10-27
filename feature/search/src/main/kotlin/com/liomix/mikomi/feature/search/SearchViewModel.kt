package com.liomix.mikomi.feature.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.data.anime.SearchRepository
import com.liomix.mikomi.data.user.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 搜索页 ViewModel（只管「输入」这一层）。
 *
 * 三条数据线彼此独立：
 * - 搜索历史：DataStore 实时观察；
 * - 热门推荐：进页面拉取一次，失败可重试；
 * - 输入联想：防抖 280ms。
 *
 * 结果列表由下一级的结果页（`feature.search.result`）自己加载，
 * 这里只负责提交时记录历史，导航交给 Route 层。
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** 输入流：防抖后用于联想请求。 */
    private val queryFlow = MutableStateFlow("")

    init {
        observeHistory()
        observeSuggestions()
        loadTrending()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            userDataRepository.observeSearchHistory().collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    /** 输入防抖联想：新输入取消上一轮请求，避免结果错位。 */
    private fun observeSuggestions() {
        viewModelScope.launch {
            queryFlow
                .debounce(SUGGEST_DEBOUNCE_MILLIS)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { keyword ->
                    if (keyword.isEmpty()) {
                        _uiState.update {
                            it.copy(suggestions = emptyList(), suggestionsState = LoadState.Idle)
                        }
                        return@collectLatest
                    }
                    _uiState.update { it.copy(suggestionsState = LoadState.Loading) }
                    try {
                        val items = searchRepository.suggest(keyword)
                        _uiState.update {
                            it.copy(suggestions = items, suggestionsState = LoadState.Success)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Load suggestions failed", e)
                        _uiState.update { it.copy(suggestionsState = LoadState.Error) }
                    }
                }
        }
    }

    private fun loadTrending() {
        viewModelScope.launch {
            _uiState.update { it.copy(trendingState = LoadState.Loading) }
            try {
                val items = searchRepository.fetchTrending()
                _uiState.update {
                    it.copy(trending = items, trendingState = LoadState.Success)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load trending failed", e)
                _uiState.update { it.copy(trendingState = LoadState.Error) }
            }
        }
    }

    /** 输入变化：清空时回到浏览态，否则进入联想态。 */
    fun onQueryChange(query: String) {
        queryFlow.value = query
        _uiState.update { state ->
            state.copy(
                query = query,
                phase = if (query.isBlank()) SearchPhase.BROWSE else SearchPhase.SUGGEST,
            )
        }
    }

    /** 清空输入：回到浏览态（历史 + 热门）。 */
    fun clearQuery() {
        queryFlow.value = ""
        _uiState.update {
            it.copy(
                query = "",
                phase = SearchPhase.BROWSE,
                suggestions = emptyList(),
                suggestionsState = LoadState.Idle,
            )
        }
    }

    /**
     * 提交搜索：记录历史并把关键词写回输入框。
     *
     * 特意**不切换** phase：点击联想后若立刻从联想态切回浏览态，
     * Crossfade 动画会和页面跳转动画叠在一起，表现为闪一下。
     * 浏览态复位统一放到页面离开时（[onPageHidden]）。
     */
    fun onSearchSubmitted(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        queryFlow.value = trimmed
        _uiState.update { it.copy(query = trimmed) }
        viewModelScope.launch {
            runCatching { userDataRepository.addSearchHistory(trimmed) }
                .onFailure { Log.w(TAG, "Save search history failed", it) }
        }
    }

    /** 搜索页离开（去结果页或返回上一页）时复位为浏览态，返回时直接看到历史列表。 */
    fun onPageHidden() {
        queryFlow.value = ""
        _uiState.update {
            it.copy(
                phase = SearchPhase.BROWSE,
                suggestions = emptyList(),
                suggestionsState = LoadState.Idle,
            )
        }
    }

    /** 重试热门推荐。 */
    fun retryTrending() = loadTrending()

    fun removeHistory(keyword: String) {
        viewModelScope.launch {
            runCatching { userDataRepository.removeSearchHistory(keyword) }
                .onFailure { Log.w(TAG, "Remove search history failed", it) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { userDataRepository.clearSearchHistory() }
                .onFailure { Log.w(TAG, "Clear search history failed", it) }
        }
    }

    private companion object {
        const val TAG = "SearchViewModel"

        /** 联想防抖时长（对齐 Dart 版 280ms）。 */
        const val SUGGEST_DEBOUNCE_MILLIS = 280L
    }
}
