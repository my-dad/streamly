package io.github.mabrur.streamly.ui.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.mabrur.streamly.core.designsystem.component.ContentState
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyShapes
import io.github.mabrur.streamly.domain.model.DownloadStatus

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onIntent: (DownloadsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(StreamlyColors.Surface)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineMedium,
                color = StreamlyColors.Ink,
            )
            Text(
                text = state.storageLabel,
                style = MaterialTheme.typography.bodySmall,
                color = StreamlyColors.Muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        HorizontalDivider(color = StreamlyColors.Divider)

        // No `error` argument: this screen reads DownloadManager on the device, which has
        // no failure this screen can render. See D-025.
        ContentState(
            isLoading = state.isLoading,
            data = state.items,
            isEmpty = { it.isEmpty() },
        ) { items ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = items, key = { it.videoId }) { item ->
                    DownloadRow(item = item, onIntent = onIntent)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadRowUi,
    onIntent: (DownloadsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Stays tappable while downloading so the toast can say why nothing opened.
            .clickable { onIntent(DownloadsIntent.PlayClicked(item.videoId)) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(16f / 9f)
                .clip(StreamlyShapes.SmallThumbnail)
                .background(
                    Brush.linearGradient(
                        listOf(
                            StreamlyColors.PlaceholderStart,
                            StreamlyColors.PlaceholderEnd,
                        ),
                    ),
                ),
        ) {
            // Offline this stays on the gradient — the thumbnail is a remote URL and only
            // renders if Coil already cached it from the Home feed.
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = StreamlyColors.Ink,
            )
            DownloadStatusLine(item = item)
        }

        IconButton(onClick = { onIntent(DownloadsIntent.RemoveClicked(item.videoId)) }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove ${item.title}",
                tint = StreamlyColors.Muted,
            )
        }
    }
}

@Composable
private fun DownloadStatusLine(
    item: DownloadRowUi,
    modifier: Modifier = Modifier,
) {
    when (val status = item.status) {
        DownloadStatus.Completed -> Row(
            modifier = modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(StreamlyColors.Ready),
            )
            Text(
                text = "Ready to play · ${item.sizeLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = StreamlyColors.Ready,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        is DownloadStatus.InProgress -> Column(modifier = modifier.padding(top = 6.dp)) {
            // Animated so real progress reads as motion rather than as a jumping bar.
            val fraction by animateFloatAsState(
                targetValue = status.percent / 100f,
                label = "progress",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(StreamlyShapes.Pill)
                    .background(StreamlyColors.ChipFill),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(StreamlyShapes.Pill)
                        .background(StreamlyColors.Accent),
                )
            }
            Text(
                text = "${status.percent.toInt()}% · ${item.sizeLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = StreamlyColors.Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        else -> Text(
            text = when (status) {
                DownloadStatus.Queued -> "Queued"
                DownloadStatus.Failed -> "Failed"
                DownloadStatus.Removing -> "Removing…"
                else -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (status == DownloadStatus.Failed) {
                StreamlyColors.Danger
            } else {
                StreamlyColors.Muted
            },
            modifier = modifier.padding(top = 6.dp),
        )
    }
}
