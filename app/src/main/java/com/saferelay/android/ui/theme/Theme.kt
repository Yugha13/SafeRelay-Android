package com.saferelay.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// SafeRelay Emergency Color Scheme – matches iOS SafeRelay theme
// Dark: Red-on-Black emergency aesthetic
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF3B30),        // Emergency red (iOS `Color.red`)
    onPrimary = Color.White,
    secondary = Color(0xFFFF9500),      // Urgent orange
    onSecondary = Color.Black,
    background = Color(0xFF000000),     // Pure black
    onBackground = Color(0xFFFF3B30),   // Red on black
    surface = Color(0xFF111111),        // Very dark gray
    onSurface = Color(0xFFFF3B30),      // Red text
    surfaceVariant = Color(0xFF1A1A1A), // Slightly lighter dark
    onSurfaceVariant = Color(0xFFFF9500),
    error = Color(0xFFFF5555),
    onError = Color.Black,
    outline = Color(0xFF3A3A3A)
)

// Light: Softer red on white for daylight readability
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFCC0000),        // Dark red
    onPrimary = Color.White,
    secondary = Color(0xFFE65100),      // Dark orange
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFFCC0000),
    surface = Color(0xFFF8F8F8),
    onSurface = Color(0xFFCC0000),
    surfaceVariant = Color(0xFFFFF0F0),
    onSurfaceVariant = Color(0xFFE65100),
    error = Color(0xFFCC0000),
    onError = Color.White,
    outline = Color(0xFFE0E0E0)
)



@Composable
fun SafeRelayTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
