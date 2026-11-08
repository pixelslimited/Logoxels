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

internal val Ink: Color get() = if (Pal.dark) Color(0xFFF3F4F6) else Color(0xFF111827)
internal val Muted: Color get() = if (Pal.dark) Color(0xFF6B7280) else Color(0xFF9CA3AF)
internal val Accent: Color get() = if (Pal.dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
internal val Bar: Color get() = if (Pal.dark) Color(0xFF1F2937) else Color.White
internal val Back: Color get() = if (Pal.dark) Color(0xFF0B1220) else Color(0xFFECEFF1)
internal val PanelBg: Color get() = if (Pal.dark) Color(0xFF111827) else Color(0xFFF8FAFC)
internal val SelBg: Color get() = if (Pal.dark) Color(0xFF134E4A) else Color(0xFFCCFBF1)
internal val Line: Color get() = if (Pal.dark) Color(0xFF4B5563) else Color(0xFFD1D5DB)

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
