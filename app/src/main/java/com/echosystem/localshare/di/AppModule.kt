package com.echosystem.localshare.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.echosystem.localshare.database.DeviceDao
import com.echosystem.localshare.database.EchoDatabase
import com.echosystem.localshare.database.PairingKeyDao
import com.echosystem.localshare.database.TransferHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings") }
        )
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EchoDatabase {
        return Room.databaseBuilder(
            context,
            EchoDatabase::class.java,
            "echo_system.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideDeviceDao(database: EchoDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun provideTransferHistoryDao(database: EchoDatabase): TransferHistoryDao = database.transferHistoryDao()

    @Provides
    fun providePairingKeyDao(database: EchoDatabase): PairingKeyDao = database.pairingKeyDao()

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
