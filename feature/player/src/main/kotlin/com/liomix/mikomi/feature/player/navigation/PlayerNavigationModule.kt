package com.liomix.mikomi.feature.player.navigation

import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.PlayerDestination
import com.liomix.mikomi.feature.player.PlayerScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ActivityRetainedComponent::class)
object PlayerNavigationModule {

    @Provides
    @IntoSet
    fun provideEntryProviderInstaller(): EntryProviderInstaller = { _ ->
        entry<PlayerDestination> { key ->
            PlayerScreen(animeId = key.animeId, episodeId = key.episodeId)
        }
    }
}
