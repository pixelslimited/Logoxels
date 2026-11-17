package ng.pixelslimited.logoxels.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Colours are chosen in OKLCH (lightness, chroma, hue) so steps look evenly spaced to the eye
 * and a hue keeps the same perceived brightness in light and dark themes.
 */
fun oklch(l: Float, c: Float, h: Float, alpha: Float = 1f): Color {
    val a = c * cos(Math.toRadians(h.toDouble())).toFloat()
    val b = c * sin(Math.toRadians(h.toDouble())).toFloat()
    val l1 = (l + 0.3963377774f * a + 0.2158037573f * b).pow(3)
    val m1 = (l - 0.1055613458f * a - 0.0638541728f * b).pow(3)
    val s1 = (l - 0.0894841775f * a - 1.2914855480f * b).pow(3)
    fun gamma(x: Float): Float {
        val v = if (x <= 0.0031308f) 12.92f * x else 1.055f * x.toDouble().pow(1 / 2.4).toFloat() - 0.055f
        return v.coerceIn(0f, 1f)
    }
    return Color(
        gamma(4.0767416621f * l1 - 3.3077115913f * m1 + 0.2309699292f * s1),
        gamma(-1.2684380046f * l1 + 2.6097574011f * m1 - 0.3413193965f * s1),
        gamma(-0.0041960863f * l1 - 0.7034186147f * m1 + 1.7076147010f * s1),
        alpha,
    )
}

/** Inverse of [oklch]: returns lightness, chroma, hue. */
fun Color.toOklch(): FloatArray {
    fun lin(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    val r = lin(red); val g = lin(green); val b = lin(blue)
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    val L = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
    val A = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    val B = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    val h = Math.toDegrees(kotlin.math.atan2(B, A).toDouble()).toFloat()
    return floatArrayOf(L, kotlin.math.hypot(A, B), (h + 360f) % 360f)
}

/** A gradient mixed in OKLCH (no muddy grey middle), as many stops as needed. */
fun oklchStops(from: Color, to: Color, steps: Int = 12): List<Color> {
    val a = from.toOklch()
    val b = to.toOklch()
    var dh = b[2] - a[2]
    if (dh > 180f) dh -= 360f
    if (dh < -180f) dh += 360f
    return (0..steps).map { i ->
        val t = i / steps.toFloat()
        oklch(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + dh * t, from.alpha + (to.alpha - from.alpha) * t)
    }
}

/**
 * Masked edges: content fades out where it is cut off by a scroll edge instead of stopping hard.
 * Only the edges that actually have more content behind them fade.
 */
fun Modifier.fadeEdges(horizontal: Boolean, start: Boolean, end: Boolean, size: Dp = 24.dp): Modifier =
    if (!start && !end) this else this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val len = size.toPx()
            val total = if (horizontal) this.size.width else this.size.height
            if (total <= 0f) return@drawWithContent
            val f = (len / total).coerceIn(0f, 0.5f)
            val stops = arrayOf(
                0f to (if (start) Color.Transparent else Color.Black), f to Color.Black,
                1f - f to Color.Black, 1f to (if (end) Color.Transparent else Color.Black),
            )
            val brush = if (horizontal) Brush.horizontalGradient(*stops) else Brush.verticalGradient(*stops)
            drawRect(brush, blendMode = BlendMode.DstIn)
        }

/** Blurred icon swap: the old icon blurs and shrinks away while the new one sharpens in. */
@Composable
fun <T> SwapIcon(state: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    var shown by remember { mutableStateOf(state) }
    var prev by remember { mutableStateOf<T?>(null) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (state != shown) {
            prev = shown
            shown = state
            p.snapTo(0f)
            p.animateTo(1f, tween(260))
            prev = null
        }
    }
    val v = p.value
    fun Modifier.soft(amount: Float) = if (Build.VERSION.SDK_INT >= 31 && amount > 0.01f) blur((6f * amount).dp) else this
    Box(modifier, contentAlignment = Alignment.Center) {
        prev?.let { old ->
            Box(Modifier.graphicsLayer { alpha = 1f - v; scaleX = 1f - 0.4f * v; scaleY = 1f - 0.4f * v }.soft(v)) { content(old) }
        }
        Box(Modifier.graphicsLayer { alpha = v; scaleX = 0.6f + 0.4f * v; scaleY = 0.6f + 0.4f * v }.soft(1f - v)) { content(shown) }
    }
}

/** Dark mode depth: a faint light rim along the top edge, as if lit from above. Nothing in light mode. */
fun Modifier.topLight(dark: Boolean): Modifier = if (!dark) this else drawWithContent {
    drawContent()
    drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx())
}

/**
 * Multiply blending: an image with a white background (a signature, a letterhead) takes on the colour under it,
 * so the white box disappears and only the ink shows. Light mode only; dark surfaces would turn it black.
 */
fun multiplyOn(surface: Color, dark: Boolean): androidx.compose.ui.graphics.ColorFilter? =
    if (dark) null else androidx.compose.ui.graphics.ColorFilter.tint(surface, BlendMode.Multiply)

/** Concentric radius: an inner corner is the outer corner minus the gap between them, so the curves stay parallel. */
fun concentric(outer: Dp, inset: Dp, min: Dp = 4.dp): Dp = (outer - inset).coerceAtLeast(min)
