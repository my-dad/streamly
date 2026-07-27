package io.github.mabrur.streamly.domain.model

data class Short(
    val id: String,
    val title: String,
    val channelName: String,
    val hlsUrl: String,
    val likeCount: Long,
    val commentCount: Long,
)
