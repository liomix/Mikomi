package com.liomix.mikomi.feature.settings.navigation

import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.VdoPlgDestination
import com.liomix.mikomi.core.navigation.VdoPlgStoreDestination
import com.liomix.mikomi.core.navigation.ProxyDestination
import com.liomix.mikomi.core.navigation.SettingsDestination
import com.liomix.mikomi.core.navigation.ThemeDestination
import com.liomix.mikomi.feature.settings.SettingsScreen
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
    fun provideEntryProviderInstaller(navigator: Navigator): EntryProviderInstaller = { _ ->
        entry<SettingsDestination> {
            SettingsScreen(
                onOpenTheme = { navigator.goTo(ThemeDestination) },
                onOpenProxy = { navigator.goTo(ProxyDestination) },
                onOpenPlugins = { navigator.goTo(VdoPlgDestination) },
                onBackClick = { navigator.goBack() },
            )
        }
        entry<ThemeDestination> {
            ThemeRoute(onBackClick = { navigator.goBack() })
        }
        entry<ProxyDestination> {
            ProxyRoute(onBackClick = { navigator.goBack() })
        }
        entry<VdoPlgDestination> {
            PlgTstRoute(
                onBackClick = { navigator.goBack() },
                onOpenStore = { navigator.goTo(VdoPlgStoreDestination) },
            )
        }
        entry<VdoPlgStoreDestination> {
            PlgTstStoreRoute(onBackClick = { navigator.goBack() })
        }
    }
}
