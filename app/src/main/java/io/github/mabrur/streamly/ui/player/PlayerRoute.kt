package io.github.mabrur.streamly.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun PlayerRoute(
    videoId: String,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    // The id comes from the route key, not SavedStateHandle — Nav3 does not populate
    // that from route keys. The ViewModel ignores a repeat Load for the same id, so
    // this is safe across recomposition and configuration change.
    LaunchedEffect(viewModel, videoId) {
        viewModel.onIntent(PlayerIntent.Load(videoId))
    }

    // Pause on onStop, resume on return. Release is NOT here — it belongs to
    // onCleared(), so it happens exactly once when the NavEntry is disposed.
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose {
            viewModel.onStop()
        }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is PlayerEffect.OpenVideo -> onOpenVideo(effect.videoId)
                    // Without this the Download button is silent: the work happens in the
                    // background and the screen looks like it ignored the tap.
                    PlayerEffect.DownloadStarted ->
                        snackbarHostState.showSnackbar("Download started")
                    PlayerEffect.DownloadFailed ->
                        snackbarHostState.showSnackbar("Couldn't start that download")
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PlayerScreen(
            state = state,
            player = viewModel.player,
            onIntent = viewModel::onIntent,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
