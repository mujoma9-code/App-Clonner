package dev.clonner.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Violet = Color(0xFF7C4DFF)
val VioletDeep = Color(0xFF5B2BE0)
val Teal = Color(0xFF12F0E1)
val Coral = Color(0xFFFF6B6B)
val Amber = Color(0xFFFFB020)
val Lime = Color(0xFF7BE04F)

private val LightColors = lightColorScheme(
    primary = VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E0FF),
    onPrimaryContainer = Color(0xFF1E0B57),
    secondary = Color(0xFF00A896),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7FFF8),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFB3261E),
    onTertiary = Color.White,
    background = Color(0xFFF7F4FF),
    onBackground = Color(0xFF15121F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15121F),
    surfaceVariant = Color(0xFFEDE7F6),
    onSurfaceVariant = Color(0xFF4A4458),
    outline = Color(0xFF7C7590),
    outlineVariant = Color(0xFFDCD5EA),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9B4FF),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF432B8C),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Teal,
    onSecondary = Color(0xFF00302B),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFA8FFF3),
    tertiary = Coral,
    onTertiary = Color(0xFF5F1412),
    background = Color(0xFF0F0D17),
    onBackground = Color(0xFFE7E1F3),
    surface = Color(0xFF17141F),
    onSurface = Color(0xFFE7E1F3),
    surfaceVariant = Color(0xFF261F35),
    onSurfaceVariant = Color(0xFFC9C2D8),
    outline = Color(0xFF6F6784),
    outlineVariant = Color(0xFF352D45),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun ClonnerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val lightBars = colors.background.luminance() > 0.5f
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ClonnerTypography,
        shapes = ClonnerShapes,
        content = content,
    )
}
