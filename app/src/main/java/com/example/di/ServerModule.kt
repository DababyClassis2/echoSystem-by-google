package com.example.di

import dagger.Module
import dagger.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ServerModule {
    // References to routes and repositories are handled via constructor injection (@Inject)
}
