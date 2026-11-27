package com.liomix.mikomi.feature.home.navigation

import com.liomix.mikomi.core.navigation.CategoryDestination
import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.HomeDestination
import com.liomix.mikomi.core.navigation.LocalNavigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.RankDestination
import com.liomix.mikomi.core.navigation.ScheduleDestination
import com.liomix.mikomi.core.navigation.SearchDestination
import com.liomix.mikomi.core.navigation.SearchResultDestination
import com.liomix.mikomi.feature.home.components.HomeQuickAction
import com.liomix.mikomi.feature.home.HomeRoute
import com.liomix.mikomi.feature.home.category.CategoryRoute
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
    fun provideEntryProviderInstaller(): EntryProviderInstaller = {
        entry<HomeDestination> {
            val navigator = LocalNavigator.current
            HomeRoute(
                onAnimeClick = { animeId -> navigator.navigate(DetailDestination(animeId)) },
                onSearchClick = { navigator.navigate(SearchDestination) },
                onProfileClick = { navigator.selectTopLevel(ProfileDestination) },
                onActionClick = { action ->
                    when (action) {
                        HomeQuickAction.SCHEDULE -> navigator.navigate(ScheduleDestination)
                        HomeQuickAction.RANK -> navigator.navigate(RankDestination)
                        HomeQuickAction.CATEGORY -> navigator.navigate(CategoryDestination)
                    }
                },
            )
        }
        entry<ScheduleDestination> {
            val navigator = LocalNavigator.current
            ScheduleRoute(
                onBackClick = { navigator.goBack() },
                // AniRpb 的 video id 与详情页 id 空间不同，先按标题进入现有搜索结果。
                onAnimeTitleClick = { title -> navigator.navigate(SearchResultDestination(title)) },
            )
        }
        entry<CategoryDestination> {
            val navigator = LocalNavigator.current
            CategoryRoute(
                onBackClick = { navigator.goBack() },
                // 分类里的条目来自 Bangumi 搜索，id 与详情页同一命名空间，可直连。
                onAnimeClick = { animeId -> navigator.navigate(DetailDestination(animeId)) },
            )
        }
        entry<RankDestination> {
            val navigator = LocalNavigator.current
            RankRoute(
                onBackClick = { navigator.goBack() },
                // 榜单只取 AniRpb，其 video id 与详情页 id 空间不同，先按标题进入现有搜索结果。
                onAnimeTitleClick = { title -> navigator.navigate(SearchResultDestination(title)) },
            )
        }
    }
}
