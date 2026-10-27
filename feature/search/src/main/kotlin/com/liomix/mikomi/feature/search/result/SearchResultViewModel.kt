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
 * 首次进入由 [bind] 注入导航参数里的关键词并拉取第一页；
 * 之后在本页直接改关键词再搜（[submitSearch]）时，结果原地替换，不新增导航层级。
 *
 * 分页游标用「服务端已返回的原始条数」累加，不能用展示条数：
 * 结果会按 id 去重、并按类型过滤掉非动画条目，两者都会小于原始条数。
 */
@HiltViewModel
class SearchResultViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchResultUiState())
    val uiState: StateFlow<SearchResultUiState> = _uiState.asStateFlow()

    /** 当前结果对应的关键词。 */
    private var currentKeyword = ""

    /** 下一页的服务端游标。 */
    private var nextOffset = 0

    /**
     * 绑定导航参数里的关键词并立即加载。
     *
     * 每次都重新加载：ViewModel 在 Activity 级共享，上次搜索的实例可能还活着，
     * 不重置就会出现「搜过 A 再搜 B，结果仍是 A」。
     */
    fun bind(keyword: String) {
        loadFirstPage(keyword)
    }

    /** 搜索框输入（只改输入内容，不触发搜索）。 */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** 清空搜索框（保留当前结果，等用户重新提交）。 */
    fun clearQuery() {
        _uiState.update { it.copy(query = "") }
    }

    /** 在结果页内再次搜索：替换当前结果。 */
    fun submitSearch(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        loadFirstPage(trimmed)
    }

    private fun loadFirstPage(keyword: String) {
        currentKeyword = keyword
        nextOffset = 0
        _uiState.value = SearchResultUiState(
            keyword = keyword,
            query = keyword,
            state = LoadState.Loading,
        )
        viewModelScope.launch {
            try {
                val page = searchRepository.search(keyword, limit = PAGE_SIZE, offset = 0)
                nextOffset = page.rawCount
                _uiState.update {
                    it.copy(
                        items = page.items,
                        state = LoadState.Success,
                        hasMore = page.rawCount >= PAGE_SIZE,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Search failed, keyword=$keyword", e)
                _uiState.update { it.copy(state = LoadState.Error) }
            }
        }
    }

    /** 滚动到底部时追加下一页。 */
    fun loadMore() {
        val current = _uiState.value
        if (current.state != LoadState.Success) return
        if (!current.hasMore || current.loadingMore) return
        val offset = nextOffset
        _uiState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val page = searchRepository.search(
                    keyword = currentKeyword,
                    limit = PAGE_SIZE,
                    offset = offset,
                )
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
        if (currentKeyword.isEmpty()) return
        loadFirstPage(currentKeyword)
    }

    private companion object {
        const val TAG = "SearchResultViewModel"
        const val PAGE_SIZE = SearchRepository.SEARCH_PAGE_SIZE
    }
}
