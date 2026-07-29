package io.github.mabrur.streamly.ui.shorts

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import io.github.mabrur.streamly.core.designsystem.component.ContentState
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyShapes

@Composable
fun ShortsScreen(
    state: ShortsUiState,
    onIntent: (ShortsIntent) -> Unit,
    playerForPage: (Int) -> Player?,
    modifier: Modifier = Modifier,
) {
    ContentState(
        isLoading = state.isLoading,
        error = state.error,
        data = state.shorts,
        isEmpty = { it.isEmpty() },
        modifier = modifier,
        onRetry = { onIntent(ShortsIntent.Retry) },
    ) { shorts ->
        val pagerState = rememberPagerState(pageCount = { shorts.size })

        // settledPage, never currentPage: a half-swipe must not start audio.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .collect { onIntent(ShortsIntent.PageSettled(it)) }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { shorts[it].id },
                // Pinned explicitly. The default would instantiate surfaces for off-screen
                // pages and quietly break the "at most two players" guarantee.
                beyondViewportPageCount = 0,
            ) { page ->
                ShortPage(
                    short = shorts[page],
                    player = playerForPage(page),
                    isSettled = page == state.settledIndex,
                )
            }

            // Outside the pager, like the design has it: one rail for the whole feed
            // rather than a copy riding on every page.
            DotRail(
                count = shorts.size,
                activeIndex = state.settledIndex,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
            )
        }
    }
}

/**
 * The design's page indicator. Read-only, like [PlayingBadge]: a 7dp dot is a quarter of
 * the minimum touch target, and the pager is already driven by the swipe it describes.
 */
@Composable
private fun DotRail(
    count: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == activeIndex) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.35f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun ShortPage(
    short: ShortUi,
    player: Player?,
    isSettled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Only the settled page gets a surface. An unsettled page showing a surface would
        // hold a decoder open for a page the user is not watching.
        if (player != null && isSettled) {
            val presentationState = rememberPresentationState(player)

            // TEXTURE_VIEW, not SURFACE_VIEW. A SurfaceView draws
            // in its own layer behind the window and depends on the view hierarchy punching
            // a transparent hole for it. VerticalPager composites every page through a
            // graphicsLayer to offset it, so the hole is never punched: audio plays and
            // frames decode, but the page paints over the video and the user sees black.
            // A TextureView draws in the normal hierarchy and survives the transform.
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                // The catalog's shorts are 16:9 sources shown in a 9:20 page. Without a
                // content scale the surface simply fills the box and the picture is
                // visibly stretched. Crop matches what a shorts feed is expected to do.
                modifier = Modifier
                    .fillMaxSize()
                    .resizeWithContentScale(
                        contentScale = ContentScale.Crop,
                        sourceSizeDp = presentationState.videoSizeDp,
                    ),
            )

            // Same shutter as the Player screen: black until the first frame actually
            // renders, so a slow page reads as buffering rather than as a broken page.
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

        // White-on-video is unreadable over a bright frame — the catalog's first short is a
        // pale sky and the caption vanishes into it. A scrim behind the text costs nothing
        // and makes the overlay legible whatever is playing underneath.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                    ),
                ),
        )

        Text(
            text = "SHORTS",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
        )

        if (isSettled) {
            PlayingBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
            )
        }

        // Stubs, explicitly permitted by the PRD. Bottom-aligned, per the design and to
        // clear the dot rail, which the design puts at the vertical centre. The caption
        // beside it already reserves the width this needs.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RailButton(glyph = "\u2665", label = short.likeLabel)
            RailButton(glyph = "\u27a4", label = "Share")
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 72.dp, bottom = 16.dp),
        ) {
            Text(
                text = "@${short.channelName.replace(" ", "").lowercase()}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = short.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The design's live indicator. Read-only: it reports what the pool is doing, and cannot
 * start or stop playback.
 */
@Composable
private fun PlayingBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playing")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot",
    )

    Row(
        modifier = modifier
            .clip(StreamlyShapes.Pill)
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(StreamlyColors.LiveDot.copy(alpha = dotAlpha)),
        )
        Text(
            text = "Playing",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun RailButton(
    glyph: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = glyph, color = Color.White)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
