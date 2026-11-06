package com.example.habitapp.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Pastel palette for habits and lists. Only the stable [key] is stored in the database.
 * Pastels are readable with dark text on both themes, so one value serves light and dark.
 */
@Immutable
enum class HabitColor(val key: String, val color: Color) {
    CORAL("coral", Color(0xFFF7845E)),
    PEACH("peach", Color(0xFFF6B48C)),
    PINK("pink", Color(0xFFF4A0A8)),
    ROSE("rose", Color(0xFFE5577D)),
    YELLOW("yellow", Color(0xFFF6DF8C)),
    AMBER("amber", Color(0xFFF5C842)),
    MINT("mint", Color(0xFFA8D8B0)),
    TEAL("teal", Color(0xFF4DB6AC)),
    AQUA("aqua", Color(0xFF95DDE3)),
    CYAN("cyan", Color(0xFF4DD0E1)),
    SKY("sky", Color(0xFF5DADEC)),
    BLUE("blue", Color(0xFF4A90E2)),
    LAVENDER("lavender", Color(0xFFC4C8EC)),
    PERIWINKLE("periwinkle", Color(0xFF9FA8DA)),
    INDIGO("indigo", Color(0xFF7986CB)),
    LILAC("lilac", Color(0xFFC5B3E6)),
    PURPLE("purple", Color(0xFFA68BD8)),
    SILVER("silver", Color(0xFFB0BEC5)),
    SLATE("slate", Color(0xFF90A4AE));

    val light: Color get() = color
    val dark: Color get() = color

    @Composable
    @Suppress("UNUSED_PARAMETER")
    fun resolve(darkTheme: Boolean = LocalDarkTheme.current): Color = color

    companion object {
        val Default = PERIWINKLE

        /** Keys used by earlier versions of the app. */
        private val legacy = mapOf(
            "evergreen" to TEAL,
            "ocean" to SKY,
            "violet" to PURPLE,
            "olive" to MINT,
        )

        fun fromKey(key: String?): HabitColor =
            entries.firstOrNull { it.key == key } ?: legacy[key] ?: Default
    }
}

/** Text colour for content drawn on a pastel habit/list colour. */
val OnPastel = Color(0xFF111111)
