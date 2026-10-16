package com.lhy.yumerin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lhy.yumerin.core.model.ThemeColor
import com.lhy.yumerin.core.model.ComponentMaterial
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

internal fun ThemeColor.colorSchemes(material: ComponentMaterial): ThemeColorSchemes {
    val accent = accentColor()
    val lightAccentContainer = lerp(Color.White, accent, 0.12f)
    val darkAccent = lerp(accent, Color.White, 0.42f)
    val darkAccentContainer = lerp(Color(0xFF242424), accent, 0.38f)
    val darkTextOnAccent = Color(0xFF171717)

    fun Colors.withMaterial(isDark: Boolean): Colors = when (material) {
        ComponentMaterial.STANDARD -> this
        ComponentMaterial.GLASS -> copy(
            secondaryVariant = secondaryVariant.copy(alpha = 0.88f),
            primaryContainer = primaryContainer.copy(alpha = 0.9f),
            secondaryContainer = secondaryContainer.copy(alpha = 0.9f),
            secondaryContainerVariant = secondaryContainerVariant.copy(alpha = 0.9f),
            tertiaryContainer = tertiaryContainer.copy(alpha = 0.9f),
            background = if (isDark) Color(0xFF17191D) else Color(0xFFE8EBF2),
            surface = if (isDark) Color(0xFF202228) else Color(0xFFF0F2F7),
            surfaceVariant = (if (isDark) Color(0xFF3C3F47) else Color.White).copy(alpha = 0.82f),
            surfaceContainer = (if (isDark) Color(0xFF353840) else Color.White).copy(alpha = 0.78f),
            surfaceContainerHigh = (if (isDark) Color(0xFF3B3E47) else Color.White).copy(alpha = 0.84f),
            surfaceContainerHighest = (if (isDark) Color(0xFF42454E) else Color.White).copy(alpha = 0.9f),
            outline = outline.copy(alpha = 0.76f),
            dividerLine = dividerLine.copy(alpha = 0.72f)
        )
    }

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
        ).withMaterial(isDark = false),
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
        ).withMaterial(isDark = true)
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
