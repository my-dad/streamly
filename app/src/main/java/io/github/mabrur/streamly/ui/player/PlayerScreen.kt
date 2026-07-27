package io.github.mabrur.streamly.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.state.rememberPresentationState
import io.github.mabrur.streamly.core.designsystem.component.ContentState
import io.github.mabrur.streamly.core.designsystem.component.VideoCard

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
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                text = "${video.channelName} · ${video.metaLine}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Button(
                onClick = { onIntent(PlayerIntent.DownloadClicked) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Download")
            }

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
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
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
            .background(Color.Black),
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
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
