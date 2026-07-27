package io.github.mabrur.streamly.domain.model

data class Video(
    val id: String,
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String,
    val thumbnailUrl: String,
    val hlsUrl: String,
    val durationMs: Long,
    val viewCount: Long,
    val publishedAtEpochSeconds: Long,
    val category: String,
)
