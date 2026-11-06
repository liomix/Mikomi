package com.liomix.mikomi.feature.player.navigation

import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.LocalNavigator
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
    fun provideEntryProviderInstaller(): EntryProviderInstaller = {
        entry<PlayerDestination> { key ->
            val navigator = LocalNavigator.current
            PlayerRoute(
                animeId = key.animeId,
                episodeId = key.episodeId,
                onBackClick = { navigator.goBack() },
                // 全屏是播放页自己的视图状态（同 window 换约束），不经过导航：
                // 新开目的地会换 window，播放器的 Surface 必须重建，切一次黑一帧。
            )
        }
    }
}
