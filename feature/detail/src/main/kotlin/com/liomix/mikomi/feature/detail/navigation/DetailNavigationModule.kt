package com.liomix.mikomi.feature.detail.navigation

import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.LocalNavigator
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
    fun provideEntryProviderInstaller(): EntryProviderInstaller = {
        entry<DetailDestination> { key ->
            val navigator = LocalNavigator.current
            DetailRoute(
                animeId = key.animeId,
                onPlayClick = {
                    navigator.navigate(PlayerDestination(key.animeId, ""))
                },
            )
        }
    }
}
