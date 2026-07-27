package io.github.mabrur.streamly.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.mabrur.streamly.core.designsystem.component.StreamlyToastHost
import io.github.mabrur.streamly.core.designsystem.component.rememberToastState

@Composable
fun DownloadsRoute(
    onOpenPlayer: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val toastState = rememberToastState()

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is DownloadsEffect.OpenPlayer -> onOpenPlayer(effect.videoId)
                    is DownloadsEffect.ShowToast -> toastState.show(effect.message)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        DownloadsScreen(state = state, onIntent = viewModel::onIntent)
        StreamlyToastHost(
            message = toastState.message,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}
