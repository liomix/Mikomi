package com.liomix.mikomi.feature.settings.navigation

import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.LocalNavigator
import com.liomix.mikomi.core.navigation.PlaybackSettingsDestination
import com.liomix.mikomi.core.navigation.ProxyDestination
import com.liomix.mikomi.core.navigation.SettingsDestination
import com.liomix.mikomi.core.navigation.ThemeDestination
import com.liomix.mikomi.core.navigation.VdoPlgDestination
import com.liomix.mikomi.core.navigation.VdoPlgStoreDestination
import com.liomix.mikomi.feature.settings.SettingsScreen
import com.liomix.mikomi.feature.settings.playback.PlaybackSettingsRoute
import com.liomix.mikomi.feature.settings.plugin_test.PlgTstRoute
import com.liomix.mikomi.feature.settings.plugin_test.store.PlgTstStoreRoute
import com.liomix.mikomi.feature.settings.proxy.ProxyRoute
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
    fun provideEntryProviderInstaller(): EntryProviderInstaller = {
        entry<SettingsDestination> {
            val navigator = LocalNavigator.current
            SettingsScreen(
                onOpenTheme = { navigator.navigate(ThemeDestination) },
                onOpenProxy = { navigator.navigate(ProxyDestination) },
                onOpenPlugins = { navigator.navigate(VdoPlgDestination) },
                onOpenPlayback = { navigator.navigate(PlaybackSettingsDestination) },
                onBackClick = { navigator.goBack() },
            )
        }
        entry<ThemeDestination> {
            val navigator = LocalNavigator.current
            ThemeRoute(onBackClick = { navigator.goBack() })
        }
        entry<ProxyDestination> {
            val navigator = LocalNavigator.current
            ProxyRoute(onBackClick = { navigator.goBack() })
        }
        entry<PlaybackSettingsDestination> {
            val navigator = LocalNavigator.current
            PlaybackSettingsRoute(onBackClick = { navigator.goBack() })
        }
        entry<VdoPlgDestination> {
            val navigator = LocalNavigator.current
            PlgTstRoute(
                onBackClick = { navigator.goBack() },
                onOpenStore = { navigator.navigate(VdoPlgStoreDestination) },
            )
        }
        entry<VdoPlgStoreDestination> {
            val navigator = LocalNavigator.current
            PlgTstStoreRoute(onBackClick = { navigator.goBack() })
        }
    }
}
