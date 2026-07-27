package io.github.mabrur.streamly.data.remote.mapper

import io.github.mabrur.streamly.data.remote.dto.CatalogDto
import io.github.mabrur.streamly.data.remote.dto.ProfileDto
import io.github.mabrur.streamly.data.remote.dto.ShortDto
import io.github.mabrur.streamly.data.remote.dto.VideoDto
import io.github.mabrur.streamly.domain.model.Category
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video

internal fun VideoDto.toDomain(): Video = Video(
    id = id,
    title = title,
    channelName = channelName,
    channelAvatarUrl = channelAvatarUrl,
    thumbnailUrl = thumbnailUrl,
    hlsUrl = hlsUrl,
    durationMs = durationMs,
    viewCount = viewCount,
    publishedAtEpochSeconds = publishedAtEpochSeconds,
    category = category,
)

internal fun ShortDto.toDomain(): Short = Short(
    id = id,
    title = title,
    channelName = channelName,
    hlsUrl = hlsUrl,
    likeCount = likeCount,
    commentCount = commentCount,
)

internal fun ProfileDto.toDomain(): UserProfile = UserProfile(
    name = name,
    email = email,
    avatarUrl = avatarUrl,
)

internal fun CatalogDto.toHomeFeed(): HomeFeed = HomeFeed(
    categories = categories.map(::Category),
    videos = videos.map(VideoDto::toDomain),
)

internal fun CatalogDto.toShorts(): List<Short> = shorts.map(ShortDto::toDomain)

internal fun CatalogDto.toProfile(): UserProfile = profile.toDomain()
