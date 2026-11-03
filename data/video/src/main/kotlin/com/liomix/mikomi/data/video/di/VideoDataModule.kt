package com.liomix.mikomi.data.video.di

import com.liomix.mikomi.data.video.remote.vdoplg.store.VdoPlgRepository
import com.liomix.mikomi.data.video.remote.vdoplg.store.VdoPlgRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `data:video` 的 Hilt 绑定。
 *
 * 这里只绑「大源仓库」，不绑任何跨源门面：本模块没有统一的 `VideoRepository` 出口，
 * 播放链路是 `VdoPlgRepository`（装了哪些插件）→ `VdoPlgEngine`（搜索/剧集/探流）
 * → `VdoPlgStreamPlanner`（出流）。将来加 CYK / RPB，各绑各自的大源仓库即可。
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class VideoDataModule {

    /**
     * 插件源仓库（**当前唯一接入的大源**，见 `VdoSourceFamily.PLUGIN`）。
     *
     * 必须是单例：插件测试页与商店页各持一个实例的话，
     * 两边看到的「已安装」会各自为政——装完回到测试页还是旧的。
     */
    @Binds
    @Singleton
    internal abstract fun bindVdoPlgRepository(impl: VdoPlgRepositoryImpl): VdoPlgRepository
}
