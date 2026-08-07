package com.nousresearch.hermes.di

import com.nousresearch.hermes.network.HermesRestClient
import com.nousresearch.hermes.network.DashboardAuthClient
import com.nousresearch.hermes.data.BackendSaver
import com.nousresearch.hermes.data.BackendRegistry
import com.nousresearch.hermes.data.SessionCredentialStore
import com.nousresearch.hermes.security.SecureTokenStore
import com.nousresearch.hermes.protocol.HermesGatewayClient
import com.nousresearch.hermes.protocol.OkHttpHermesGatewayClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds
    @Singleton
    abstract fun bindGatewayClient(implementation: OkHttpHermesGatewayClient): HermesGatewayClient

    @Binds
    @Singleton
    abstract fun bindBackendSaver(implementation: BackendRegistry): BackendSaver

    @Binds
    @Singleton
    abstract fun bindSessionCredentialStore(implementation: SecureTokenStore): SessionCredentialStore
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
    }

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun restClient(
        client: OkHttpClient,
        json: Json,
        credentials: SessionCredentialStore,
    ): HermesRestClient = HermesRestClient(client, json, credentials)

    @Provides
    @Singleton
    fun dashboardAuthClient(client: OkHttpClient, json: Json): DashboardAuthClient = DashboardAuthClient(client, json)
}
