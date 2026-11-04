// Generated: the same recipe in every app of the suite (Synchrotron, Mémento, Ecoutify, Météorite, Échoppe, Portix).
package app.portix.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Fixed brand colors (never the wallpaper's), from the app accent #D9801A (the icon's mustard):
 * Material's tonal-spot scheme for surfaces and containers, the accent at full strength for `primary`.
 */
internal val SuiteLightColors = lightColorScheme(
    primary = Color(0xFF8D4F00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC0),
    onPrimaryContainer = Color(0xFF6B3B03),
    inversePrimary = Color(0xFFFFB877),
    secondary = Color(0xFF735943),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC0),
    onSecondaryContainer = Color(0xFF5A422D),
    tertiary = Color(0xFF5A6238),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEE8B2),
    onTertiaryContainer = Color(0xFF424A23),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF2DFD1),
    onSurfaceVariant = Color(0xFF51443A),
    surfaceTint = Color(0xFF8D4F00),
    inverseSurface = Color(0xFF372F28),
    inverseOnSurface = Color(0xFFFEEEE3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF837469),
    outlineVariant = Color(0xFFD5C3B6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainer = Color(0xFFFBEBE1),
    surfaceContainerHigh = Color(0xFFF5E5DB),
    surfaceContainerHighest = Color(0xFFEFE0D5),
    surfaceContainerLow = Color(0xFFFFF1E8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6D7CD),
)

internal val SuiteDarkColors = darkColorScheme(
    primary = Color(0xFFFFB877),
    onPrimary = Color(0xFF4B2700),
    primaryContainer = Color(0xFF6B3B03),
    onPrimaryContainer = Color(0xFFFFDCC0),
    inversePrimary = Color(0xFF87521B),
    secondary = Color(0xFFE2C0A5),
    onSecondary = Color(0xFF412C19),
    secondaryContainer = Color(0xFF5A422D),
    onSecondaryContainer = Color(0xFFFFDCC0),
    tertiary = Color(0xFFC2CB98),
    onTertiary = Color(0xFF2C340E),
    tertiaryContainer = Color(0xFF424A23),
    onTertiaryContainer = Color(0xFFDEE8B2),
    background = Color(0xFF19120C),
    onBackground = Color(0xFFEFE0D5),
    surface = Color(0xFF19120C),
    onSurface = Color(0xFFEFE0D5),
    surfaceVariant = Color(0xFF51443A),
    onSurfaceVariant = Color(0xFFD5C3B6),
    surfaceTint = Color(0xFFFFB877),
    inverseSurface = Color(0xFFEFE0D5),
    inverseOnSurface = Color(0xFF372F28),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9E8E82),
    outlineVariant = Color(0xFF51443A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF413730),
    surfaceContainer = Color(0xFF261E18),
    surfaceContainerHigh = Color(0xFF312822),
    surfaceContainerHighest = Color(0xFF3C332C),
    surfaceContainerLow = Color(0xFF221A14),
    surfaceContainerLowest = Color(0xFF130D07),
    surfaceDim = Color(0xFF19120C),
)

/** Rounded shapes shared by the whole suite. */
internal val SuiteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
