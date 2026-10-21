package com.liomix.mikomi.feature.search.navigation

import com.liomix.mikomi.core.navigation.EntryProviderInstaller
import com.liomix.mikomi.core.navigation.SearchDestination
import com.liomix.mikomi.feature.search.SearchScreen
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ActivityRetainedComponent::class)
object SearchNavigationModule {

    @Provides
    @IntoSet
    fun provideEntryProviderInstaller(): EntryProviderInstaller = { _ ->
        entry<SearchDestination> {
            SearchScreen()
        }
    }
}
