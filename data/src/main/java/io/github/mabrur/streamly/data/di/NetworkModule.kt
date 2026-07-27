package io.github.mabrur.streamly.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mabrur.streamly.data.remote.CatalogApi
import io.github.mabrur.streamly.data.remote.mockCatalogEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(
        @ApplicationContext context: Context,
    ): HttpClient {
        val engine = mockCatalogEngine(
            readAsset = {
                context.assets.open("catalog.json").bufferedReader().use { it.readText() }
            },
        )
        return HttpClient(engine) {
            // Required: Ktor does not throw on non-2xx by default, so without this
            // the injected failures would reach the deserializer and be misreported
            // as AppError.Unknown instead of AppError.Network.
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @Provides
    @Singleton
    fun provideCatalogApi(client: HttpClient): CatalogApi = CatalogApi(client)
}
