package io.github.mabrur.streamly

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import dagger.hilt.android.AndroidEntryPoint
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyTheme
import io.github.mabrur.streamly.ui.StreamlyApp

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The download service runs in the foreground and needs a visible notification;
        // without this grant the system kills it. minSdk is 25 and the permission does
        // not exist below 33, so the version guard is mandatory, not defensive.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        enableEdgeToEdge()
        setContent {
            StreamlyTheme {
                StreamlyApp(windowSizeClass = calculateWindowSizeClass(this))
            }
        }
    }
}
