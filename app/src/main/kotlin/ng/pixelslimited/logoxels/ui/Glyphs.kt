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

enum class G { Select, Rect, Oval, Polygon, Star, Line, Undo, Redo, Union, Minus, Intersect, Exclude, Style, Arrange, Corner, Pen, Knife, Layers, Nodes, Scissors, Grid, Text, Library, Trace, Eyedropper }

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
            G.Pen -> drawPath(Path().apply {
                moveTo(.18f * w, .86f * w); lineTo(.3f * w, .56f * w); lineTo(.7f * w, .16f * w)
                lineTo(.86f * w, .32f * w); lineTo(.46f * w, .72f * w); close()
            }, tint, style = st)
            G.Knife -> drawPath(Path().apply {
                moveTo(.15f * w, .85f * w); lineTo(.85f * w, .15f * w); lineTo(.85f * w, .45f * w)
                lineTo(.45f * w, .85f * w); close()
            }, tint, style = st)
            G.Layers -> {
                drawRect(tint, pt(.2f, .6f), sz(.6f, .22f), style = st)
                drawRect(tint, pt(.2f, .4f), sz(.6f, .22f), style = st)
                drawRect(tint, pt(.2f, .2f), sz(.6f, .22f), style = st)
            }
            G.Corner -> drawPath(Path().apply {
                moveTo(.2f * w, .85f * w); lineTo(.2f * w, .5f * w)
                cubicTo(.2f * w, .307f * w, .357f * w, .15f * w, .55f * w, .15f * w)
                lineTo(.85f * w, .15f * w)
            }, tint, style = st)
            G.Nodes -> {
                drawPath(Path().apply {
                    moveTo(.18f * w, .75f * w)
                    cubicTo(.3f * w, .2f * w, .65f * w, .2f * w, .82f * w, .7f * w)
                }, tint, style = st)
                drawRect(tint, pt(.1f, .67f), sz(.16f, .16f))
                drawRect(tint, pt(.74f, .62f), sz(.16f, .16f))
                drawCircle(tint, radius = w * 0.08f, center = pt(.5f, .33f))
            }
            G.Scissors -> {
                drawLine(tint, pt(.3f, .72f), pt(.82f, .16f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawLine(tint, pt(.7f, .72f), pt(.18f, .16f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.11f, center = pt(.27f, .8f), style = st)
                drawCircle(tint, radius = w * 0.11f, center = pt(.73f, .8f), style = st)
            }
            G.Grid -> {
                for (f in listOf(.3f, .7f)) {
                    drawLine(tint, pt(f, .14f), pt(f, .86f), strokeWidth = w * 0.07f)
                    drawLine(tint, pt(.14f, f), pt(.86f, f), strokeWidth = w * 0.07f)
                }
            }
            G.Text -> {
                drawLine(tint, pt(.2f, .2f), pt(.8f, .2f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(tint, pt(.5f, .2f), pt(.5f, .84f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(tint, pt(.36f, .84f), pt(.64f, .84f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            }
            G.Library -> {
                drawRect(tint, pt(.12f, .12f), sz(.3f, .3f), style = st)
                drawCircle(tint, radius = w * 0.17f, center = pt(.7f, .27f), style = st)
                drawPath(Path().apply {
                    moveTo(.27f * w, .58f * w); lineTo(.42f * w, .88f * w); lineTo(.12f * w, .88f * w); close()
                }, tint, style = st)
                drawLine(tint, pt(.58f, .62f), pt(.88f, .62f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawLine(tint, pt(.58f, .8f), pt(.88f, .8f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            }
            G.Trace -> {
                drawRect(tint, pt(.12f, .18f), sz(.76f, .64f), style = st)
                drawPath(Path().apply {
                    moveTo(.18f * w, .76f * w); lineTo(.42f * w, .44f * w); lineTo(.56f * w, .62f * w)
                    lineTo(.66f * w, .5f * w); lineTo(.82f * w, .76f * w)
                }, tint, style = st)
            }
            G.Eyedropper -> {
                drawLine(tint, pt(.26f, .74f), pt(.6f, .4f), strokeWidth = w * 0.15f, cap = StrokeCap.Round)
                drawLine(tint, pt(.6f, .4f), pt(.8f, .2f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
                drawLine(tint, pt(.7f, .5f), pt(.5f, .3f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.06f, center = pt(.18f, .84f))
            }
            G.Arrange -> {
                drawRect(tint, pt(.14f, .16f), sz(.7f, .18f))
                drawRect(tint, pt(.14f, .41f), sz(.44f, .18f))
                drawRect(tint, pt(.14f, .66f), sz(.58f, .18f))
            }
        }
    }
}
