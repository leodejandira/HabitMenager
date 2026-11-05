package com.example.habitapp

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.presentation.MainUiState
import com.example.habitapp.presentation.MainViewModel
import com.example.habitapp.presentation.navigation.DeepLink
import com.example.habitapp.presentation.navigation.HabitApp
import com.example.habitapp.presentation.theme.HabitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Destination requested by a notification tap, consumed once by the navigation host. */
    private var deepLink by mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) deepLink = DeepLink.from(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = resolveDarkTheme(uiState)
            val dynamicColor = (uiState as? MainUiState.Ready)?.useDynamicColor ?: false

            // Keep system bar icons legible when the in-app theme overrides the system one.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose { }
            }

            HabitTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Preferences load from disk in a few milliseconds; drawing the app only
                    // afterwards avoids a flash of the wrong theme.
                    if (uiState is MainUiState.Ready) {
                        HabitApp(deepLink = deepLink, onDeepLinkHandled = { deepLink = null })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLink.from(intent)?.let { deepLink = it }
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}

@Composable
private fun resolveDarkTheme(uiState: MainUiState): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (uiState) {
        MainUiState.Loading -> systemDark
        is MainUiState.Ready -> when (uiState.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }
}
