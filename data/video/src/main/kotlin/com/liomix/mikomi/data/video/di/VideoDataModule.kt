package com.liomix.mikomi.data.video.di

import com.liomix.mikomi.data.video.VideoRepository
import com.liomix.mikomi.data.video.internal.VideoRepositoryImpl
import com.liomix.mikomi.data.video.remote.vdoplg.store.VdoPlgRepository
import com.liomix.mikomi.data.video.remote.vdoplg.store.VdoPlgRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VideoDataModule {

    @Binds
    internal abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    /**
     * 规则仓库必须是单例：插件源页与商店页各持一个实例的话，
     * 两边看到的「已安装」会各自为政——装完回到插件源页还是旧的。
     */
    @Binds
    @Singleton
    internal abstract fun bindVdoPlgRepository(impl: VdoPlgRepositoryImpl): VdoPlgRepository
}
