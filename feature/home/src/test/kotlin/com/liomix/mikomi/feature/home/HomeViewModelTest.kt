package com.liomix.mikomi.feature.home

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.testing.MainDispatcherRule
import com.liomix.mikomi.data.anime.HomeRepository
import com.liomix.mikomi.data.anime.LoadMoreResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `home view model constructs with a fake repository`() = runTest {
        val viewModel = HomeViewModel(FakeHomeRepository(emptyList(), emptyList()))
        assertNotNull(viewModel.uiState)
    }

    @Test
    fun `success state exposes banners and recommended separately`() = runTest {
        val banners = (1..5).map { id ->
            Anime(id = "banner$id", title = "轮播$id")
        }
        val recommended = (1..8).map { id ->
            Anime(id = "rec$id", title = "推荐$id")
        }
        val viewModel = HomeViewModel(FakeHomeRepository(banners, recommended))

        val success = viewModel.uiState
            .first { it is HomeUiState.Success } as HomeUiState.Success

        assertEquals(5, success.banners.size)
        assertEquals(8, success.anime.size)
        assertEquals("banner1", success.banners.first().id)
    }

    @Test
    fun `success state exposes empty lists when repository is empty`() = runTest {
        val viewModel = HomeViewModel(FakeHomeRepository(emptyList(), emptyList()))

        val success = viewModel.uiState
            .first { it is HomeUiState.Success } as HomeUiState.Success

        assertEquals(0, success.banners.size)
        assertEquals(0, success.anime.size)
    }

    @Test
    fun `loadMore updates hasMore state`() = runTest {
        val viewModel = HomeViewModel(FakeHomeRepository(emptyList(), emptyList()))

        viewModel.loadMore()

        val success = viewModel.uiState
            .first { it is HomeUiState.Success } as HomeUiState.Success
        assertEquals(false, success.hasMore)
        assertEquals(false, success.isLoadingMore)
    }

    private class FakeHomeRepository(
        private val banners: List<Anime>,
        private val recommended: List<Anime>,
    ) : HomeRepository {
        override fun observeBanners(): Flow<List<Anime>> = flowOf(banners)
        override fun observeRecommended(): Flow<List<Anime>> = flowOf(recommended)
        override suspend fun refreshHome(): LoadMoreResult =
            LoadMoreResult(hasMore = true, loadedCount = recommended.size)
        override suspend fun loadMore(offset: Int): LoadMoreResult =
            LoadMoreResult(hasMore = false, loadedCount = 0)
    }
}
