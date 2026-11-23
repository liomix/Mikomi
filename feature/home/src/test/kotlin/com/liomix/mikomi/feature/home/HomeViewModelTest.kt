package com.liomix.mikomi.feature.home

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.testing.MainDispatcherRule
import com.liomix.mikomi.data.anime.HomeRepository
import com.liomix.mikomi.data.anime.LoadMoreResult
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 首页状态机：内容由本地数据决定，刷新只是附加事实。
 *
 * 两条断言针对的是真实出过的问题：刷新失败曾把整条状态流**终止**在错误态，
 * 之后无论是重试还是数据自己到达，页面都再也回不去。
 *
 * 时钟：`MainDispatcherRule` 与 `runTest` 必须共用同一个 [TestCoroutineScheduler]。
 * 否则 ViewModel 里的 `delay`（`WhileSubscribed(5s)`、分页宽限 300ms）跑在另一套
 * 虚拟时钟上，测试会一直等下去 —— 那是「测试自己坏了」，不是「代码坏了」。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val scheduler = TestCoroutineScheduler()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher(scheduler))

    @Test
    fun `success exposes banners and recommended separately`() = runTest(scheduler) {
        val viewModel = HomeViewModel(FakeHomeRepository(banners = 5, recommended = 8))

        val content = viewModel.uiState.awaitContent<HomeContent.Success>()

        assertEquals(5, content.banners.size)
        assertEquals(8, content.anime.size)
    }

    @Test
    fun `refresh failure is recoverable when data arrives`() = runTest(scheduler) {
        val repository = FakeHomeRepository().apply { failRefresh = true }
        val viewModel = HomeViewModel(repository)

        assertTrue(viewModel.uiState.first { it.content is HomeContent.Error }.content is HomeContent.Error)

        // 数据自己到了（另一次刷新、或别的页面写库）：页面该回到内容态。
        repository.pushRecommended(count = 3)

        val content = viewModel.uiState.awaitContent<HomeContent.Success>()
        assertEquals(3, content.anime.size)
    }

    @Test
    fun `retry after failure leaves the error state`() = runTest(scheduler) {
        val repository = FakeHomeRepository().apply { failRefresh = true }
        val viewModel = HomeViewModel(repository)
        viewModel.uiState.first { it.content is HomeContent.Error }

        repository.failRefresh = false
        viewModel.refresh()

        // 数据仍为空，所以停在骨架屏而不是错误页 —— 关键是「重试有反应」。
        assertTrue(viewModel.uiState.first { it.content is HomeContent.Loading }.content is HomeContent.Loading)
    }

    @Test
    fun `loadMore stops at the last page`() = runTest(scheduler) {
        val viewModel = HomeViewModel(FakeHomeRepository(recommended = 8))
        viewModel.uiState.awaitContent<HomeContent.Success>()

        viewModel.loadMore()

        // 底部分页骨架一直挂到「本地条数真的变了」或宽限到期，所以这里等到它落下。
        val content = viewModel.uiState.first {
            (it.content as? HomeContent.Success)?.isLoadingMore == false
        }.content as HomeContent.Success
        assertEquals(false, content.hasMore)
    }

    /** 等到某一种内容态，并把它取出来。 */
    private suspend inline fun <reified T : HomeContent> StateFlow<HomeUiState>.awaitContent(): T =
        first { it.content is T }.content as T

    private class FakeHomeRepository(
        banners: Int = 0,
        recommended: Int = 0,
    ) : HomeRepository {

        private val bannerFlow = MutableStateFlow(animeList("banner", banners))
        private val recommendedFlow = MutableStateFlow(animeList("rec", recommended))

        /** 为真时 `refreshHome` 抛异常，用来复现「刷新失败但本地可能有数据」的场景。 */
        var failRefresh: Boolean = false

        fun pushRecommended(count: Int) {
            recommendedFlow.value = animeList("rec", count)
        }

        override fun observeBanners(): Flow<List<Anime>> = bannerFlow

        override fun observeRecommended(): Flow<List<Anime>> = recommendedFlow

        override suspend fun refreshHome(): LoadMoreResult {
            if (failRefresh) throw IOException("boom")
            return LoadMoreResult(hasMore = true, loadedCount = recommendedFlow.value.size)
        }

        override suspend fun loadMore(offset: Int): LoadMoreResult =
            LoadMoreResult(hasMore = false, loadedCount = 0)
    }

    private companion object {
        fun animeList(prefix: String, count: Int): List<Anime> =
            (1..count).map { Anime(id = "$prefix$it", title = "$prefix$it") }
    }
}
