package com.liomix.mikomi.feature.settings.navigation

import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.SettingsDestination
import com.liomix.mikomi.core.navigation.ThemeDestination
import com.liomix.mikomi.feature.settings.SettingsScreen
import com.liomix.mikomi.feature.settings.theme.ThemeRoute
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
    fun provideEntryProviderInstaller(navigator: Navigator): EntryProviderInstaller = { _ ->
        entry<SettingsDestination> {
            SettingsScreen()
        }
        entry<ThemeDestination> {
            ThemeRoute(onBackClick = { navigator.goBack() })
        }
    }
}
