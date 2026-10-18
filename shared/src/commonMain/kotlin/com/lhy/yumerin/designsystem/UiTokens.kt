package com.lhy.yumerin.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhy.yumerin.core.model.ComponentMaterial
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/** Semantic layout values shared by Yumerin's Compose screens. */
object YumerinSpacing {
    val Tight = 2.dp
    val CompactRow = 3.dp
    val Inline = 5.dp
    val PageHorizontal = 16.dp
    val PageVertical = 8.dp
    val Section = 18.dp
    val PageSection = 24.dp
    val CardInset = 18.dp
    val LargeInset = 20.dp
    val CardVertical = 16.dp
    val Dense = 6.dp
    val Field = 12.dp
    val FormField = 10.dp
    val Item = 8.dp
    val Row = 14.dp
    val Compact = 4.dp
}

object YumerinSize {
    val SmallIcon = 20.dp
    val Icon = 22.dp
    val NavigationIcon = 24.dp
    val StatusIcon = 15.dp
    val IconViewport = 24.dp
    val SendIcon = 21.dp
    val PreferenceIconContainer = 42.dp
    val TouchTarget = 48.dp
    val ToolbarControl = 52.dp
    val PreferenceRow = 56.dp
    val ChatListRow = 76.dp
    val MenuAction = 40.dp
    val LargeIcon = 28.dp
    val Avatar = 48.dp
    val LargeAvatar = 96.dp
}

object YumerinShape {
    val CardRadius = 20.dp
    val ControlRadius = 16.dp
    val HeroRadius = 24.dp
    val Card = RoundedCornerShape(CardRadius)
    val Control = RoundedCornerShape(ControlRadius)
}

object YumerinOpacity {
    const val DropdownOutline = 0.48f
}

object YumerinTypography {
    val PageTitle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal)
    val SettingsTitle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal)
    val SectionTitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium)
    val Body = TextStyle(fontSize = 17.sp)
    val Supporting = TextStyle(fontSize = 14.sp)
    val Label = TextStyle(fontSize = 13.sp)
    val NavigationLabel = 11.sp

    val miuix: TextStyles = defaultTextStyles(
        main = Body,
        paragraph = Body.copy(lineHeight = 1.2.em),
        body1 = TextStyle(fontSize = 16.sp),
        body2 = Supporting,
        button = Body,
        footnote1 = Label,
        footnote2 = TextStyle(fontSize = 11.sp),
        headline1 = Body,
        headline2 = TextStyle(fontSize = 16.sp),
        subtitle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
        title1 = PageTitle,
        title2 = TextStyle(fontSize = 24.sp),
        title3 = SectionTitle,
        title4 = TextStyle(fontSize = 18.sp)
    )

    fun material3(miuix: TextStyles): Typography = Typography(
        displayLarge = miuix.title1,
        displayMedium = miuix.title1.copy(fontSize = 28.sp),
        displaySmall = miuix.title2,
        headlineLarge = miuix.title1,
        headlineMedium = miuix.title2,
        headlineSmall = miuix.title3,
        titleLarge = miuix.title2,
        titleMedium = miuix.title3,
        titleSmall = miuix.title4,
        bodyLarge = miuix.main,
        bodyMedium = miuix.body1,
        bodySmall = miuix.body2,
        labelLarge = miuix.button,
        labelMedium = miuix.footnote1,
        labelSmall = miuix.footnote2
    )
}

val LocalComponentMaterial = staticCompositionLocalOf { ComponentMaterial.STANDARD }

/** Chat geometry remains intentionally distinct from generic cards and forms. */
object YumerinChatTokens {
    val HeaderHeight = 62.dp
    val HeaderShapeRadius = 31.dp
    val HeaderInsetHorizontal = 11.dp
    val HeaderAvatar = 40.dp
    val HeaderAvatarGap = 9.dp
    val HeaderTitleGap = 1.dp
    val ComposerShapeRadius = 28.dp
    val ComposerInsetHorizontal = 9.dp
    val ComposerInsetVertical = 8.dp
    val ComposerItemGap = 6.dp
    val ComposerFieldRadius = 18.dp
    val BubbleRadius = 20.dp
    val SystemBubbleRadius = 16.dp
    val MessageGap = 10.dp
    val BubbleInsetHorizontal = 14.dp
    val BubbleInsetVertical = 11.dp
    val BubbleMetaGap = 2.dp
    val BubbleActionGap = 2.dp
    val EmptyStateGap = 5.dp
    val ComposerBottomClearance = 16.dp
}

/** Geometry for the chat-list selector, floating action, and row alignment. */
object YumerinChatListTokens {
    val ContentBottomClearance = 96.dp
    val FloatingActionBottomInset = 106.dp
    val DividerLeadingInset = 80.dp
    val SelectorPressDistance = 8.dp
    val SelectorEdgeWidth = 0.8.dp
}

/** The floating bar keeps its accepted layout and backdrop proportions. */
object YumerinNavigationTokens {
    val OuterHorizontalInset = 12.dp
    val OuterVerticalInset = 4.dp
    val BarHeight = 56.dp
    val PressedScale = 78f / 56f
    val MaxWidth = 420.dp
    val WidthFraction = 0.89f
    val TrackInset = 4.dp
    val SelectedHeight = 48.dp
    val PressScaleDistance = 16.dp
    val PanelOffset = 4.dp
    val LensRadiusResting = 10.dp
    val LensRadiusPressed = 14.dp
    val PressShadowRadius = 8.dp
    val TabGap = 2.dp
    val SelectedFillLight = Color.Black.copy(alpha = 0.06f)
    val SelectedFillDark = Color.White.copy(alpha = 0.1f)
    val PressFill = Color.Black.copy(alpha = 0.035f)
}

object YumerinGlassTokens {
    val LightSurface = Color(0xFFFAFAFA)
    val DarkSurface = Color(0xFF242424)
    val PrimaryActionAlpha = 0.9f
    val BlurRadius = 8.dp
    val LensRadius = 24.dp
    val ShadowElevation = 4.dp
    val ShadowAlpha = 0.08f
    val EdgeBlurRadius = 0.4.dp

    @Composable
    fun controlSurface(isLightTheme: Boolean): Color {
        val selectedMaterial = LocalComponentMaterial.current
        val alpha = when (selectedMaterial) {
            ComponentMaterial.STANDARD -> if (isLightTheme) 0.76f else 0.84f
            ComponentMaterial.GLASS -> if (isLightTheme) 0.62f else 0.70f
        }
        return (if (isLightTheme) LightSurface else DarkSurface).copy(alpha = alpha)
    }

    @Composable
    fun navigationSurface(isLightTheme: Boolean): Color {
        val alpha = when (LocalComponentMaterial.current) {
            ComponentMaterial.STANDARD -> 0.6f
            ComponentMaterial.GLASS -> 0.48f
        }
        return (if (isLightTheme) LightSurface else DarkSurface).copy(alpha = alpha)
    }

    @Composable
    fun edgeLight(isLightTheme: Boolean): EdgeLight {
        val glass = LocalComponentMaterial.current == ComponentMaterial.GLASS
        val baseAlpha = if (isLightTheme) 0.6f else 0.2f
        val alpha = if (glass) baseAlpha * 0.85f else baseAlpha
        return EdgeLight(
            color = Color.White.copy(alpha = alpha),
            width = if (isLightTheme) 0.24.dp else 0.34.dp,
            blurRadius = EdgeBlurRadius
        )
    }
}

fun ComponentMaterial.label(): String = when (this) {
    ComponentMaterial.STANDARD -> "标准"
    ComponentMaterial.GLASS -> "柔透"
}
