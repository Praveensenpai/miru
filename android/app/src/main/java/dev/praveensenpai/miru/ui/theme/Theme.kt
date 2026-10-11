package dev.praveensenpai.miru.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback palette used when dynamic color is unavailable (API < 31).
private val Ink = Color(0xFF101014)
private val InkSoft = Color(0xFF1A1A20)
private val Accent = Color(0xFF8AB4F8)
private val AccentDim = Color(0xFF3F5C8A)
private val OnDark = Color(0xFFE6E1E5)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    primaryContainer = AccentDim,
    onPrimaryContainer = OnDark,
    background = Ink,
    onBackground = OnDark,
    surface = Ink,
    onSurface = OnDark,
    surfaceVariant = InkSoft,
    onSurfaceVariant = Color(0xFFCAC4D0),
)

private val LightScheme = lightColorScheme(
    primary = AccentDim,
    onPrimary = Color.White,
)

/**
 * miru theme.
 *
 * Prefers Material You dynamic color on Android 12+ so the app inherits the
 * user's wallpaper palette; falls back to a hand-tuned dark scheme otherwise.
 * The app is dark-first by design — a video client should not flash white.
 */
@Composable
fun MiruTheme(
    darkTheme: Boolean = isSystemInDarkTheme() || true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}