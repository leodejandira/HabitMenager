package com.example.habitapp.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/** The locale of the current configuration, so per-app language changes recompose. */
@Composable
fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
}
