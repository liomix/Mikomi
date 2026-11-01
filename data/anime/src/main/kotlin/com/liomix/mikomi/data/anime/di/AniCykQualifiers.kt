package com.liomix.mikomi.data.anime.di

import javax.inject.Qualifier

/** 次元库（AniCyk）专用 OkHttp 实例：超时更短，且只访问明文节点白名单。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AniCykHttp
