package io.github.mabrur.streamly.ui.shorts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ShortsRoute(
    modifier: Modifier = Modifier,
    viewModel: ShortsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Leaving Shorts — to Home, to another tab, or to the background — must silence the
    // pool. This is the audio-bleed guarantee.
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }

    ShortsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        playerForPage = viewModel::playerForPage,
        modifier = modifier,
    )
}
