package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CryptoDarkColorScheme =
    darkColorScheme(
        primary = CryptoPrimary,
        onPrimary = CryptoBackground,
        secondary = CryptoSecondary,
        onSecondary = CryptoTextPrimary,
        tertiary = CryptoSuccess,
        background = CryptoBackground,
        onBackground = CryptoTextPrimary,
        surface = CryptoSurface,
        onSurface = CryptoTextPrimary,
        surfaceVariant = CryptoSurfaceVariant,
        onSurfaceVariant = CryptoTextSecondary,
        error = CryptoError,
        onError = CryptoTextPrimary
    )

@Composable
fun SmmDirectProTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CryptoDarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    SmmDirectProTheme(content = content)
}
