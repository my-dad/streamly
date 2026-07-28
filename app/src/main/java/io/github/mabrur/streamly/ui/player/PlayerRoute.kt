package io.github.mabrur.streamly.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mabrur.streamly.core.designsystem.component.StreamlyToastHost
import io.github.mabrur.streamly.core.designsystem.component.rememberToastState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun PlayerRoute(
    videoId: String,
    windowSizeClass: WindowSizeClass,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val toastState = rememberToastState()

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
                    PlayerEffect.DownloadStarted -> toastState.show("Download started")
                    PlayerEffect.DownloadFailed ->
                        toastState.show("Couldn't start that download")
                    PlayerEffect.LinkCopied -> toastState.show("Link copied")
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PlayerScreen(
            state = state,
            player = viewModel.player,
            windowSizeClass = windowSizeClass,
            onIntent = viewModel::onIntent,
        )
        StreamlyToastHost(
            message = toastState.message,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}
