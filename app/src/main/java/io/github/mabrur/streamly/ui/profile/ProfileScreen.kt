package io.github.mabrur.streamly.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import io.github.mabrur.streamly.core.designsystem.component.ContentState
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyShapes

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ContentState(
        isLoading = state.isLoading,
        error = state.error,
        data = state.profile,
        modifier = modifier,
        onRetry = { onIntent(ProfileIntent.Retry) },
    ) { profile ->
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StreamlyColors.Accent)
                    .statusBarsPadding()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f)),
                )
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = profile.email.ifEmpty { "Signed in as guest" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }

            // Shallow links, explicitly permitted by the PRD. They are tappable so the
            // toast can say so, rather than looking broken when nothing happens.
            listOf("Downloads", "Watch history", "Settings").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = StreamlyColors.Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StreamlyColors.Surface)
                        .clickable { onIntent(ProfileIntent.ShallowLinkClicked(label)) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                )
                HorizontalDivider(color = StreamlyColors.Divider)
            }

            Text(
                text = "Sign out",
                style = MaterialTheme.typography.bodyLarge,
                color = StreamlyColors.Danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StreamlyColors.Surface)
                    .clickable { onIntent(ProfileIntent.SignOutClicked) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }

    // Screen 07: a dialog gating sign-out, not a route — see docs/decisions.md D-004.
    if (state.showSignOutDialog) {
        SignOutDialog(
            onConfirm = { onIntent(ProfileIntent.SignOutConfirmed) },
            onDismiss = { onIntent(ProfileIntent.SignOutDismissed) },
        )
    }
}

@Composable
private fun SignOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = StreamlyShapes.Dialog, color = StreamlyColors.Surface) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                Text(
                    text = "Sign out?",
                    style = MaterialTheme.typography.titleLarge,
                    color = StreamlyColors.Ink,
                )
                Text(
                    text = "You'll need to sign in again to see your downloads and history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StreamlyColors.Muted,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogButton(
                        label = "Cancel",
                        container = StreamlyColors.NeutralFill,
                        content = StreamlyColors.Ink,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    DialogButton(
                        label = "Sign out",
                        container = StreamlyColors.Danger,
                        content = StreamlyColors.Surface,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(StreamlyShapes.Pill)
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
