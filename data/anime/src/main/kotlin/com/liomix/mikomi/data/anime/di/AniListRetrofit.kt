package com.liomix.mikomi.data.anime.di

import javax.inject.Qualifier

/** AniList 专用 Retrofit 实例的限定符，与默认 baseUrl 的 Retrofit 区分。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AniListRetrofit
