package com.noam.kidtube.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A warm, high-contrast palette rather than Material's default purple.
 *
 * Light only, deliberately: a dark theme on a child's tablet mostly makes thumbnails
 * harder to tell apart, and the grid is almost entirely pictures.
 */
private val KidColors = lightColorScheme(
    primary = Color(0xFF00629E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFFFF8A3D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC6),
    onSecondaryContainer = Color(0xFF2F1500),
    background = Color(0xFFFCFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72777F),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

/**
 * Larger than Material's defaults throughout. Everything on the child's side is meant to
 * be read across a tablet held at arm's length, or tapped without reading at all.
 */
private val KidTypography = Typography(
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
)

/**
 * The parent's side keeps Material's own sizes. The admin screens are lists of channels,
 * history rows and numbers read by an adult holding the tablet, and the child's type
 * scale fits about four rows of it on screen.
 */
private val AdminTypography = Typography()

/**
 * One palette, two type scales. [forAdmin] is what keeps the parent's dense lists legible
 * without giving the child's screens an adult's small text.
 */
@Composable
fun AppTheme(forAdmin: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KidColors,
        typography = if (forAdmin) AdminTypography else KidTypography,
        content = content,
    )
}

/** The child's side. Equivalent to [AppTheme] with the default argument; named for previews. */
@Composable
fun KidTheme(content: @Composable () -> Unit) = AppTheme(forAdmin = false, content = content)
