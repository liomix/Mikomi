package com.liomix.mikomi.feature.animap.navigation

import com.liomix.mikomi.core.navigation.AniMapDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.ImmersionBridge
import com.liomix.mikomi.feature.animap.AniMapScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ActivityRetainedComponent::class)
object AniMapNavigationModule {

    @Provides
    @IntoSet
    fun provideEntryProviderInstaller(immersionBridge: ImmersionBridge): EntryProviderInstaller =
        { _ ->
            entry<AniMapDestination> {
                AniMapScreen(immersionBridge = immersionBridge)
            }
        }
}
