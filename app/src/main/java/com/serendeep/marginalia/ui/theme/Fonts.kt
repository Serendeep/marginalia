package com.serendeep.marginalia.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.serendeep.marginalia.R

// Fonts ship inside the APK. The downloadable-fonts provider needs Google
// Play Services, which Huawei tablets don't have — there the app silently
// fell back to the system typeface.
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight) = Font(
    resId = res,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DisplayFamily = FontFamily(
    variable(R.font.space_grotesk, FontWeight.Normal),
    variable(R.font.space_grotesk, FontWeight.Medium),
    variable(R.font.space_grotesk, FontWeight.SemiBold),
    variable(R.font.space_grotesk, FontWeight.Bold),
)

val BodyFamily = FontFamily(
    variable(R.font.ibm_plex_sans, FontWeight.Normal),
    variable(R.font.ibm_plex_sans, FontWeight.Medium),
    variable(R.font.ibm_plex_sans, FontWeight.SemiBold),
)

val MonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)
