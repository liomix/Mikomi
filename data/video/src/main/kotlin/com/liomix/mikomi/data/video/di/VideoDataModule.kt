package com.liomix.mikomi.data.video.di

import com.liomix.mikomi.data.video.VideoRepository
import com.liomix.mikomi.data.video.internal.VideoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VideoDataModule {

    @Binds
    internal abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository
}
