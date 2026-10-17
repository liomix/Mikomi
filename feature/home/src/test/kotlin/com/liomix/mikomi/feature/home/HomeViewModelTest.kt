package com.liomix.mikomi.feature.home

import com.liomix.mikomi.core.model.Anime
import com.liomix.mikomi.core.model.Episode
import com.liomix.mikomi.core.testing.MainDispatcherRule
import com.liomix.mikomi.data.anime.AnimeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `home view model constructs with a fake repository`() = runTest {
        val viewModel = HomeViewModel(FakeAnimeRepository())
        assertNotNull(viewModel.uiState)
    }

    private class FakeAnimeRepository : AnimeRepository {
        override fun observeAnimeList(): Flow<List<Anime>> = flowOf(emptyList())
        override fun observeAnime(id: String): Flow<Anime?> = flowOf(null)
        override fun observeEpisodes(animeId: String): Flow<List<Episode>> = flowOf(emptyList())
        override suspend fun refreshAnimeList() = Unit
    }
}
