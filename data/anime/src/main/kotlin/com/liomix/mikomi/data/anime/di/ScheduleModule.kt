package com.liomix.mikomi.data.anime.di

import com.liomix.mikomi.data.anime.processor.schedule.ScheduleMergeConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 时间表合并策略的注入点。
 *
 * 为什么不用 `@Inject constructor`：它的四个参数是 `Boolean` / `Int`，没有任何限定符
 * 可以区分 —— Dagger 无法决定「这个 boolean 该填什么」，只能报 MissingBinding。
 * 所以这里显式构造并给默认值，这也是它非用 `@Provides` 不可的原因。
 *
 * 要退回「纯去重」，把 [ScheduleMergeConfig.patchMissingTime] 置 false 即可。
 */
@Module
@InstallIn(SingletonComponent::class)
object ScheduleModule {

    @Provides
    @Singleton
    internal fun provideScheduleMergeConfig(): ScheduleMergeConfig = ScheduleMergeConfig()
}
