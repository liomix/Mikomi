package com.liomix.mikomi.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liomix.mikomi.data.anime.HomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 首页 ViewModel。
 *
 * 与 [com.liomix.mikomi.feature.home.rank.RankViewModel]、
 * [com.liomix.mikomi.feature.home.schedule.ScheduleViewModel] 同一个形状：
 * **在线数据只决定「有没有内容」，刷新只是一个附加事实**。由此两条行为是推出来的，
 * 不是各写一遍的补丁：
 *
 * - 本地已有数据就展示（离线优先），刷新失败不把已看到的内容换成错误页；
 * - 刷新失败只写进 [RefreshState.failed]，数据源照旧可观察 —— 重试与「数据自己到了」
 *   都能把页面带回内容态。
 *
 * 上一版把异常挂在合成流的末尾（`.catch {}`）。`catch` 的语义是**终止**：它发出
 * 一个错误态之后整条流就结束了，`stateIn` 永远停在那个值上 —— 错误页的「重试」
 * 点了没有反应，恢复只能靠重新进页面。这是「一个功能有两种实现」的典型：
 * 失败态既由流驱动，又指望命令把流救回来。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
) : ViewModel() {

    // 两个数据源各留一份引用：除了喂给 combine，loadMore 还要拿 recommended
    // 判断「本地是不是真的多出了条目」。
    private val banners = homeRepository.observeBanners()
    private val recommended = homeRepository.observeRecommended()

    private val refreshState = MutableStateFlow(RefreshState())
    private val paging = MutableStateFlow(PagingState())

    /** 在途刷新任务：同一时刻只允许一趟，连点不叠加请求。 */
    private var refreshJob: Job? = null

    /** 下一页的起始偏移，由每次刷新返回的 `loadedCount` 推进。 */
    private var offset = 0

    /**
     * 首页唯一状态：内容 + 下拉指示器。
     *
     * 指示器不放进 `Success` 分支：骨架屏上也要能下拉，错误页上也要能重试，
     * 塞进内容态就丢了。外层放**跨态**字段，内层 sealed 只表达「内容现在是什么」。
     */
    val uiState: StateFlow<HomeUiState> = combine(
        banners,
        recommended,
        refreshState,
        paging,
    ) { banners, recommended, refresh, paging ->
        HomeUiState(
            // 只有用户主动发起的那一次才转指示器；自动首载由骨架屏表达加载。
            isRefreshing = refresh.showsIndicator,
            content = when {
                banners.isNotEmpty() || recommended.isNotEmpty() -> HomeContent.Success(
                    banners = banners,
                    anime = recommended,
                    isLoadingMore = paging.isLoadingMore,
                    hasMore = paging.hasMore,
                )

                // 一个数据都没有时，才需要区分「还在加载」与「真的失败了」。
                refresh.failed && !refresh.inFlight -> HomeContent.Error
                else -> HomeContent.Loading
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        // 首页刚拉过就不发请求：轮播与推荐来自同一次刷新。
        startRefresh(userInitiated = false)
    }

    /** 用户下拉刷新，或错误页点重试：请求期间点亮下拉指示器。 */
    fun refresh() = startRefresh(userInitiated = true)

    private fun startRefresh(userInitiated: Boolean) {
        // 已有刷新在途：复用那一趟，只把指示器点亮，结束时由 finally 统一熄灭。
        if (refreshJob?.isActive == true) {
            if (userInitiated) refreshState.value = refreshState.value.copy(userVisible = true)
            return
        }
        refreshJob = viewModelScope.launch {
            // 每次发起都重置 failed：这是一次新的尝试，页面不该带着上一次的结论进加载。
            refreshState.value = RefreshState(inFlight = true, userVisible = userInitiated)
            try {
                // 非用户触发时交给数据层判断 TTL；它内部会决定要不要真的发请求，
                // 所以这里不需要再读返回值（分页状态由 Room 的列表长度驱动）。
                if (userInitiated) {
                    val result = homeRepository.refreshHome()
                    offset = result.loadedCount
                    paging.value = PagingState(isLoadingMore = false, hasMore = result.hasMore)
                } else {
                    homeRepository.refreshHomeIfStale()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh home failed", e)
                refreshState.value = refreshState.value.copy(failed = true)
            } finally {
                refreshState.value = refreshState.value.copy(inFlight = false, userVisible = false)
            }
        }
    }

    /**
     * 加载下一页。
     *
     * 结束时机不是「请求返回」，而是「本地真的多了一条」：请求返回与 Room 送新列表
     * 是两条独立路径，早一步撤掉底部骨架行，中间那一两帧就是「骨架没了、卡片还没到」。
     * 空页与失败时条数永远不变，所以最多等 [PAGING_GRACE_MILLIS] 就收。
     *
     * 这段等待以前写在 UI 里（`pendingSize` 哨兵 + 两个 `LaunchedEffect` + 宽限常量），
     * 等于把状态机摊进组合：页面每次重组都要重新推导「骨架行该不该留着」。
     */
    fun loadMore() {
        val state = paging.value
        // 刷新在途时不动分页：并发写入会与刷新的事务清理竞态。
        if (state.isLoadingMore || !state.hasMore || refreshState.value.inFlight) return
        // 以 UI 正在显示的那一份为准；还没有内容时没得翻页，按 0 处理。
        val shown = (uiState.value.content as? HomeContent.Success)?.anime?.size ?: 0
        viewModelScope.launch {
            paging.value = state.copy(isLoadingMore = true)
            try {
                val result = homeRepository.loadMore(offset)
                offset += result.loadedCount
                // 保持「正在补内容」，直到本地条数变化或宽限到期。
                paging.value = PagingState(isLoadingMore = true, hasMore = result.hasMore)
                withTimeoutOrNull(PAGING_GRACE_MILLIS) {
                    recommended.firstOrNull { it.size != shown }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Load more failed", e)
            } finally {
                paging.value = paging.value.copy(isLoadingMore = false)
            }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"

        /** 等本地落库的宽限：正常一两帧（~30ms）就到，这个时长只用来兜住空页与失败。 */
        const val PAGING_GRACE_MILLIS = 300L
    }
}

/** 分页游标：是否正在补内容、还有没有下一页。 */
private data class PagingState(
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)
