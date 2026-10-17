package com.liomix.mikomi.data.anime.di

import javax.inject.Qualifier

/** Bangumi Next（next.bgm.tv）专用 Retrofit 限定符。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BangumiNextRetrofit

/** Bangumi v0（api.bgm.tv）专用 Retrofit 限定符。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BangumiApiRetrofit

/** 携带 Bangumi 官方要求 User-Agent 的 OkHttpClient 限定符。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BangumiClient
