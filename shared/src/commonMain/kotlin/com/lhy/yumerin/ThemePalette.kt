package com.lhy.yumerin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lhy.yumerin.core.model.ThemeColor
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

internal data class ThemeColorSchemes(
    val light: Colors,
    val dark: Colors
)

/** Accent color used only for controls and accent containers; neutral surfaces stay fixed. */
internal fun ThemeColor.accentColor(): Color = when (this) {
    ThemeColor.BLUE -> Color(0xFF536EE8)
    ThemeColor.PURPLE -> Color(0xFF6659B8)
    ThemeColor.GREEN -> Color(0xFF397B58)
    ThemeColor.ROSE -> Color(0xFFB34269)
    ThemeColor.ORANGE -> Color(0xFFA75C15)
}

internal fun ThemeColor.colorSchemes(): ThemeColorSchemes {
    val accent = accentColor()
    val lightAccentContainer = lerp(Color.White, accent, 0.12f)
    val darkAccent = lerp(accent, Color.White, 0.42f)
    val darkAccentContainer = lerp(Color(0xFF242424), accent, 0.38f)
    val darkTextOnAccent = Color(0xFF171717)

    return ThemeColorSchemes(
        light = lightColorScheme().copy(
            primary = accent,
            onPrimary = Color.White,
            primaryVariant = accent,
            onPrimaryVariant = Color.White,
            disabledPrimary = lightAccentContainer,
            disabledOnPrimary = accent,
            disabledPrimaryButton = lightAccentContainer,
            disabledOnPrimaryButton = accent,
            disabledPrimarySlider = lightAccentContainer,
            primaryContainer = lightAccentContainer,
            onPrimaryContainer = accent,
            tertiaryContainer = lightAccentContainer,
            onTertiaryContainer = accent
        ),
        dark = darkColorScheme().copy(
            primary = darkAccent,
            onPrimary = darkTextOnAccent,
            primaryVariant = accent,
            onPrimaryVariant = darkAccent,
            disabledPrimary = darkAccentContainer,
            disabledOnPrimary = darkAccent,
            disabledPrimaryButton = darkAccentContainer,
            disabledOnPrimaryButton = darkAccent,
            disabledPrimarySlider = darkAccentContainer,
            primaryContainer = darkAccentContainer,
            onPrimaryContainer = darkAccent,
            tertiaryContainer = darkAccentContainer,
            onTertiaryContainer = darkAccent
        )
    )
}

internal fun ThemeColor.swatch(): Color = accentColor()

internal fun ThemeColor.label(): String = when (this) {
    ThemeColor.BLUE -> "蓝色"
    ThemeColor.PURPLE -> "紫色"
    ThemeColor.GREEN -> "绿色"
    ThemeColor.ROSE -> "玫瑰色"
    ThemeColor.ORANGE -> "橙色"
}
