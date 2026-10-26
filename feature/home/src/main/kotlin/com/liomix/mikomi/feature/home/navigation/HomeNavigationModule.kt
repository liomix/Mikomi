package com.liomix.mikomi.feature.home.navigation

import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.HomeDestination
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.ScheduleDestination
import com.liomix.mikomi.core.navigation.SearchDestination
import com.liomix.mikomi.feature.home.HomeQuickAction
import com.liomix.mikomi.feature.home.HomeRoute
import com.liomix.mikomi.feature.home.schedule.ScheduleRoute
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
    fun provideEntryProviderInstaller(navigator: Navigator): EntryProviderInstaller = { _ ->
        entry<HomeDestination> {
            HomeRoute(
                onAnimeClick = { animeId -> navigator.goTo(DetailDestination(animeId)) },
                onSearchClick = { navigator.goTo(SearchDestination) },
                onProfileClick = { navigator.selectTopLevel(ProfileDestination) },
                onActionClick = { action ->
                    when (action) {
                        HomeQuickAction.SCHEDULE -> navigator.goTo(ScheduleDestination)
                        // 排行榜 / 分类目的地待接入。
                        HomeQuickAction.RANK,
                        HomeQuickAction.CATEGORY,
                        -> Unit
                    }
                },
            )
        }
        entry<ScheduleDestination> {
            ScheduleRoute(
                onBackClick = { navigator.goBack() },
                onAnimeClick = { animeId -> navigator.goTo(DetailDestination(animeId)) },
            )
        }
    }
}
