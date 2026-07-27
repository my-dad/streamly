package io.github.mabrur.streamly.core.player.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mabrur.streamly.core.player.ExoPlayerHolder
import io.github.mabrur.streamly.core.player.PlayerHolder
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    /**
     * The single media cache, shared by playback and downloads.
     *
     * [NoOpCacheEvictor] is required: a download cache must never evict, or a
     * "Ready to play" item would silently stop working offline.
     *
     * Exactly one [SimpleCache] instance may exist per directory per process —
     * constructing a second one against the same folder throws. That is why this is
     * a @Singleton and why the Downloads plan injects it rather than building its own.
     */
    @Provides
    @Singleton
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache = SimpleCache(
        File(context.filesDir, "media_downloads"),
        NoOpCacheEvictor(),
        databaseProvider,
    )

    /** Network-facing factory. Downloads write through this; playback reads past it. */
    @Provides
    @Singleton
    @Named("upstream")
    fun provideUpstreamDataSourceFactory(
        @ApplicationContext context: Context,
    ): DataSource.Factory = DefaultDataSource.Factory(context)

    /**
     * Read-through cache factory used for playback.
     *
     * `setCacheWriteDataSinkFactory(null)` makes playback read-only, so streaming a video
     * never partially populates the download cache and makes a half-downloaded item look
     * complete.
     */
    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        cache: SimpleCache,
        @Named("upstream") upstream: DataSource.Factory,
    ): DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * HLS support comes from having `media3-exoplayer-hls` on the classpath —
     * [DefaultMediaSourceFactory] then resolves `.m3u8` automatically. Track selection
     * is left at its defaults so adaptive bitrate keeps working.
     */
    @Provides
    @Singleton
    fun provideMediaSourceFactory(
        @ApplicationContext context: Context,
        dataSourceFactory: DataSource.Factory,
    ): MediaSource.Factory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSourceFactory)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerBindingModule {

    /**
     * Intentionally NOT @Singleton. Each PlayerViewModel gets its own holder, so
     * releasing one when its NavEntry is popped cannot poison the next Player screen.
     */
    @Binds
    abstract fun bindPlayerHolder(impl: ExoPlayerHolder): PlayerHolder
}
