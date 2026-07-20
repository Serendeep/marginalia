package com.serendeep.marginalia.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.serendeep.marginalia.R

// Fonts resolve through the Google Fonts provider at runtime; when the
// provider is unavailable the platform default typeface is used instead.
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val DisplayFamily = FontFamily(
    Font(GoogleFont("Outfit"), provider, FontWeight.Normal),
    Font(GoogleFont("Outfit"), provider, FontWeight.Medium),
    Font(GoogleFont("Outfit"), provider, FontWeight.SemiBold),
)

val BodyFamily = FontFamily(
    Font(GoogleFont("IBM Plex Sans"), provider, FontWeight.Normal),
    Font(GoogleFont("IBM Plex Sans"), provider, FontWeight.Medium),
    Font(GoogleFont("IBM Plex Sans"), provider, FontWeight.SemiBold),
)

val MonoFamily = FontFamily(
    Font(GoogleFont("IBM Plex Mono"), provider, FontWeight.Normal),
    Font(GoogleFont("IBM Plex Mono"), provider, FontWeight.Medium),
)
