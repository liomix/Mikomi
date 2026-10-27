package com.liomix.mikomi.feature.profile.navigation

import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.SettingsDestination
import com.liomix.mikomi.core.navigation.ThemeDestination
import com.liomix.mikomi.feature.profile.ProfileScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ActivityRetainedComponent::class)
object ProfileNavigationModule {

    @Provides
    @IntoSet
    fun provideEntryProviderInstaller(navigator: Navigator): EntryProviderInstaller = { _ ->
        entry<ProfileDestination> {
            ProfileScreen(
                onThemeClick = { navigator.goTo(ThemeDestination) },
                onSettingsClick = { navigator.goTo(SettingsDestination) },
            )
        }
    }
}
