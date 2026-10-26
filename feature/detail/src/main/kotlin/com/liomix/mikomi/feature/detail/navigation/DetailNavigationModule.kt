package com.liomix.mikomi.feature.detail.navigation

import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.PlayerDestination
import com.liomix.mikomi.feature.detail.DetailRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ActivityRetainedComponent::class)
object DetailNavigationModule {

    @Provides
    @IntoSet
    fun provideEntryProviderInstaller(navigator: Navigator): EntryProviderInstaller = { _ ->
        entry<DetailDestination> { key ->
            DetailRoute(
                animeId = key.animeId,
                onEpisodeClick = { episodeId ->
                    navigator.goTo(PlayerDestination(key.animeId, episodeId))
                },
                onPlayClick = {
                    navigator.goTo(PlayerDestination(key.animeId, ""))
                },
            )
        }
    }
}
