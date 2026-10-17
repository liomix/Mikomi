package com.liomix.mikomi.feature.home

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.Episode
import com.liomix.mikomi.core.testing.MainDispatcherRule
import com.liomix.mikomi.data.anime.AnimeRepository
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
        val viewModel = HomeViewModel(FakeAnimeRepository(emptyList()))
        assertNotNull(viewModel.uiState)
    }

    @Test
    fun `success state exposes banners as first five anime`() = runTest {
        val anime = (1..8).map { id ->
            Anime(id = "id$id", title = "番剧$id")
        }
        val viewModel = HomeViewModel(FakeAnimeRepository(anime))

        val success = viewModel.uiState
            .first { it is HomeUiState.Success } as HomeUiState.Success

        assertEquals(5, success.banners.size)
        assertEquals(8, success.anime.size)
        assertEquals("id1", success.banners.first().id)
    }

    @Test
    fun `success state exposes empty banners when list is empty`() = runTest {
        val viewModel = HomeViewModel(FakeAnimeRepository(emptyList()))

        val success = viewModel.uiState
            .first { it is HomeUiState.Success } as HomeUiState.Success

        assertEquals(0, success.banners.size)
        assertEquals(0, success.anime.size)
    }

    private class FakeAnimeRepository(
        private val anime: List<Anime>,
    ) : AnimeRepository {
        override fun observeAnimeList(): Flow<List<Anime>> = flowOf(anime)
        override fun observeAnime(id: String): Flow<Anime?> = flowOf(null)
        override fun observeEpisodes(animeId: String): Flow<List<Episode>> = flowOf(emptyList())
        override suspend fun refreshAnimeList() = Unit
    }
}
