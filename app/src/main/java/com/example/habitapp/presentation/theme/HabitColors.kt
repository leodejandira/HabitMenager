package com.example.habitapp.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * User-selectable habit / list accent colors.
 *
 * Only the stable [key] is persisted in the database (never an ARGB value), so the
 * palette can be retuned for contrast without migrating user data. Each entry has a
 * light- and dark-theme variant tuned for legibility on the app surfaces.
 */
@Immutable
enum class HabitColor(val key: String, val light: Color, val dark: Color) {
    EVERGREEN("evergreen", Color(0xFF2F6B5F), Color(0xFF7FCBB6)),
    OCEAN("ocean", Color(0xFF1F6A9A), Color(0xFF8BC8F2)),
    INDIGO("indigo", Color(0xFF4E5BB0), Color(0xFFB4BDFF)),
    VIOLET("violet", Color(0xFF7A4BA8), Color(0xFFD7B5FA)),
    ROSE("rose", Color(0xFFAD3E6B), Color(0xFFFFADCB)),
    CORAL("coral", Color(0xFFC0503A), Color(0xFFFFB3A2)),
    AMBER("amber", Color(0xFFA86A00), Color(0xFFFFC56B)),
    OLIVE("olive", Color(0xFF5E6E1F), Color(0xFFC3D57A)),
    TEAL("teal", Color(0xFF006A6A), Color(0xFF6FD8D6)),
    SLATE("slate", Color(0xFF52606D), Color(0xFFB8C6D4));

    @Composable
    fun resolve(darkTheme: Boolean = LocalDarkTheme.current): Color = if (darkTheme) dark else light

    companion object {
        val Default = EVERGREEN

        fun fromKey(key: String?): HabitColor = entries.firstOrNull { it.key == key } ?: Default
    }
}
