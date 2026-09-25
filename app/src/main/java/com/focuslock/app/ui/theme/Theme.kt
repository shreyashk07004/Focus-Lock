package com.focuslock.app.ui.theme

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

// A calm teal palette used on Android 11 and below (Android 12+ uses the wallpaper colours).
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F0EA),
    onPrimaryContainer = Color(0xFF00201E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD3CD),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFFA7F0EA),
)

@Composable
fun FocusLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
