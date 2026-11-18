package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.i18n.AppTranslations
import com.example.i18n.StoreStrings

val LocalStoreStrings = staticCompositionLocalOf { AppTranslations.ar }

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun AromaPosTheme(
    darkTheme: Boolean,
    primaryColorHex: String = "#1B4D3E",
    secondaryColorHex: String = "#C59B27",
    language: AppLanguage = AppLanguage.AR,
    content: @Composable () -> Unit
) {
    val primaryColor = parseHexColor(primaryColorHex, DefaultEmeraldPrimary)
    val secondaryColor = parseHexColor(secondaryColorHex, DefaultGoldSecondary)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = secondaryColor,
            onPrimary = ObsidianBackground,
            primaryContainer = primaryColor,
            onPrimaryContainer = AlabasterText,
            secondary = primaryColor,
            onSecondary = AlabasterText,
            secondaryContainer = ObsidianSurfaceVariant,
            onSecondaryContainer = AlabasterText,
            background = ObsidianBackground,
            onBackground = AlabasterText,
            surface = ObsidianSurface,
            onSurface = AlabasterText,
            surfaceVariant = ObsidianSurfaceVariant,
            onSurfaceVariant = MutedAlabaster,
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.14f),
            onPrimaryContainer = primaryColor,
            secondary = secondaryColor,
            onSecondary = InkText,
            secondaryContainer = secondaryColor.copy(alpha = 0.18f),
            onSecondaryContainer = InkText,
            background = IvoryBackground,
            onBackground = InkText,
            surface = IvorySurface,
            onSurface = InkText,
            surfaceVariant = IvorySurfaceVariant,
            onSurfaceVariant = MutedInk,
            error = DangerCrimson,
            onError = Color.White
        )
    }

    val layoutDirection = if (language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr
    val strings = AppTranslations.forLanguage(language)

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalStoreStrings provides strings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
