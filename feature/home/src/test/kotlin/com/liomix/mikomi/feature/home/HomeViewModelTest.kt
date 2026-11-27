package com.liomix.mikomi.feature.home

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.anime.SliderImage
import com.liomix.mikomi.core.testing.MainDispatcherRule
import com.liomix.mikomi.data.anime.repository.HomeContentRepository
import com.liomix.mikomi.data.anime.repository.SliderImageRepository
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
    fun `success exposes sliders and content separately`() = runTest(scheduler) {
        val fixture = Fixture(sliders = 5, items = 8)

        val content = fixture.viewModel.uiState.awaitContent<HomeContent.Success>()

        assertEquals(5, content.sliders.size)
        assertEquals(8, content.anime.size)
    }

    @Test
    fun `refresh failure is recoverable when data arrives`() = runTest(scheduler) {
        val fixture = Fixture()
        fixture.content.failRefresh = true

        fixture.viewModel.refresh()
        assertTrue(
            fixture.viewModel.uiState.first { it.content is HomeContent.Error }.content
                is HomeContent.Error,
        )

        // 数据自己到了（另一次刷新、或别的页面写库）：页面该回到内容态。
        fixture.content.push(count = 3)

        val content = fixture.viewModel.uiState.awaitContent<HomeContent.Success>()
        assertEquals(3, content.anime.size)
    }

    @Test
    fun `retry after failure leaves the error state`() = runTest(scheduler) {
        val fixture = Fixture()
        fixture.content.failRefresh = true
        fixture.viewModel.refresh()
        fixture.viewModel.uiState.first { it.content is HomeContent.Error }

        fixture.content.failRefresh = false
        fixture.viewModel.refresh()

        // 数据仍为空，所以停在骨架屏而不是错误页 —— 关键是「重试有反应」。
        assertTrue(
            fixture.viewModel.uiState.first { it.content is HomeContent.Loading }.content
                is HomeContent.Loading,
        )
    }

    @Test
    fun `slider failure does not block content refresh`() = runTest(scheduler) {
        val fixture = Fixture(items = 4)
        fixture.sliderImages.failRefresh = true

        fixture.viewModel.refresh()

        // 两个源互不取消：轮播失败，内容区这次刷新照样落地。
        val content = fixture.viewModel.uiState.awaitContent<HomeContent.Success>()
        assertEquals(4, content.anime.size)
        assertTrue(content.sliders.isEmpty())
    }

    @Test
    fun `loadMore stops at the last page`() = runTest(scheduler) {
        val fixture = Fixture(items = 8)
        fixture.viewModel.uiState.awaitContent<HomeContent.Success>()

        fixture.viewModel.loadMore()

        // 底部分页骨架一直挂到「本地条数真的变了」或宽限到期，所以这里等到它落下。
        val content = fixture.viewModel.uiState.first {
            (it.content as? HomeContent.Success)?.isLoadingMore == false
        }.content as HomeContent.Success
        assertEquals(false, content.hasMore)
    }

    /** 等到某一种内容态，并把它取出来。 */
    private suspend inline fun <reified T : HomeContent> StateFlow<HomeUiState>.awaitContent(): T =
        first { it.content is T }.content as T

    /**
     * 两个源 + 一个 ViewModel 的组合。
     *
     * 两个替身**各自独立**（不互相派生）：接口的语义就是「两条独立链路」，
     * 替身写成「一个喂另一个」会让「互不取消」这条行为永远测不出来。
     */
    private class Fixture(sliders: Int = 0, items: Int = 0) {
        val sliderImages = FakeSliderImageRepository(sliders)
        val content = FakeHomeContentRepository(items)
        val viewModel = HomeViewModel(content, sliderImages)
    }

    private class FakeHomeContentRepository(items: Int = 0) : HomeContentRepository {

        private val contentFlow = MutableStateFlow(animeList("rec", items))

        /** 为真时 `refresh` 抛异常，用来复现「刷新失败但本地可能有数据」的场景。 */
        var failRefresh: Boolean = false

        fun push(count: Int) {
            contentFlow.value = animeList("rec", count)
        }

        override fun observeContent(): Flow<List<Anime>> = contentFlow

        override suspend fun refreshIfStale() = Unit

        override suspend fun refresh(): Boolean {
            if (failRefresh) throw IOException("boom")
            return true
        }

        override suspend fun loadMore(): Boolean = false
    }

    private class FakeSliderImageRepository(count: Int = 0) : SliderImageRepository {

        private val sliderFlow = MutableStateFlow(sliderList(count))

        /** 为真时 `refresh` 抛异常，用来验证「轮播挂了不拖累内容区」。 */
        var failRefresh: Boolean = false

        override fun observeSliderImages(): Flow<List<SliderImage>> = sliderFlow

        override suspend fun refreshIfStale() = Unit

        override suspend fun refresh() {
            if (failRefresh) throw IOException("slider boom")
        }
    }

    private companion object {
        fun animeList(prefix: String, count: Int): List<Anime> =
            (1..count).map { Anime(id = "$prefix$it", title = "$prefix$it") }

        fun sliderList(count: Int): List<SliderImage> =
            (1..count).map {
                SliderImage(animeId = "slider$it", title = "slider$it", coverUrl = null)
            }
    }
}
