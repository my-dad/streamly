package io.github.mabrur.streamly.ui.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import io.github.mabrur.streamly.core.designsystem.component.ContentState

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

            // TEXTURE_VIEW, not SURFACE_VIEW — unlike the Player screen. A SurfaceView draws
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = short.channelName,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                text = short.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp),
            )
            // Like / comment / share are stubs, explicitly permitted by the PRD.
            Text(
                text = "♥ ${short.likeLabel}   💬 ${short.commentLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
