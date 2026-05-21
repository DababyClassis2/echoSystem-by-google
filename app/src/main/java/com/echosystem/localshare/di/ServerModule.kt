package com.echosystem.localshare.di

import com.echosystem.localshare.repository.DeviceRegistry
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.server.ServerEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServerModule {
    // Already defined as @Singleton and @Inject in classes, but if we had interfaces:
    // @Provides @Singleton fun provideDeviceRegistry(): DeviceRegistry = DeviceRegistry()
}
