package com.liomix.mikomi.data.anime.di

import com.liomix.mikomi.data.anime.AnimeRepository
import com.liomix.mikomi.data.anime.internal.AnimeRepositoryImpl
import com.liomix.mikomi.data.anime.remote.AnimeApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AnimeApiModule {

    @Provides
    fun provideAnimeApi(retrofit: Retrofit): AnimeApi = retrofit.create(AnimeApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AnimeDataModule {

    @Binds
    internal abstract fun bindAnimeRepository(impl: AnimeRepositoryImpl): AnimeRepository
}
