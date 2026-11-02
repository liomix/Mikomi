package com.liomix.mikomi.feature.settings.navigation

import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.PluginDestination
import com.liomix.mikomi.core.navigation.PluginStoreDestination
import com.liomix.mikomi.core.navigation.ProxyDestination
import com.liomix.mikomi.core.navigation.SettingsDestination
import com.liomix.mikomi.core.navigation.ThemeDestination
import com.liomix.mikomi.feature.settings.SettingsScreen
import com.liomix.mikomi.feature.settings.plugin.PluginRoute
import com.liomix.mikomi.feature.settings.plugin.store.PluginStoreRoute
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
    fun provideEntryProviderInstaller(navigator: Navigator): EntryProviderInstaller = { _ ->
        entry<SettingsDestination> {
            SettingsScreen(
                onOpenTheme = { navigator.goTo(ThemeDestination) },
                onOpenProxy = { navigator.goTo(ProxyDestination) },
                onOpenPlugins = { navigator.goTo(PluginDestination) },
                onBackClick = { navigator.goBack() },
            )
        }
        entry<ThemeDestination> {
            ThemeRoute(onBackClick = { navigator.goBack() })
        }
        entry<ProxyDestination> {
            ProxyRoute(onBackClick = { navigator.goBack() })
        }
        entry<PluginDestination> {
            PluginRoute(
                onBackClick = { navigator.goBack() },
                onOpenStore = { navigator.goTo(PluginStoreDestination) },
            )
        }
        entry<PluginStoreDestination> {
            PluginStoreRoute(onBackClick = { navigator.goBack() })
        }
    }
}
