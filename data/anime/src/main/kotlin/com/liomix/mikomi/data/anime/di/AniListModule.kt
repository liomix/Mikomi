package com.liomix.mikomi.data.anime.di

import com.liomix.mikomi.data.anime.remote.anilist.AniListApi
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
object AniListModule {

    @Provides
    @Singleton
    @AniListRetrofit
    fun provideAniListRetrofit(json: Json, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(ANILIST_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    internal fun provideAniListApi(@AniListRetrofit retrofit: Retrofit): AniListApi =
        retrofit.create(AniListApi::class.java)

    private const val ANILIST_BASE_URL = "https://graphql.anilist.co/"
}
