package com.liomix.mikomi.data.anime.di

import com.liomix.mikomi.data.anime.schedule.ScheduleMergeConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 时间表合并策略的注入点。
 *
 * 当前用默认值（开启 patch：重复条目只补 base 缺失的具体时刻）。要退回「纯去重」，
 * 把 [ScheduleMergeConfig.patchMissingTime] 置 false 即可 —— 后续接远端配置 / DataStore
 * 时只需要改这一个 provider，合并逻辑不用动。
 */
@Module
@InstallIn(SingletonComponent::class)
object ScheduleModule {

    @Provides
    @Singleton
    internal fun provideScheduleMergeConfig(): ScheduleMergeConfig = ScheduleMergeConfig()
}
