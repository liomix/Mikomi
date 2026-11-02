package com.liomix.mikomi.feature.player.navigation

import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.Navigator
import com.liomix.mikomi.core.navigation.PlayerDestination
import com.liomix.mikomi.feature.player.PlayerRoute
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
    fun provideEntryProviderInstaller(navigator: Navigator): EntryProviderInstaller = { _ ->
        entry<PlayerDestination> { key ->
            PlayerRoute(
                animeId = key.animeId,
                episodeId = key.episodeId,
                onBackClick = { navigator.goBack() },
                // TODO: 横屏全屏播放（锁定方向 + 隐藏系统栏）后续单独实现。
                onFullscreenClick = {},
            )
        }
    }
}
