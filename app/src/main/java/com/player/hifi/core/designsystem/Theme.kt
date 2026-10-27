package com.player.hifi.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.player.hifi.core.model.ThemeMode

private val FallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF005AC1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF535E78),
    background = Color(0xFFFAF9FD),
    surface = Color(0xFFFAF9FD),
    surfaceVariant = Color(0xFFE1E2EC)
)

private val FallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBBC6E4),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF44474F)
)

private fun ColorScheme.toAmoledBlackScheme(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF121316),
    surfaceContainerHigh = Color(0xFF1A1C20)
)

@Composable
fun PlayerTheme(
    themeMode: ThemeMode,
    useAmoledDark: Boolean,
    useDynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val baseScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }

    val finalScheme = if (isDark && useAmoledDark) {
        baseScheme.toAmoledBlackScheme()
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = Typography(),
        content = content
    )
}