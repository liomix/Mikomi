package com.liomix.mikomi.core.analytics.di

import com.liomix.mikomi.core.analytics.AnalyticsEvent
import com.liomix.mikomi.core.analytics.AnalyticsTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    /**
     * 空实现：埋点尚未接入上报端点。
     *
     * 这里必须是匿名对象 —— `AnalyticsTracker` 是普通 Kotlin 接口（不是 `fun interface`），
     * `AnalyticsTracker { }` 那种 SAM 简写只在 Java 接口或 `fun interface` 上成立，
     * 它会编译失败。而这个模块此前没有任何模块依赖，所以这份错误一直没被编译到过。
     */
    @Provides
    @Singleton
    fun provideAnalyticsTracker(): AnalyticsTracker = object : AnalyticsTracker {
        override fun track(event: AnalyticsEvent) = Unit
    }
}
