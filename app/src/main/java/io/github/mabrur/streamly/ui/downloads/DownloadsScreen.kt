package io.github.mabrur.streamly.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.mabrur.streamly.core.designsystem.component.ContentState
import io.github.mabrur.streamly.domain.model.DownloadStatus

@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onIntent: (DownloadsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            text = "Storage used: ${state.storageLabel}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(16.dp),
        )

        ContentState(
            isLoading = state.isLoading,
            error = state.error,
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
            .clickable { onIntent(DownloadsIntent.PlayClicked(item.videoId)) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Offline this falls back to blank rather than failing — the thumbnail is a
        // remote URL and only renders if Coil already cached it from the Home feed.
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(96.dp).aspectRatio(16f / 9f),
        )

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (val status = item.status) {
                        DownloadStatus.Completed -> "Ready to play · ${item.sizeLabel}"
                        DownloadStatus.Queued -> "Queued"
                        DownloadStatus.Failed -> "Failed"
                        DownloadStatus.Removing -> "Removing…"
                        is DownloadStatus.InProgress ->
                            "${status.percent.toInt()}% · ${item.sizeLabel}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { onIntent(DownloadsIntent.RemoveClicked(item.videoId)) },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Remove")
                }
            }

            val status = item.status
            if (status is DownloadStatus.InProgress) {
                LinearProgressIndicator(
                    progress = { status.percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

