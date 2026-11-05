package com.example.habitapp.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// "Steady" palette: a calm evergreen primary, a warm clay secondary and a soft
// indigo tertiary, on slightly green-tinted neutrals.

private val Evergreen10 = Color(0xFF00201A)
private val Evergreen30 = Color(0xFF14524A)
private val Evergreen40 = Color(0xFF2F6B5F)
private val Evergreen80 = Color(0xFF94D3C1)
private val Evergreen90 = Color(0xFFB0EFDD)

private val Clay10 = Color(0xFF311300)
private val Clay30 = Color(0xFF6E3A1E)
private val Clay40 = Color(0xFF8A5133)
private val Clay80 = Color(0xFFFFB68F)
private val Clay90 = Color(0xFFFFDBCA)

private val Indigo10 = Color(0xFF0E1A43)
private val Indigo30 = Color(0xFF3A4675)
private val Indigo40 = Color(0xFF525E8F)
private val Indigo80 = Color(0xFFBAC4FD)
private val Indigo90 = Color(0xFFDEE1FF)

private val Red10 = Color(0xFF410002)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

private val Neutral6 = Color(0xFF101413)
private val Neutral10 = Color(0xFF181C1B)
private val Neutral12 = Color(0xFF1C201F)
private val Neutral17 = Color(0xFF272B29)
private val Neutral22 = Color(0xFF313634)
private val Neutral24 = Color(0xFF363A39)
private val Neutral90 = Color(0xFFDFE4E1)
private val Neutral94 = Color(0xFFEAEFEC)
private val Neutral96 = Color(0xFFF0F5F2)
private val Neutral98 = Color(0xFFF8FAF7)
private val Neutral100 = Color(0xFFFFFFFF)

private val NeutralVariant30 = Color(0xFF3F4945)
private val NeutralVariant50 = Color(0xFF6F7975)
private val NeutralVariant60 = Color(0xFF89938F)
private val NeutralVariant80 = Color(0xFFBEC9C4)
private val NeutralVariant90 = Color(0xFFDAE5E0)

internal val LightColors = lightColorScheme(
    primary = Evergreen40,
    onPrimary = Neutral100,
    primaryContainer = Evergreen90,
    onPrimaryContainer = Evergreen10,
    secondary = Clay40,
    onSecondary = Neutral100,
    secondaryContainer = Clay90,
    onSecondaryContainer = Clay10,
    tertiary = Indigo40,
    onTertiary = Neutral100,
    tertiaryContainer = Indigo90,
    onTertiaryContainer = Indigo10,
    error = Red40,
    onError = Neutral100,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Color(0xFFE5EAE6),
    surfaceContainerHighest = Neutral90,
    inverseSurface = Color(0xFF2D3130),
    inverseOnSurface = Color(0xFFEEF2EF),
    inversePrimary = Evergreen80,
)

internal val DarkColors = darkColorScheme(
    primary = Evergreen80,
    onPrimary = Color(0xFF003730),
    primaryContainer = Evergreen30,
    onPrimaryContainer = Evergreen90,
    secondary = Clay80,
    onSecondary = Color(0xFF51240A),
    secondaryContainer = Clay30,
    onSecondaryContainer = Clay90,
    tertiary = Indigo80,
    onTertiary = Color(0xFF23305E),
    tertiaryContainer = Indigo30,
    onTertiaryContainer = Indigo90,
    error = Red80,
    onError = Color(0xFF690005),
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    surfaceContainerLowest = Color(0xFF0B0F0E),
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22,
    inverseSurface = Neutral90,
    inverseOnSurface = Color(0xFF2D3130),
    inversePrimary = Evergreen40,
    surfaceBright = Neutral24,
)
