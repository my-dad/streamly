package io.github.mabrur.streamly.ui.home

import androidx.compose.runtime.Immutable
import io.github.mabrur.streamly.core.designsystem.format.formatDuration
import io.github.mabrur.streamly.core.designsystem.format.formatRelativeAge
import io.github.mabrur.streamly.core.designsystem.format.formatViewCount
import io.github.mabrur.streamly.domain.model.Video

@Immutable
data class VideoUi(
    val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val metaLine: String,
    val durationLabel: String,
)

fun Video.toUi(nowSeconds: Long): VideoUi = VideoUi(
    id = id,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    metaLine = "${formatViewCount(viewCount)} · ${
        formatRelativeAge(publishedAtEpochSeconds, nowSeconds)
    }",
    durationLabel = formatDuration(durationMs),
)
