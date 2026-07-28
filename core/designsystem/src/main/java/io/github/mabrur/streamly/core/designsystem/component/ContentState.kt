package io.github.mabrur.streamly.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mabrur.streamly.core.designsystem.R
import io.github.mabrur.streamly.core.designsystem.error.bodyResId
import io.github.mabrur.streamly.core.designsystem.error.isRetryable
import io.github.mabrur.streamly.core.designsystem.error.titleResId
import io.github.mabrur.streamly.domain.error.AppError

/**
 * Shared loading / empty / error / content wrapper.
 *
 * Precedence is deliberate and fixed: error wins over loading, loading over
 * empty, empty over content. A screen that is refreshing after a failure shows
 * the error rather than flickering a spinner over stale data.
 */
@Composable
fun <T> ContentState(
    isLoading: Boolean,
    data: T?,
    modifier: Modifier = Modifier,
    /** Omit on screens with no failure to render — see D-025. */
    error: AppError? = null,
    isEmpty: (T) -> Boolean = { false },
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when {
        error != null -> MessageState(
            modifier = modifier,
            title = stringResource(error.titleResId()),
            body = stringResource(error.bodyResId()),
            actionLabel = if (error.isRetryable() && onRetry != null) {
                stringResource(R.string.action_retry)
            } else {
                null
            },
            onAction = onRetry,
        )

        isLoading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        data == null || isEmpty(data) -> MessageState(
            modifier = modifier,
            title = stringResource(R.string.empty_title),
            body = stringResource(R.string.empty_body),
            actionLabel = null,
            onAction = null,
        )

        else -> content(data)
    }
}

@Composable
private fun MessageState(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}
