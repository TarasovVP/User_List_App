package com.example.userlistapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.userlistapp.domain.model.ThemeMode

private val BrandBlue = Color(0xFF0078B8)
private val BrandLightBlue = Color(0xFF55B8E1)
private val BrandOrange = Color(0xFFF39A18)
private val BrandCoral = Color(0xFFF04B2E)

val FavoriteSelectedColor = BrandOrange

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBEAFF),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = Color(0xFF347F9B),
    onSecondary = Color.White,
    secondaryContainer = BrandLightBlue,
    onSecondaryContainer = Color(0xFF002633),
    tertiary = Color(0xFF9A5B00),
    onTertiary = Color.White,
    tertiaryContainer = BrandOrange,
    onTertiaryContainer = Color(0xFF2D1700),
    error = Color(0xFFB62F1A),
    onError = Color.White,
    errorContainer = BrandCoral,
    onErrorContainer = Color(0xFF280200),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDCE4E8),
    onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70787C),
    outlineVariant = Color(0xFFC0C8CC),
    inverseSurface = Color(0xFF2C3134),
    inverseOnSurface = Color(0xFFEDF1F4),
    inversePrimary = Color(0xFF79CFFF),
    surfaceTint = BrandBlue,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF79CFFF),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF005A86),
    onPrimaryContainer = Color(0xFFC8EAFF),
    secondary = Color(0xFFA6DDF3),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF164E60),
    onSecondaryContainer = Color(0xFFC8EDFC),
    tertiary = Color(0xFFFFB866),
    onTertiary = Color(0xFF512A00),
    tertiaryContainer = Color(0xFF713F00),
    onTertiaryContainer = Color(0xFFFFDCB2),
    error = Color(0xFFFFB4A5),
    onError = Color(0xFF680001),
    errorContainer = Color(0xFF922014),
    onErrorContainer = Color(0xFFFFDAD2),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE0E4E7),
    surface = Color(0xFF101416),
    onSurface = Color(0xFFE0E4E7),
    surfaceVariant = Color(0xFF40484C),
    onSurfaceVariant = Color(0xFFC0C8CC),
    outline = Color(0xFF8A9296),
    outlineVariant = Color(0xFF40484C),
    inverseSurface = Color(0xFFE0E4E7),
    inverseOnSurface = Color(0xFF2C3134),
    inversePrimary = BrandBlue,
    surfaceTint = Color(0xFF79CFFF),
)

@Composable
fun UserListTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
