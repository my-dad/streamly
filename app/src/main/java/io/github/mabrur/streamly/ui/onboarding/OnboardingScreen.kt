package io.github.mabrur.streamly.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Streamly", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Watch anywhere. Even offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        Button(
            onClick = { onIntent(OnboardingIntent.ContinueWithGoogle) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue with Google")
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = { onIntent(OnboardingIntent.EmailChanged(it)) },
            label = { Text("Email") },
            singleLine = true,
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
            Text("Continue as guest")
        }
    }
}
