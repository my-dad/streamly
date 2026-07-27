package io.github.mabrur.streamly.ui.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
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
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.fillMaxSize(),
            )
        }

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
