package io.github.mabrur.streamly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.mabrur.streamly.core.designsystem.theme.StreamlyTheme
import io.github.mabrur.streamly.ui.StreamlyApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamlyTheme {
                StreamlyApp()
            }
        }
    }
}
