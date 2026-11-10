// Generated: the same recipe in every app of the suite (Synchrotron, Mémento, Ecoutify, Météorite, Échoppe, Beeper).
package app.beeper.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Fixed brand colors (never the wallpaper's), from the app accent #E0457B (the pink end of the icon's sunset):
 * Material's tonal-spot scheme for surfaces and containers, the accent at full strength for `primary`.
 */
internal val SuiteLightColors = lightColorScheme(
    primary = Color(0xFFB31F5A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E0),
    onPrimaryContainer = Color(0xFF713345),
    inversePrimary = Color(0xFFFFB1C3),
    secondary = Color(0xFF75565D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E0),
    onSecondaryContainer = Color(0xFF5B3F46),
    tertiary = Color(0xFF7B5733),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF60401E),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF22191B),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF22191B),
    surfaceVariant = Color(0xFFF3DDE1),
    onSurfaceVariant = Color(0xFF514346),
    surfaceTint = Color(0xFFB31F5A),
    inverseSurface = Color(0xFF382E30),
    inverseOnSurface = Color(0xFFFEEDEF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF847376),
    outlineVariant = Color(0xFFD6C2C5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F7),
    surfaceContainer = Color(0xFFFBEAEC),
    surfaceContainerHigh = Color(0xFFF5E4E6),
    surfaceContainerHighest = Color(0xFFEFDEE1),
    surfaceContainerLow = Color(0xFFFFF0F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6D6D8),
)

internal val SuiteDarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C3),
    onPrimary = Color(0xFF551D2F),
    primaryContainer = Color(0xFF713345),
    onPrimaryContainer = Color(0xFFFFD9E0),
    inversePrimary = Color(0xFF8D4A5C),
    secondary = Color(0xFFE3BDC4),
    onSecondary = Color(0xFF43292F),
    secondaryContainer = Color(0xFF5B3F46),
    onSecondaryContainer = Color(0xFFFFD9E0),
    tertiary = Color(0xFFEDBD92),
    onTertiary = Color(0xFF472A0A),
    tertiaryContainer = Color(0xFF60401E),
    onTertiaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF191113),
    onBackground = Color(0xFFEFDEE1),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFEFDEE1),
    surfaceVariant = Color(0xFF514346),
    onSurfaceVariant = Color(0xFFD6C2C5),
    surfaceTint = Color(0xFFFFB1C3),
    inverseSurface = Color(0xFFEFDEE1),
    inverseOnSurface = Color(0xFF382E30),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9E8C8F),
    outlineVariant = Color(0xFF514346),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF413738),
    surfaceContainer = Color(0xFF261D1F),
    surfaceContainerHigh = Color(0xFF312829),
    surfaceContainerHighest = Color(0xFF3C3234),
    surfaceContainerLow = Color(0xFF22191B),
    surfaceContainerLowest = Color(0xFF140C0E),
    surfaceDim = Color(0xFF191113),
)

/** Rounded shapes shared by the whole suite. */
internal val SuiteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
