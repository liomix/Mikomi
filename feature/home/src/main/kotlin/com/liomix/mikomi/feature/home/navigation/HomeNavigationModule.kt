package com.liomix.mikomi.feature.home.navigation

import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.HomeDestination
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.RankDestination
import com.liomix.mikomi.core.navigation.ScheduleDestination
import com.liomix.mikomi.core.navigation.SearchDestination
import com.liomix.mikomi.core.navigation.SearchResultDestination
import com.liomix.mikomi.feature.home.HomeQuickAction
import com.liomix.mikomi.feature.home.HomeRoute
import com.liomix.mikomi.feature.home.rank.RankRoute
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
                        HomeQuickAction.RANK -> navigator.goTo(RankDestination)
                        // 分类目的地待接入。
                        HomeQuickAction.CATEGORY -> Unit
                    }
                },
            )
        }
        entry<ScheduleDestination> {
            ScheduleRoute(
                onBackClick = { navigator.goBack() },
                // AniRpb 的 video id 与详情页 id 空间不同，先按标题进入现有搜索结果。
                onAnimeTitleClick = { title -> navigator.goTo(SearchResultDestination(title)) },
            )
        }
        entry<RankDestination> {
            RankRoute(
                onBackClick = { navigator.goBack() },
                onAnimeClick = { animeId -> navigator.goTo(DetailDestination(animeId)) },
            )
        }
    }
}
