package io.github.mabrur.streamly.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.mabrur.streamly.core.designsystem.component.ContentState

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
        modifier = modifier.statusBarsPadding(),
        onRetry = { onIntent(ProfileIntent.Retry) },
    ) { profile ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = profile.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
            )
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = profile.email.ifEmpty { "Signed in as guest" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            // Shallow links, explicitly permitted by the PRD. They are tappable so the
            // toast can say so, rather than looking broken when nothing happens.
            listOf("Downloads", "History", "Settings").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIntent(ProfileIntent.ShallowLinkClicked(label)) }
                        .padding(vertical = 12.dp),
                )
            }

            TextButton(
                onClick = { onIntent(ProfileIntent.SignOutClicked) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // Screen 07: a dialog gating sign-out, not a route — see docs/decisions.md D-004.
    if (state.showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(ProfileIntent.SignOutDismissed) },
            title = { Text("Sign out?") },
            text = { Text("You'll need to sign in again to watch. Downloads stay on this device.") },
            confirmButton = {
                TextButton(onClick = { onIntent(ProfileIntent.SignOutConfirmed) }) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(ProfileIntent.SignOutDismissed) }) {
                    Text("Cancel")
                }
            },
        )
    }
}
