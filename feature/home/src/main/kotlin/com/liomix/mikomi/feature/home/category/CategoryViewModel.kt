package com.liomix.mikomi.feature.home.category

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.CategoryQuery
import com.liomix.mikomi.data.anime.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 分类浏览。
 *
 * ## 条件变更是「换一趟」，不是「往同一列表里加」
 *
 * [query] 用 `collectLatest` 驱动加载：用户改一格筛选，上一次请求就被取消、列表清空重来。
 * 这样不需要自己维护「这次结果属于哪个条件」—— 条件的生命周期由协程的生命周期表达。
 *
 * ## 翻页会重
 *
 * 服务端按 `offset` 分页，翻页期间有新条目上架时相邻页会重复返回（数据源注释里写过）。
 * 合并时按 id 去重，不去和服务端较劲。
 *
 * ## 结果的归属校验
 *
 * 翻页请求与条件变更是两个协程，可能交错：条件已经换了，旧条件的第二页才回来。
 * 所以每次写回前都比一次 `query` —— 对不上就把这次结果丢掉，而不是把它混进新列表。
 */
@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val query = MutableStateFlow(CategoryQuery())

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    /** 下一页的页码；条件变化时归零。 */
    private var nextPage = 0

    init {
        viewModelScope.launch {
            query.collectLatest { current ->
                _uiState.value = CategoryUiState(query = current, content = CategoryContent.Loading)
                nextPage = 0
                load(current)
            }
        }
    }

    /** 改一格筛选。值没变时 StateFlow 不发射，等于什么都不做。 */
    fun onQueryChange(newQuery: CategoryQuery) {
        query.value = newQuery
    }

    fun loadMore() {
        val content = _uiState.value.content as? CategoryContent.Success ?: return
        if (content.isLoadingMore || !content.hasMore) return
        val current = _uiState.value.query
        viewModelScope.launch { load(current) }
    }

    /** 错误态点重试：条件不变，从第一页重来。 */
    fun retry() {
        val current = _uiState.value.query
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(content = CategoryContent.Loading)
            nextPage = 0
            load(current)
        }
    }

    private suspend fun load(expected: CategoryQuery) {
        val page = nextPage
        val isFirstPage = page == 0
        if (!isFirstPage) {
            val current = _uiState.value.content as? CategoryContent.Success ?: return
            _uiState.value = _uiState.value.copy(content = current.copy(isLoadingMore = true))
        }
        try {
            val result = categoryRepository.browse(expected, page)
            // 条件已经换了：这次结果属于上一趟，丢掉。
            if (_uiState.value.query != expected) return
            nextPage = page + 1
            val merged = if (isFirstPage) {
                result.items
            } else {
                val shown = (_uiState.value.content as? CategoryContent.Success)?.anime.orEmpty()
                (shown + result.items).distinctBy { it.id }
            }
            _uiState.value = _uiState.value.copy(
                content = if (merged.isEmpty()) {
                    CategoryContent.Empty
                } else {
                    CategoryContent.Success(
                        anime = merged,
                        isLoadingMore = false,
                        hasMore = result.hasMore,
                    )
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Browse category failed", e)
            if (_uiState.value.query != expected) return
            _uiState.value = _uiState.value.copy(
                content = if (isFirstPage) {
                    CategoryContent.Error
                } else {
                    // 翻页失败：保留已显示的，只收起底部骨架，用户可以再滚一次重试。
                    (_uiState.value.content as? CategoryContent.Success)
                        ?.copy(isLoadingMore = false)
                        ?: _uiState.value.content
                },
            )
        }
    }

    private companion object {
        const val TAG = "CategoryViewModel"
    }
}

/**
 * 分类页状态。
 *
 * 筛选条件放在**外层**：四个胶囊在任何内容态下都要能点（加载中、空、出错时改一格就能
 * 重新开始），塞进 `Success` 的话错误页就换不了条件了。
 */
data class CategoryUiState(
    val query: CategoryQuery = CategoryQuery(),
    val content: CategoryContent = CategoryContent.Loading,
)

/** 内容态：同一时刻只会是其中一种。 */
sealed interface CategoryContent {
    data object Loading : CategoryContent

    data class Success(
        val anime: List<Anime>,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
    ) : CategoryContent

    data object Empty : CategoryContent

    data object Error : CategoryContent
}
