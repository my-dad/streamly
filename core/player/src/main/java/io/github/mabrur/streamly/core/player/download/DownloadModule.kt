package io.github.mabrur.streamly.core.player.download

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mabrur.streamly.domain.repository.DownloadRepository
import java.util.concurrent.Executors
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    /**
     * Bound to the SimpleCache singleton created in PlayerModule — the same instance
     * playback reads from. Two caches here would mean downloads land somewhere playback
     * never looks, and offline playback would fail while everything looked fine online.
     *
     * The upstream (network) factory is used deliberately: downloads must WRITE, so the
     * read-only CacheDataSource.Factory would be wrong here.
     */
    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        cache: SimpleCache,
        @Named("upstream") upstreamFactory: DataSource.Factory,
    ): DownloadManager = DownloadManager(
        context,
        databaseProvider,
        cache,
        upstreamFactory,
        Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS),
    ).apply {
        maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
    }

    private const val MAX_PARALLEL_DOWNLOADS = 2
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadBindingModule {

    /** `:app` injects the domain interface; the Media3 implementation never leaves here. */
    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository
}
