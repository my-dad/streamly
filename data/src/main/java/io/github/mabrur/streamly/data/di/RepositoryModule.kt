package io.github.mabrur.streamly.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.mabrur.streamly.data.repository.CatalogRepositoryImpl
import io.github.mabrur.streamly.data.repository.SessionRepositoryImpl
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import io.github.mabrur.streamly.domain.repository.SessionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository
}
