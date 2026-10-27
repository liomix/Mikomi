package com.liomix.mikomi.feature.search.navigation

import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.SearchDestination
import com.liomix.mikomi.core.navigation.SearchResultDestination
import com.liomix.mikomi.feature.search.SearchRoute
import com.liomix.mikomi.feature.search.result.SearchResultRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ActivityRetainedComponent::class)
object SearchNavigationModule {

    @Provides
    @IntoSet
    fun provideEntryProviderInstaller(navigator: Navigator): EntryProviderInstaller = { _ ->
        // 层级：其他页面 → 搜索页 → 搜索结果页；返回逐级回退。
        entry<SearchDestination> {
            SearchRoute(
                onBackClick = { navigator.goBack() },
                onSearch = { keyword -> navigator.goTo(SearchResultDestination(keyword)) },
                onAnimeClick = { animeId -> navigator.goTo(DetailDestination(animeId)) },
            )
        }
        entry<SearchResultDestination> { destination ->
            SearchResultRoute(
                keyword = destination.keyword,
                onBackClick = { navigator.goBack() },
                // 重新搜索：回到搜索页改词（关键词已保留在输入框），不再压新一层。
                onSearchAgain = { navigator.goBack() },
                onAnimeClick = { animeId -> navigator.goTo(DetailDestination(animeId)) },
            )
        }
    }
}
