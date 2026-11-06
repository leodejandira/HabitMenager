package com.example.habitapp.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Whether the resolved app theme is dark (may differ from the system setting). */
val LocalDarkTheme = staticCompositionLocalOf { false }

fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun HabitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && supportsDynamicColor() -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (fontScale == 1f) AppTypography else AppTypography.scaled(fontScale),
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Design-system tokens that Material 3 does not model directly. */
object HabitTheme {
    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalDarkTheme.current
}

private fun androidx.compose.ui.text.TextStyle.scaled(factor: Float) =
    copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)

private fun androidx.compose.material3.Typography.scaled(f: Float) = androidx.compose.material3.Typography(
    displayLarge = displayLarge.scaled(f), displayMedium = displayMedium.scaled(f), displaySmall = displaySmall.scaled(f),
    headlineLarge = headlineLarge.scaled(f), headlineMedium = headlineMedium.scaled(f), headlineSmall = headlineSmall.scaled(f),
    titleLarge = titleLarge.scaled(f), titleMedium = titleMedium.scaled(f), titleSmall = titleSmall.scaled(f),
    bodyLarge = bodyLarge.scaled(f), bodyMedium = bodyMedium.scaled(f), bodySmall = bodySmall.scaled(f),
    labelLarge = labelLarge.scaled(f), labelMedium = labelMedium.scaled(f), labelSmall = labelSmall.scaled(f),
)
