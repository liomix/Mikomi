package com.liomix.mikomi.feature.search.result

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.data.anime.SearchRepository
import com.liomix.mikomi.feature.search.LoadState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 搜索结果页 ViewModel。
 *
 * [bind] 注入关键词后立即拉取第一页，滚动到底继续追加。
 *
 * 分页游标用「服务端已返回的原始条数」累加，不能用展示条数：
 * 结果会按 id 去重、并按类型过滤掉非动画条目，两者都会小于原始条数。
 */
@HiltViewModel
class SearchResultViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val keywordFlow = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(SearchResultUiState())
    val uiState: StateFlow<SearchResultUiState> = _uiState.asStateFlow()

    /** 下一页的服务端游标。 */
    private var nextOffset = 0

    /** 绑定关键词（同关键词重复调用无副作用）。 */
    fun bind(keyword: String) {
        if (keywordFlow.value == keyword) return
        keywordFlow.value = keyword
        nextOffset = 0
        _uiState.value = SearchResultUiState(keyword = keyword, state = LoadState.Loading)
        loadFirstPage(keyword)
    }

    private fun loadFirstPage(keyword: String) {
        viewModelScope.launch {
            try {
                val page = searchRepository.search(keyword, limit = PAGE_SIZE, offset = 0)
                nextOffset = page.rawCount
                _uiState.value = SearchResultUiState(
                    keyword = keyword,
                    items = page.items,
                    state = LoadState.Success,
                    hasMore = page.rawCount >= PAGE_SIZE,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Search failed, keyword=$keyword", e)
                _uiState.value = SearchResultUiState(
                    keyword = keyword,
                    state = LoadState.Error,
                )
            }
        }
    }

    /** 滚动到底部时追加下一页。 */
    fun loadMore() {
        val current = _uiState.value
        if (current.state != LoadState.Success) return
        if (!current.hasMore || current.loadingMore) return
        val keyword = keywordFlow.value ?: return
        val offset = nextOffset
        _uiState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val page = searchRepository.search(keyword, limit = PAGE_SIZE, offset = offset)
                nextOffset += page.rawCount
                _uiState.update { state ->
                    // 服务端分页可能因新增条目返回重复项，按 id 去重保证网格 key 唯一。
                    val existingIds = state.items.mapTo(HashSet()) { it.id }
                    val appended = page.items.filter { it.id !in existingIds }
                    state.copy(
                        items = state.items + appended,
                        loadingMore = false,
                        hasMore = page.rawCount >= PAGE_SIZE,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load more results failed", e)
                _uiState.update { it.copy(loadingMore = false, hasMore = false) }
            }
        }
    }

    /** 首屏失败后重试。 */
    fun retry() {
        val keyword = keywordFlow.value ?: return
        nextOffset = 0
        _uiState.value = SearchResultUiState(keyword = keyword, state = LoadState.Loading)
        loadFirstPage(keyword)
    }

    private companion object {
        const val TAG = "SearchResultViewModel"
        const val PAGE_SIZE = SearchRepository.SEARCH_PAGE_SIZE
    }
}
