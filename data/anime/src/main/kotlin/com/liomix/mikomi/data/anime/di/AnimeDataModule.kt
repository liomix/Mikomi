package com.liomix.mikomi.data.anime.di

import com.liomix.mikomi.data.anime.repository.CommentRepository
import com.liomix.mikomi.data.anime.repository.DetailRepository
import com.liomix.mikomi.data.anime.repository.HomeContentRepository
import com.liomix.mikomi.data.anime.repository.HotRepository
import com.liomix.mikomi.data.anime.repository.PlayerRepository
import com.liomix.mikomi.data.anime.repository.RankRepository
import com.liomix.mikomi.data.anime.repository.SearchRepository
import com.liomix.mikomi.data.anime.repository.ScheduleRepository
import com.liomix.mikomi.data.anime.repository.SliderImageRepository
import com.liomix.mikomi.data.anime.repository.Impl.HotRepositoryImpl
import com.liomix.mikomi.data.anime.repository.Impl.PlayerRepositoryImpl
import com.liomix.mikomi.data.anime.repository.Impl.ScheduleRepositoryImpl
import com.liomix.mikomi.data.anime.repository.Impl.CommentRepositoryImpl
import com.liomix.mikomi.data.anime.repository.Impl.DetailRepositoryImpl
import com.liomix.mikomi.data.anime.repository.Impl.HomeContentRepositoryImpl
import com.liomix.mikomi.data.anime.repository.Impl.RankRepositoryImpl
import com.liomix.mikomi.data.anime.repository.Impl.SearchRepositoryImpl
import com.liomix.mikomi.data.anime.repository.Impl.SliderImageRepositoryImpl
import com.liomix.mikomi.data.anime.remote.anicyk.AniCykScheduleSource
import com.liomix.mikomi.data.anime.remote.anirpb.AniRpbScheduleSource
import com.liomix.mikomi.data.anime.processor.schedule.AnimeIdentityRegistry
import com.liomix.mikomi.data.anime.processor.schedule.AnimeIdentityRegistryImpl
import com.liomix.mikomi.data.anime.processor.AnimeIdConverter
import com.liomix.mikomi.data.anime.processor.AnimeIdConverterImpl
import com.liomix.mikomi.data.anime.processor.schedule.ScheduleSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import retrofit2.Retrofit



@Module
@InstallIn(SingletonComponent::class)
internal abstract class AnimeDataModule {




    @Binds
    internal abstract fun bindPlayerRepository(impl: PlayerRepositoryImpl): PlayerRepository

    @Binds
    internal abstract fun bindHotRepository(impl: HotRepositoryImpl): HotRepository


    @Binds
    internal abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository


    @Binds
    internal abstract fun bindRankRepository(impl: RankRepositoryImpl): RankRepository

    @Binds
    internal abstract fun bindAnimeIdConverter(impl: AnimeIdConverterImpl): AnimeIdConverter

    @Binds
    internal abstract fun bindHomeContentRepository(
        impl: HomeContentRepositoryImpl,
    ): HomeContentRepository

    @Binds
    internal abstract fun bindSliderImageRepository(
        impl: SliderImageRepositoryImpl,
    ): SliderImageRepository

    @Binds
    internal abstract fun bindDetailRepository(impl: DetailRepositoryImpl): DetailRepository

    @Binds
    internal abstract fun bindCommentRepository(impl: CommentRepositoryImpl): CommentRepository

    @Binds
    internal abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository


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
