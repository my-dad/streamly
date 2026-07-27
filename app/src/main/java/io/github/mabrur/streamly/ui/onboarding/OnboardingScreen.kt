package io.github.mabrur.streamly.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyColors
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyShapes

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(StreamlyColors.Accent, StreamlyColors.AccentGradientEnd),
                ),
            )
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(StreamlyShapes.Logo)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
        }

        Text(
            text = "Welcome to\nStreamly",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "Watch videos & shorts, offline too.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        Button(
            onClick = { onIntent(OnboardingIntent.ContinueWithGoogle) },
            enabled = !state.isSubmitting,
            shape = StreamlyShapes.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = StreamlyColors.Surface,
                contentColor = StreamlyColors.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue with Google")
        }

        // Kept against the design export, which drops straight through on this button.
        // PRD §9 requires Onboarding to offer email sign-in, and the PRD outranks the
        // design — so the field and its validation stay, restyled onto the gradient.
        OutlinedTextField(
            value = state.email,
            onValueChange = { onIntent(OnboardingIntent.EmailChanged(it)) },
            label = { Text("Email") },
            singleLine = true,
            isError = state.error != null,
            shape = StreamlyShapes.Button,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.55f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.75f),
                cursorColor = Color.White,
                errorBorderColor = StreamlyColors.Danger,
                errorLabelColor = StreamlyColors.Danger,
                errorSupportingTextColor = StreamlyColors.Danger,
            ),
            supportingText = {
                if (state.error is OnboardingError.InvalidEmail) {
                    Text("Enter a valid email address")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )

        OutlinedButton(
            onClick = { onIntent(OnboardingIntent.SubmitEmail) },
            enabled = !state.isSubmitting,
            shape = StreamlyShapes.Pill,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.55f),
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Sign in with email")
        }

        TextButton(
            onClick = { onIntent(OnboardingIntent.ContinueAsGuest) },
            enabled = !state.isSubmitting,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(
                text = "Continue as guest",
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}
