package com.liomix.mikomi.data.anime.di

import com.liomix.mikomi.data.anime.AniCykRepository
import com.liomix.mikomi.data.anime.AniRpbRepository
import com.liomix.mikomi.data.anime.AnimeIdMapper
import com.liomix.mikomi.data.anime.AnimeRepository
import com.liomix.mikomi.data.anime.repository.CommentRepository
import com.liomix.mikomi.data.anime.repository.DetailRepository
import com.liomix.mikomi.data.anime.repository.HomeRepository
import com.liomix.mikomi.data.anime.repository.HotRepository
import com.liomix.mikomi.data.anime.repository.RankRepository
import com.liomix.mikomi.data.anime.repository.SearchRepository
import com.liomix.mikomi.data.anime.ScheduleRepository
import com.liomix.mikomi.data.anime.internal.AniCykRepositoryImpl
import com.liomix.mikomi.data.anime.internal.AniRpbRepositoryImpl
import com.liomix.mikomi.data.anime.internal.AnimeIdMapperImpl
import com.liomix.mikomi.data.anime.internal.AnimeRepositoryImpl
import com.liomix.mikomi.data.anime.repository.Ipml.HotRepositoryIpml
import com.liomix.mikomi.data.anime.internal.ScheduleRepositoryImpl
import com.liomix.mikomi.data.anime.repository.Ipml.CommentRepositoryIpml
import com.liomix.mikomi.data.anime.repository.Ipml.DetailRepositoryIpml
import com.liomix.mikomi.data.anime.repository.Ipml.HomeRepositoryIpml
import com.liomix.mikomi.data.anime.repository.Ipml.RankRepositoryIpml
import com.liomix.mikomi.data.anime.repository.Ipml.SearchRepositoryIpml
import com.liomix.mikomi.data.anime.remote.AnimeApi
import com.liomix.mikomi.data.anime.schedule.AniCykScheduleSource
import com.liomix.mikomi.data.anime.schedule.AniRpbScheduleSource
import com.liomix.mikomi.data.anime.schedule.AnimeIdentityRegistry
import com.liomix.mikomi.data.anime.schedule.AnimeIdentityRegistryImpl
import com.liomix.mikomi.data.anime.schedule.ScheduleSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
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
    internal abstract fun bindAniRpbRepository(impl: AniRpbRepositoryImpl): AniRpbRepository

    @Binds
    internal abstract fun bindAniCykRepository(impl: AniCykRepositoryImpl): AniCykRepository

    @Binds
    internal abstract fun bindAnimeRepository(impl: AnimeRepositoryImpl): AnimeRepository

    @Binds
    internal abstract fun bindHotRepository(impl: HotRepositoryIpml): HotRepository


    @Binds
    internal abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository


    @Binds
    internal abstract fun bindRankRepository(impl: RankRepositoryIpml): RankRepository

    @Binds
    internal abstract fun bindHomeRepository(impl: HomeRepositoryIpml): HomeRepository

    @Binds
    internal abstract fun bindDetailRepository(impl: DetailRepositoryIpml): DetailRepository

    @Binds
    internal abstract fun bindCommentRepository(impl: CommentRepositoryIpml): CommentRepository

    @Binds
    internal abstract fun bindSearchRepository(impl: SearchRepositoryIpml): SearchRepository

    @Binds
    internal abstract fun bindAnimeIdMapper(impl: AnimeIdMapperImpl): AnimeIdMapper

    @Binds
    internal abstract fun bindAnimeIdentityRegistry(
        impl: AnimeIdentityRegistryImpl,
    ): AnimeIdentityRegistry

    /**
     * 时间表数据源集合，由编排层按 priority 排序：次元库(1) → AniRpb(2)。
     *
     * 次元库内部还有「超清 / 樱花」两条线路（两家不同上游站点的片库）。它们不是平级源，
     * 而是在 [AniCykScheduleSource] 里合成一份日历：超清为主、樱花只补超清没有的番。
     * 因此这里只注册一个次元库源，优先级与 patch 行为同单线路时一致。
     */
    @Binds
    @IntoSet
    internal abstract fun bindAniCykScheduleSource(source: AniCykScheduleSource): ScheduleSource

    @Binds
    @IntoSet
    internal abstract fun bindAniRpbScheduleSource(source: AniRpbScheduleSource): ScheduleSource
}
