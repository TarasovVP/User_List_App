package com.example.userlistapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.core.net.toUri
import androidx.room.Room
import com.example.userlistapp.BuildConfig
import com.example.userlistapp.core.common.DefaultDispatcher
import com.example.userlistapp.core.common.IoDispatcher
import com.example.userlistapp.data.local.RoomUserLocalDataSource
import com.example.userlistapp.data.local.UserDao
import com.example.userlistapp.data.local.UserDatabase
import com.example.userlistapp.data.local.UserLocalDataSource
import com.example.userlistapp.data.preferences.AuthSessionRepositoryImpl
import com.example.userlistapp.data.preferences.LocalAvatarStorage
import com.example.userlistapp.data.preferences.SettingsRepositoryImpl
import com.example.userlistapp.data.preferences.authSessionDataStore
import com.example.userlistapp.data.preferences.settingsDataStore
import com.example.userlistapp.data.remote.AuthApi
import com.example.userlistapp.data.remote.RetrofitUserRemoteDataSource
import com.example.userlistapp.data.remote.UserApi
import com.example.userlistapp.data.remote.UserRemoteDataSource
import com.example.userlistapp.data.repository.UserRepositoryImpl
import com.example.userlistapp.domain.repository.AuthSessionRepository
import com.example.userlistapp.domain.repository.SettingsRepository
import com.example.userlistapp.domain.repository.SyncScheduler
import com.example.userlistapp.domain.repository.UserRepository
import com.example.userlistapp.worker.WorkManagerSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthDataStore

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header("Accept", "application/json").build()
            )
        }
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }.build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun api(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)
    @Provides
    @Singleton
    fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): UserDatabase =
        Room.databaseBuilder(context, UserDatabase::class.java, "users.db")
            .addMigrations(UserDatabase.MIGRATION_1_2).build()

    @Provides
    @Singleton
    fun dao(database: UserDatabase): UserDao = database.userDao()
    @Provides
    @Singleton
    @SettingsDataStore
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    @AuthDataStore
    fun authDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.authSessionDataStore

    @Provides
    @Singleton
    fun avatarStorage(@ApplicationContext context: Context): LocalAvatarStorage =
        LocalAvatarStorage(File(context.filesDir, LocalAvatarStorage.DIRECTORY_NAME)) { uri ->
            context.contentResolver.openInputStream(uri.toUri())
        }

    @Provides
    @Singleton
    fun remote(api: UserApi): UserRemoteDataSource = RetrofitUserRemoteDataSource(api)
    @Provides
    @Singleton
    fun local(database: UserDatabase, dao: UserDao): UserLocalDataSource =
        RoomUserLocalDataSource(database, dao)

    @Provides
    @Singleton
    fun users(
        remote: UserRemoteDataSource,
        local: UserLocalDataSource,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): UserRepository = UserRepositoryImpl(remote, local, dispatcher)

    @Provides
    @Singleton
    fun settings(@SettingsDataStore dataStore: DataStore<Preferences>): SettingsRepository =
        SettingsRepositoryImpl(dataStore)

    @Provides
    @Singleton
    fun authSession(
        @AuthDataStore dataStore: DataStore<Preferences>,
        api: AuthApi,
        avatarStorage: LocalAvatarStorage,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): AuthSessionRepository =
        AuthSessionRepositoryImpl(dataStore, api, avatarStorage, ioDispatcher)

    @Provides
    @Singleton
    fun scheduler(impl: WorkManagerSyncScheduler): SyncScheduler = impl
}
