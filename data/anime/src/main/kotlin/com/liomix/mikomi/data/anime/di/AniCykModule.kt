package com.liomix.mikomi.data.anime.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * AniCyk 的 HTTP 依赖。
 *
 * 复用全局 [OkHttpClient] 的连接池与拦截器，只覆盖超时：主 API 是明文节点，链路抖动比
 * HTTPS 站点更明显，长读超时会把整个时间表刷新拖住。
 */
@Module
@InstallIn(SingletonComponent::class)
object AniCykModule {

    @Provides
    @Singleton
    @AniCykHttp
    fun provideAniCykHttpClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
}
