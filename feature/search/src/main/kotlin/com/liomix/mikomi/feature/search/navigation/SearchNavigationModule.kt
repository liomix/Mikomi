package com.liomix.mikomi.feature.search.navigation

import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.LocalNavigator
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
    fun provideEntryProviderInstaller(): EntryProviderInstaller = {
        // 层级：其他页面 → 搜索页 → 搜索结果页；返回逐级回退。
        entry<SearchDestination> {
            val navigator = LocalNavigator.current
            SearchRoute(
                onBackClick = { navigator.goBack() },
                onSearch = { keyword -> navigator.navigate(SearchResultDestination(keyword)) },
            )
        }
        entry<SearchResultDestination> { destination ->
            val navigator = LocalNavigator.current
            SearchResultRoute(
                keyword = destination.keyword,
                onBackClick = { navigator.goBack() },
                onAnimeClick = { animeId -> navigator.navigate(DetailDestination(animeId)) },
            )
        }
    }
}
