package com.liomix.mikomi.feature.profile.navigation

import com.liomix.mikomi.core.navigation.CollectionDestination
import com.liomix.mikomi.core.navigation.DetailDestination
import com.liomix.mikomi.core.navigation.EditorDestination
import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.LocalNavigator
import com.liomix.mikomi.core.navigation.ProfileDestination
import com.liomix.mikomi.core.navigation.SettingsDestination
import com.liomix.mikomi.core.navigation.ThemeDestination
import com.liomix.mikomi.feature.profile.ProfileRoute
import com.liomix.mikomi.feature.profile.collection.CollectRoute
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
            ProfileRoute(
                onThemeClick = { navigator.navigate(ThemeDestination) },
                onSettingsClick = { navigator.navigate(SettingsDestination) },
                onEditInfo = { navigator.navigate(EditorDestination) },
                // 收藏卡片直接进详情：与首页点卡片的落点一致。
                onAnimeClick = { animeId -> navigator.navigate(DetailDestination(animeId)) },
                // 计数条 / 长按封面进收藏页：带 id 进去就直接是整理态并选中那一部。
                onCollectionClick = { editAnimeId ->
                    navigator.navigate(CollectionDestination(editAnimeId))
                },
            )
        }
        entry<CollectionDestination> { key ->
            val navigator = LocalNavigator.current
            CollectRoute(
                onBackClick = { navigator.goBack() },
                onAnimeClick = { animeId -> navigator.navigate(DetailDestination(animeId)) },
                editAnimeId = key.editAnimeId,
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
