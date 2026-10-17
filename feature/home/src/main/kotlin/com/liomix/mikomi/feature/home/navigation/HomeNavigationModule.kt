package com.liomix.mikomi.feature.home.navigation

import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.HomeDestination
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.SearchDestination
import com.liomix.mikomi.feature.home.HomeRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ActivityRetainedComponent::class)
object HomeNavigationModule {

    @Provides
    @IntoSet
    fun provideEntryProviderInstaller(navigator: Navigator): EntryProviderInstaller = {
        entry<HomeDestination> {
            HomeRoute(
                onAnimeClick = { animeId -> navigator.goTo(DetailDestination(animeId)) },
                onSearchClick = { navigator.goTo(SearchDestination) },
                onProfileClick = { navigator.selectTopLevel(ProfileDestination) },
                onActionClick = {
                    // 时间表 / 排行榜 / 分类目的地待接入。
                },
            )
        }
    }
}
