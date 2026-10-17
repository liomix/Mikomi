package com.liomix.mikomi.data.anime.di

import com.liomix.mikomi.data.anime.remote.bangumi.BangumiApi
import com.liomix.mikomi.data.anime.remote.bangumi.BangumiTrendApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object BangumiModule {

    @Provides
    @Singleton
    @BangumiClient
    fun provideBangumiClient(client: OkHttpClient): OkHttpClient =
        client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", BANGUMI_USER_AGENT)
                        .build(),
                )
            }
            .build()

    @Provides
    @Singleton
    @BangumiNextRetrofit
    fun provideBangumiNextRetrofit(
        json: Json,
        @BangumiClient client: OkHttpClient,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BANGUMI_NEXT_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @BangumiApiRetrofit
    fun provideBangumiApiRetrofit(
        json: Json,
        @BangumiClient client: OkHttpClient,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BANGUMI_API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    internal fun provideBangumiTrendApi(@BangumiNextRetrofit retrofit: Retrofit): BangumiTrendApi =
        retrofit.create(BangumiTrendApi::class.java)

    @Provides
    internal fun provideBangumiApi(@BangumiApiRetrofit retrofit: Retrofit): BangumiApi =
        retrofit.create(BangumiApi::class.java)

    private const val BANGUMI_NEXT_BASE_URL = "https://next.bgm.tv/"
    private const val BANGUMI_API_BASE_URL = "https://api.bgm.tv/"
    private const val BANGUMI_USER_AGENT =
        "Mikomi/1.0.0 (Android) (https://github.com/liomix/mikomi)"
}
