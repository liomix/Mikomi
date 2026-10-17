package com.liomix.mikomi.data.user.di

import com.liomix.mikomi.data.user.UserDataRepository
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
}
