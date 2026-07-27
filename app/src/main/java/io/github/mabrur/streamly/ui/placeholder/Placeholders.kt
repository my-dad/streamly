package io.github.mabrur.streamly.ui.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.mabrur.streamly.core.designsystem.component.ContentState

/**
 * Temporary screen bodies for the navigation shell.
 * Each later plan replaces exactly one of these with the real screen.
 */
@Composable
fun PlaceholderScreen(
    label: String,
    modifier: Modifier = Modifier,
) {
    ContentState(
        isLoading = false,
        error = null,
        data = label,
        modifier = modifier,
        isEmpty = { it.isEmpty() },
    ) { value ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
