// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.freetotake.resources.Res
import app.freetotake.resources.inter_bold
import app.freetotake.resources.inter_regular
import app.freetotake.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/**
 * Tokens from Figma "Freebies Android application".
 * v1.13 dark mode = inverted neutrals (white ↔ near-black, black ↔ white); lime, yellow and photos stay as they are.
 */
object FttColors {
    /** Set once per composition root from the system setting (activity is recreated when it changes). */
    var dark: Boolean = false

    private fun pick(light: Color, dark: Color) = if (this.dark) dark else light

    val Lime = Color(0xFFD5FB0B)              // primary button
    val StartLime = Color(0xFFC8FF00)         // Home "Start" button
    val Yellow = Color(0xFFFFCC33)            // Background/yellow
    /** Content on lime / yellow surfaces: always black (both themes). */
    val OnLime = Color.Black
    val TabInactive = Color(0xFF8E8E93)

    val BackgroundPrimary get() = pick(Color(0xFFF2F2F7), Color(0xFF0D0D0F)) // Background/Primary
    val BackgroundSecondary get() = pick(Color.White, Color(0xFF1C1C1E))     // Background/Secondary, cards
    /** Cards, fields, sheets rows. */
    val Surface get() = BackgroundSecondary
    val TextPrimary get() = pick(Color.Black, Color.White)
    /** Default ink for text, icons and outlines on surfaces. */
    val Ink get() = TextPrimary
    val TextSecondary get() = pick(Color(0xFF85858B), Color(0xFF9E9EA5))    // text/secondary (Skip)
    val DotInactive get() = pick(Color(0xFFD8D8DC), Color(0xFF3A3A3C))      // SystemGray/05
    val ButtonBorder get() = pick(Color(0x993C3C43), Color(0x99EBEBF5))     // rgba(60,60,67,0.6)
    val SectionCard get() = pick(Color(0x66DDE1E6), Color(0x66303036))      // empty-state cards
    val LabelSecondary get() = pick(Color(0x993C3C43), Color(0x99EBEBF5))   // label/secondary
    val Separator get() = pick(Color(0x333C3C43), Color(0x33EBEBF5))
}

@Composable
fun interFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

/** Figma text styles; SF Pro (iOS-only) mapped to Inter. */
object FttType {
    @Composable fun title2() = TextStyle(fontFamily = interFamily(), fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.22).sp)
    @Composable fun largeTitleBold() = TextStyle(fontFamily = interFamily(), fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp)
    @Composable fun bodyBold() = TextStyle(fontFamily = interFamily(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.408).sp)
    @Composable fun body() = TextStyle(fontFamily = interFamily(), fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.408).sp)
    @Composable fun subheadline() = TextStyle(fontFamily = interFamily(), fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.24).sp)
    @Composable fun caption() = TextStyle(fontFamily = interFamily(), fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.066.sp)
    @Composable fun sectionTitle() = TextStyle(fontFamily = interFamily(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.408).sp)
    @Composable fun title1Bold() = TextStyle(fontFamily = interFamily(), fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
    @Composable fun subheadlineBold() = TextStyle(fontFamily = interFamily(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.5).sp)
}

@Composable
fun FttTheme(content: @Composable () -> Unit) {
    FttColors.dark = isSystemInDarkTheme()
    val scheme = if (FttColors.dark) darkColorScheme(
        primary = FttColors.Lime, onPrimary = FttColors.OnLime,
        background = FttColors.BackgroundPrimary, surface = FttColors.BackgroundSecondary,
        onBackground = FttColors.TextPrimary, onSurface = FttColors.TextPrimary,
    ) else lightColorScheme(
        primary = FttColors.Lime, onPrimary = FttColors.OnLime,
        background = FttColors.BackgroundPrimary, surface = FttColors.BackgroundSecondary,
        onBackground = FttColors.TextPrimary, onSurface = FttColors.TextPrimary,
    )
    MaterialTheme(colorScheme = scheme) {
        // Text without an explicit colour follows the theme ink (white in dark mode).
        CompositionLocalProvider(LocalContentColor provides FttColors.Ink, content = content)
    }
}
