package com.example.userlistapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.example.userlistapp.BuildConfig
import com.example.userlistapp.core.common.DefaultDispatcher
import com.example.userlistapp.data.local.RoomUserLocalDataSource
import com.example.userlistapp.data.local.UserDao
import com.example.userlistapp.data.local.UserDatabase
import com.example.userlistapp.data.local.UserLocalDataSource
import com.example.userlistapp.data.preferences.SettingsRepositoryImpl
import com.example.userlistapp.data.preferences.settingsDataStore
import com.example.userlistapp.data.remote.RetrofitUserRemoteDataSource
import com.example.userlistapp.data.remote.UserApi
import com.example.userlistapp.data.remote.AuthApi
import com.example.userlistapp.data.preferences.AuthSessionRepositoryImpl
import com.example.userlistapp.data.remote.UserRemoteDataSource
import com.example.userlistapp.data.repository.UserRepositoryImpl
import com.example.userlistapp.domain.repository.SettingsRepository
import com.example.userlistapp.domain.repository.SyncScheduler
import com.example.userlistapp.domain.repository.UserRepository
import com.example.userlistapp.domain.repository.AuthSessionRepository
import com.example.userlistapp.worker.WorkManagerSyncScheduler
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @DefaultDispatcher fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
    @Provides @Singleton fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Accept", "application/json").build()) }
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }.build()

    @Provides @Singleton fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton fun api(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)
    @Provides @Singleton fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides @Singleton fun database(@ApplicationContext context: Context): UserDatabase =
        Room.databaseBuilder(context, UserDatabase::class.java, "users.db")
            .addMigrations(UserDatabase.MIGRATION_1_2).build()

    @Provides @Singleton fun dao(database: UserDatabase): UserDao = database.userDao()
    @Provides @Singleton fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore

    @Provides @Singleton fun remote(api: UserApi): UserRemoteDataSource = RetrofitUserRemoteDataSource(api)
    @Provides @Singleton fun local(database: UserDatabase, dao: UserDao): UserLocalDataSource = RoomUserLocalDataSource(database, dao)
    @Provides @Singleton fun users(remote: UserRemoteDataSource, local: UserLocalDataSource, @DefaultDispatcher dispatcher: CoroutineDispatcher): UserRepository = UserRepositoryImpl(remote, local, dispatcher)
    @Provides @Singleton fun settings(dataStore: DataStore<Preferences>): SettingsRepository = SettingsRepositoryImpl(dataStore)
    @Provides @Singleton fun authSession(dataStore: DataStore<Preferences>, api: AuthApi): AuthSessionRepository =
        AuthSessionRepositoryImpl(dataStore, api)
    @Provides @Singleton fun scheduler(impl: WorkManagerSyncScheduler): SyncScheduler = impl
}
