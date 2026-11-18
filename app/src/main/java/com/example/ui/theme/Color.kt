package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val IvoryBackground = Color(0xFFFAF7F2)
val IvorySurface = Color(0xFFFFFFFF)
val IvorySurfaceVariant = Color(0xFFF1ECE1)
val InkText = Color(0xFF1A1916)
val MutedInk = Color(0xFF5D5950)

val ObsidianBackground = Color(0xFF12100E)
val ObsidianSurface = Color(0xFF1C1916)
val ObsidianSurfaceVariant = Color(0xFF28241F)
val AlabasterText = Color(0xFFF5F1E8)
val MutedAlabaster = Color(0xFFB5AEA0)

val DefaultEmeraldPrimary = Color(0xFF1B4D3E)
val DefaultGoldSecondary = Color(0xFFC59B27)
val DangerCrimson = Color(0xFFB3261E)
val WarningAmber = Color(0xFFD97706)
val SuccessTeal = Color(0xFF0F766E)

data class ThemePalettePreset(
    val id: String,
    val labelAr: String,
    val labelEn: String,
    val primaryHex: String,
    val secondaryHex: String
)

val ThemePresets = listOf(
    ThemePalettePreset("emerald_gold", "زمرد وذهب", "Emerald & Gold", "#1B4D3E", "#C59B27"),
    ThemePalettePreset("royal_oud", "عود ملكي", "Royal Oud", "#4A2C2A", "#D4AF37"),
    ThemePalettePreset("desert_indigo", "نيلي ورملي", "Indigo & Sand", "#1E3A5F", "#C88A36"),
    ThemePalettePreset("damask_rose", "ورد دمشقي", "Damask Rose", "#7A2848", "#C99E52")
)

fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        val cleaned = hex.trim().removePrefix("#")
        val value = when (cleaned.length) {
            6 -> 0xFF000000L or cleaned.toLong(16)
            8 -> cleaned.toLong(16)
            else -> return fallback
        }
        Color(value)
    } catch (_: Exception) {
        fallback
    }
}
