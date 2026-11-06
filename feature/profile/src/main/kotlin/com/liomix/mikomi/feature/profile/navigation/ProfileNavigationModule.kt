package com.liomix.mikomi.feature.profile.navigation

import com.liomix.mikomi.core.navigation.EditorDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.LocalNavigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.SettingsDestination
import com.liomix.mikomi.core.navigation.ThemeDestination
import com.liomix.mikomi.feature.profile.ProfileScreen
import com.liomix.mikomi.feature.profile.editor.EditorScreen
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
    fun provideEntryProviderInstaller(): EntryProviderInstaller = {
        entry<ProfileDestination> {
            val navigator = LocalNavigator.current
            ProfileScreen(
                onThemeClick = { navigator.navigate(ThemeDestination) },
                onSettingsClick = { navigator.navigate(SettingsDestination) },
                onEditInfo = { navigator.navigate(EditorDestination) },
            )
        }
        entry<EditorDestination> {
            val navigator = LocalNavigator.current
            EditorScreen(
                onBackClick = { navigator.goBack() },
                // 先做 UI：保存暂不落库，直接返回。
                onSave = { _, _, _ -> navigator.goBack() },
            )
        }
    }
}
