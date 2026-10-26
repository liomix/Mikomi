package com.liomix.mikomi.data.anime.di

import com.liomix.mikomi.data.anime.AnimeIdMapper
import com.liomix.mikomi.data.anime.AnimeRepository
import com.liomix.mikomi.data.anime.HomeRepository
import com.liomix.mikomi.data.anime.RankRepository
import com.liomix.mikomi.data.anime.ScheduleRepository
import com.liomix.mikomi.data.anime.internal.AnimeIdMapperImpl
import com.liomix.mikomi.data.anime.internal.AnimeRepositoryImpl
import com.liomix.mikomi.data.anime.internal.HomeRepositoryImpl
import com.liomix.mikomi.data.anime.internal.RankRepositoryImpl
import com.liomix.mikomi.data.anime.internal.ScheduleRepositoryImpl
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

    @Binds
    internal abstract fun bindHomeRepository(impl: HomeRepositoryImpl): HomeRepository

    @Binds
    internal abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    internal abstract fun bindRankRepository(impl: RankRepositoryImpl): RankRepository

    @Binds
    internal abstract fun bindAnimeIdMapper(impl: AnimeIdMapperImpl): AnimeIdMapper
}
