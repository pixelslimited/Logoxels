package ng.pixelslimited.logoxels.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class G { Select, Rect, Oval, Polygon, Star, Line, Undo, Redo, Union, Minus, Intersect, Exclude, Style, Arrange, Corner }

private fun ngon(n: Int, inner: Float, w: Float): Path = Path().apply {
    val c = w / 2f
    val r = w * 0.4f
    val total = if (inner > 0f) n * 2 else n
    for (i in 0 until total) {
        val a = -PI / 2 + i * 2 * PI / total
        val k = if (inner > 0f && i % 2 == 1) inner else 1f
        val x = c + (r * k * cos(a)).toFloat()
        val y = c + (r * k * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Small vector icons drawn in code (no emoji, no icon library). */
@Composable
fun Glyph(g: G, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val st = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun pt(x: Float, y: Float) = Offset(x * w, y * w)
        fun sz(x: Float, y: Float) = Size(x * w, y * w)
        when (g) {
            G.Select -> drawPath(Path().apply {
                moveTo(.28f * w, .12f * w); lineTo(.28f * w, .84f * w); lineTo(.46f * w, .66f * w)
                lineTo(.6f * w, .92f * w); lineTo(.72f * w, .86f * w); lineTo(.58f * w, .6f * w)
                lineTo(.82f * w, .58f * w); close()
            }, tint)
            G.Rect -> drawRect(tint, pt(.14f, .22f), sz(.72f, .56f), style = st)
            G.Oval -> drawOval(tint, pt(.12f, .2f), sz(.76f, .6f), style = st)
            G.Polygon -> drawPath(ngon(6, 0f, w), tint, style = st)
            G.Star -> drawPath(ngon(5, 0.45f, w), tint, style = st)
            G.Line -> drawLine(tint, pt(.18f, .8f), pt(.82f, .2f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            G.Undo, G.Redo -> scale(if (g == G.Redo) -1f else 1f, 1f, Offset(w / 2f, w / 2f)) {
                drawPath(Path().apply {
                    moveTo(.2f * w, .42f * w)
                    cubicTo(.45f * w, .2f * w, .82f * w, .3f * w, .82f * w, .62f * w)
                    cubicTo(.82f * w, .8f * w, .7f * w, .85f * w, .6f * w, .85f * w)
                }, tint, style = st)
                drawPath(Path().apply {
                    moveTo(.2f * w, .2f * w); lineTo(.2f * w, .42f * w); lineTo(.42f * w, .42f * w)
                }, tint, style = st)
            }
            G.Union -> {
                drawRect(tint, pt(.1f, .1f), sz(.5f, .5f))
                drawRect(tint, pt(.4f, .4f), sz(.5f, .5f))
            }
            G.Minus -> {
                drawRect(tint, pt(.1f, .1f), sz(.5f, .5f))
                drawRect(tint, pt(.4f, .4f), sz(.5f, .5f), style = st)
            }
            G.Intersect -> {
                drawRect(tint, pt(.1f, .1f), sz(.5f, .5f), style = st)
                drawRect(tint, pt(.4f, .4f), sz(.5f, .5f), style = st)
                drawRect(tint, pt(.4f, .4f), sz(.2f, .2f))
            }
            G.Exclude -> drawPath(Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(.1f * w, .1f * w, .6f * w, .6f * w))
                addRect(androidx.compose.ui.geometry.Rect(.4f * w, .4f * w, .9f * w, .9f * w))
            }, tint)
            G.Style -> {
                drawCircle(tint, radius = w * 0.26f, center = pt(.4f, .4f))
                drawCircle(tint, radius = w * 0.26f, center = pt(.62f, .62f), style = st)
            }
            G.Corner -> drawPath(Path().apply {
                moveTo(.2f * w, .85f * w); lineTo(.2f * w, .5f * w)
                cubicTo(.2f * w, .307f * w, .357f * w, .15f * w, .55f * w, .15f * w)
                lineTo(.85f * w, .15f * w)
            }, tint, style = st)
            G.Arrange -> {
                drawRect(tint, pt(.14f, .16f), sz(.7f, .18f))
                drawRect(tint, pt(.14f, .41f), sz(.44f, .18f))
                drawRect(tint, pt(.14f, .66f), sz(.58f, .18f))
            }
        }
    }
}
