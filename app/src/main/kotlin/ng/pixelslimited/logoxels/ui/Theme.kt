package ng.pixelslimited.logoxels.ui

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Light/dark switch, read by the colours below so every screen follows it. */
internal object Pal {
    var dark by mutableStateOf(false)
}

// Set in OKLCH so the neutral steps are evenly spaced and the green reads equally bright in both themes.
internal val Ink: Color get() = if (Pal.dark) oklch(0.965f, 0.003f, 265f) else oklch(0.210f, 0.030f, 265f)
internal val Muted: Color get() = if (Pal.dark) oklch(0.700f, 0.015f, 265f) else oklch(0.520f, 0.018f, 265f)
internal val Accent: Color get() = if (Pal.dark) oklch(0.800f, 0.180f, 150f) else oklch(0.600f, 0.160f, 150f)
// Dark depth: canvas back, then panels, then bars, each a step lighter.
internal val Back: Color get() = if (Pal.dark) oklch(0.170f, 0.025f, 265f) else oklch(0.945f, 0.004f, 230f)
internal val PanelBg: Color get() = if (Pal.dark) oklch(0.215f, 0.028f, 265f) else oklch(0.985f, 0.003f, 250f)
internal val Bar: Color get() = if (Pal.dark) oklch(0.275f, 0.028f, 265f) else Color.White
internal val SelBg: Color get() = if (Pal.dark) oklch(0.330f, 0.070f, 150f) else oklch(0.950f, 0.050f, 150f)
/** Lines are see-through ink, so they read the same on every surface. */
internal val Line: Color get() = if (Pal.dark) Color.White.copy(alpha = 0.16f) else oklch(0.210f, 0.030f, 265f, 0.16f)

/**
 * Interface font. Century Gothic is a licensed font and cannot be shipped with the app, so the build
 * bundles an open font of the same geometric style (assets/fonts/ui.ttf); bold is drawn from it.
 * Falls back to the system font when the file is missing.
 */
@OptIn(ExperimentalTextApi::class)
internal fun uiFont(ctx: Context): FontFamily {
    val present = runCatching { ctx.assets.list("fonts")?.contains("ui.ttf") == true }.getOrDefault(false)
    return if (present) FontFamily(Font("fonts/ui.ttf", ctx.assets, FontWeight.Normal)) else FontFamily.Default
}

internal fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)
