package com.liomix.mikomi.core.database.di

import android.content.Context
import androidx.room.Room
import com.liomix.mikomi.core.database.MikomiDatabase
import com.liomix.mikomi.core.database.dao.AnimeDao
import com.liomix.mikomi.core.database.dao.EpisodeDao
import com.liomix.mikomi.core.database.dao.FavoriteDao
import com.liomix.mikomi.core.database.dao.HistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MikomiDatabase =
        Room.databaseBuilder(context, MikomiDatabase::class.java, "mikomi.db").build()

    @Provides
    fun provideAnimeDao(db: MikomiDatabase): AnimeDao = db.animeDao()

    @Provides
    fun provideEpisodeDao(db: MikomiDatabase): EpisodeDao = db.episodeDao()

    @Provides
    fun provideFavoriteDao(db: MikomiDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideHistoryDao(db: MikomiDatabase): HistoryDao = db.historyDao()
}
