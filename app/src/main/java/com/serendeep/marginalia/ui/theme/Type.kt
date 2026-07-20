package com.serendeep.marginalia.ui.theme

import androidx.compose.material3.Typography

// Outfit for display and titles, IBM Plex Sans for body and labels,
// IBM Plex Mono for small utility text.
private val defaults = Typography()

val MarginaliaTypography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = DisplayFamily),
    displayMedium = defaults.displayMedium.copy(fontFamily = DisplayFamily),
    displaySmall = defaults.displaySmall.copy(fontFamily = DisplayFamily),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = DisplayFamily),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = DisplayFamily),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = DisplayFamily),
    titleLarge = defaults.titleLarge.copy(fontFamily = DisplayFamily),
    titleMedium = defaults.titleMedium.copy(fontFamily = DisplayFamily),
    titleSmall = defaults.titleSmall.copy(fontFamily = DisplayFamily),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = BodyFamily),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = BodyFamily),
    bodySmall = defaults.bodySmall.copy(fontFamily = BodyFamily),
    labelLarge = defaults.labelLarge.copy(fontFamily = BodyFamily),
    labelMedium = defaults.labelMedium.copy(fontFamily = BodyFamily),
    labelSmall = defaults.labelSmall.copy(fontFamily = MonoFamily),
)
