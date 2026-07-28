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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import io.github.mabrur.streamly.core.designsystem.component.ContentState
import io.github.mabrur.streamly.core.designsystem.component.VideoCard
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyShapes
import io.github.mabrur.streamly.ui.home.VideoUi

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    player: Player,
    windowSizeClass: WindowSizeClass,
    onBack: () -> Unit,
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
        val activity = LocalActivity.current
        val isFullscreen = windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact

        // The button rotates the device rather than inventing a second fullscreen state:
        // landscape already *is* fullscreen, so one concept covers both entry paths.
        val onToggleFullscreen = {
            activity?.requestedOrientation = if (isFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            }
        }

        // Without this the lock outlives the screen and every other tab inherits it.
        DisposableEffect(activity) {
            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        // Height, not width, is what runs out: a phone in landscape is Compact-height, and
        // there is no arrangement of a 16:9 stage plus a details column that leaves either
        // one usable. Landscape means fullscreen, which is what every video app does and
        // what the details are worth giving up for.
        if (isFullscreen) {
            FullscreenStage(
                player = player,
                onBack = onBack,
                onToggleFullscreen = onToggleFullscreen,
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Outside the list on purpose. Scrolling the stage away would leave the
                // video playing where nobody can see it — and take the controls with it.
                VideoStage(
                    player = player,
                    onBack = onBack,
                    isFullscreen = false,
                    onToggleFullscreen = onToggleFullscreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                // Everything below it is one list, so the title and actions scroll with
                // the up-next items instead of pinning above them and squeezing the list
                // into whatever height was left.
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    details(video, state, onIntent)
                }
            }
        }
    }
}

/**
 * Landscape: the video and nothing else, with the system bars out of the way. The stage
 * already carries the controls, so this only has to hand it the whole window.
 */
@Composable
private fun FullscreenStage(
    player: Player,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val window = LocalActivity.current?.window

    // Scoped to this branch: leaving landscape, or the screen, disposes it and puts the
    // bars back. Nothing else has to remember to undo it.
    DisposableEffect(view, window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    VideoStage(
        player = player,
        onBack = onBack,
        isFullscreen = true,
        onToggleFullscreen = onToggleFullscreen,
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    )
}

private fun LazyListScope.details(
    video: VideoUi,
    state: PlayerUiState,
    onIntent: (PlayerIntent) -> Unit,
) {
    item {
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            color = StreamlyColors.Ink,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }

    // Only shown when the video genuinely has a completed download, so it is a
    // statement of fact rather than decoration.
    if (state.isPlayingOffline) {
        item {
            Text(
                text = "Downloaded · playing offline",
                style = MaterialTheme.typography.labelMedium,
                color = StreamlyColors.Ready,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    item {
        ChannelRow(
            channelName = video.channelName,
            metaLine = video.metaLine,
            isSubscribed = state.isSubscribed,
            onSubscribe = { onIntent(PlayerIntent.SubscribeToggled) },
        )
    }

    item { ActionRow(state = state, onIntent = onIntent) }

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
 * Video stage, with the controls on it. The caller sizes it — 16:9 across the width in
 * portrait, the whole window in landscape — so the surface must fit itself to whatever
 * box it is handed.
 *
 * [rememberPresentationState] drives the shutter: `coverSurface` stays true until the
 * first frame is rendered, so the spinner is a genuine buffering indicator rather than
 * a timed guess. This is the PRD's required "visible buffering state".
 */
@Composable
private fun VideoStage(
    player: Player,
    onBack: () -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentationState = rememberPresentationState(player)

    Box(
        modifier = modifier.background(StreamlyColors.VideoBackground),
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            // Fit, not Crop: the landscape pane is not 16:9, and cropping a long-form
            // video to fill it would cut the picture. Letterboxing into the black stage
            // is what a player does. A no-op in portrait, where the box already matches.
            modifier = Modifier
                .fillMaxSize()
                .resizeWithContentScale(
                    contentScale = ContentScale.Fit,
                    sourceSizeDp = presentationState.videoSizeDp,
                ),
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

        // Last, so it sits above the shutter — reaching Back during a slow buffer should
        // not require waiting for the first frame.
        PlayerControls(
            player = player,
            onBack = onBack,
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen,
        )
    }
}
