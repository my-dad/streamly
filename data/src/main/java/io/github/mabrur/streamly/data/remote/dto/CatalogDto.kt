package io.github.mabrur.streamly.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogDto(
    val categories: List<String>,
    val videos: List<VideoDto>,
    val shorts: List<ShortDto>,
    val profile: ProfileDto,
)

@Serializable
internal data class VideoDto(
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

@Serializable
internal data class ShortDto(
    val id: String,
    val title: String,
    val channelName: String,
    val hlsUrl: String,
    val likeCount: Long,
    val commentCount: Long,
)

@Serializable
internal data class ProfileDto(
    val name: String,
    val email: String,
    val avatarUrl: String,
)
