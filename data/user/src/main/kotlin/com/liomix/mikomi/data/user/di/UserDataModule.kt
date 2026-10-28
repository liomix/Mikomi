package com.liomix.mikomi.data.user.di

import com.liomix.mikomi.core.network.bangumi.BangumiNetworkConfigProvider
import com.liomix.mikomi.data.user.UserDataRepository
import com.liomix.mikomi.data.user.internal.BangumiNetworkConfigStore
import com.liomix.mikomi.data.user.internal.UserDataRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UserDataModule {

    @Binds
    internal abstract fun bindUserDataRepository(impl: UserDataRepositoryImpl): UserDataRepository

    /** 网络配置快照：供 OkHttp 拦截器同步读取（实现里用后台协程跟随 DataStore）。 */
    @Binds
    internal abstract fun bindBangumiNetworkConfigProvider(
        impl: BangumiNetworkConfigStore,
    ): BangumiNetworkConfigProvider
}
