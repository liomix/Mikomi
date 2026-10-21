package com.liomix.mikomi.feature.settings.navigation

import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.SettingsDestination
import com.liomix.mikomi.feature.settings.SettingsScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ActivityRetainedComponent::class)
object SettingsNavigationModule {

    @Provides
    @IntoSet
    fun provideEntryProviderInstaller(): EntryProviderInstaller = { _ ->
        entry<SettingsDestination> {
            SettingsScreen()
        }
    }
}
