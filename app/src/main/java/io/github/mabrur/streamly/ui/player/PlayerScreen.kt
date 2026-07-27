package io.github.mabrur.streamly.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.state.rememberPresentationState
import io.github.mabrur.streamly.core.designsystem.component.ContentState
import io.github.mabrur.streamly.core.designsystem.component.VideoCard
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyShapes

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    player: Player,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ContentState(
        isLoading = state.isLoading,
        error = state.error,
        data = state.video,
        modifier = modifier,
        onRetry = { onIntent(PlayerIntent.Retry) },
    ) { video ->
        Column(modifier = Modifier.fillMaxSize()) {
            VideoStage(player = player)

            Text(
                text = video.title,
                style = MaterialTheme.typography.titleLarge,
                color = StreamlyColors.Ink,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )

            // Only shown when the video genuinely has a completed download, so it is a
            // statement of fact rather than decoration.
            if (state.isPlayingOffline) {
                Text(
                    text = "Downloaded · playing offline",
                    style = MaterialTheme.typography.labelMedium,
                    color = StreamlyColors.Ready,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            ChannelRow(
                channelName = video.channelName,
                metaLine = video.metaLine,
                isSubscribed = state.isSubscribed,
                onSubscribe = { onIntent(PlayerIntent.SubscribeToggled) },
            )

            ActionRow(state = state, onIntent = onIntent)

            PlayerControls(player = player)

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.related, key = { it.id }) { related ->
                    VideoCard(
                        title = related.title,
                        channelName = related.channelName,
                        thumbnailUrl = related.thumbnailUrl,
                        metaLine = related.metaLine,
                        durationLabel = related.durationLabel,
                        onClick = { onIntent(PlayerIntent.RelatedClicked(related.id)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channelName: String,
    metaLine: String,
    isSubscribed: Boolean,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(StreamlyColors.AvatarPlaceholder),
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = channelName,
                style = MaterialTheme.typography.labelLarge,
                color = StreamlyColors.Ink,
            )
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = StreamlyColors.Muted,
            )
        }
        Box(modifier = Modifier.weight(1f))
        Text(
            text = if (isSubscribed) "Subscribed" else "Subscribe",
            style = MaterialTheme.typography.labelMedium,
            color = if (isSubscribed) StreamlyColors.Ink else StreamlyColors.Surface,
            modifier = Modifier
                .clip(StreamlyShapes.Pill)
                .background(
                    if (isSubscribed) StreamlyColors.NeutralFill else StreamlyColors.Accent,
                )
                .clickable(onClick = onSubscribe)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ActionRow(
    state: PlayerUiState,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            label = if (state.isLiked) "Liked" else "Like",
            isActive = state.isLiked,
            onClick = { onIntent(PlayerIntent.LikeToggled) },
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            label = "Share",
            isActive = false,
            onClick = { onIntent(PlayerIntent.ShareClicked) },
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            label = state.downloadLabel,
            isActive = state.isPlayingOffline,
            onClick = { onIntent(PlayerIntent.DownloadClicked) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (isActive) StreamlyColors.Surface else StreamlyColors.Ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(StreamlyShapes.Button)
            .background(if (isActive) StreamlyColors.Accent else StreamlyColors.NeutralFill)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/**
 * 16:9 video stage.
 *
 * [rememberPresentationState] drives the shutter: `coverSurface` stays true until the
 * first frame is rendered, so the spinner is a genuine buffering indicator rather than
 * a timed guess. This is the PRD's required "visible buffering state".
 */
@Composable
private fun VideoStage(
    player: Player,
    modifier: Modifier = Modifier,
) {
    val presentationState = rememberPresentationState(player)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(StreamlyColors.VideoBackground),
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.fillMaxSize(),
        )

        if (presentationState.coverSurface) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StreamlyColors.VideoBackground),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
