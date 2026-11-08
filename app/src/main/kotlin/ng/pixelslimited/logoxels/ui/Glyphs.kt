package ng.pixelslimited.logoxels.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class G { Select, Rect, Oval, Polygon, Star, Line, Undo, Redo, Union, Minus, Intersect, Exclude, Style, Arrange, Corner, Pen, Knife, Layers, Nodes, Scissors, Grid, Text, Library, Trace, Eyedropper, Artboard, Sketch, Theme,
    AlignL, AlignC, AlignR, AlignT, AlignM, AlignB, RotL, RotR, FlipH, FlipV, ToFront, Forward, Backward, ToBack,
    Group, Ungroup, Clip, Unclip, DistH, DistV, Dup, SelAll, Trash, Move, Check,
    Eye, EyeOff, Lock, Unlock, Grip }

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
        fun bar(x: Float, y: Float, a: Float, b: Float) = drawRect(tint, pt(x, y), sz(a, b))
        fun box(x: Float, y: Float, a: Float, b: Float) = drawRect(tint, pt(x, y), sz(a, b), style = st)
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float, k: Float = .07f) =
            drawLine(tint, pt(x1, y1), pt(x2, y2), strokeWidth = w * k, cap = StrokeCap.Round)
        fun tri(a: Offset, b: Offset, c: Offset, fill: Boolean) = drawPath(
            Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() },
            tint, style = if (fill) Fill else st,
        )
        fun turn(cw: Boolean) {
            val r = .3f
            val sweep = if (cw) 250.0 else -250.0
            drawArc(tint, -120f, sweep.toFloat(), false, pt(.5f - r, .5f - r), sz(2 * r, 2 * r), style = st)
            val a1 = Math.toRadians(-120.0 + sweep)
            val ex = .5f + r * cos(a1).toFloat()
            val ey = .5f + r * sin(a1).toFloat()
            val d = if (cw) 1f else -1f
            val tx = -sin(a1).toFloat() * d
            val ty = cos(a1).toFloat() * d
            tri(pt(ex + tx * .14f, ey + ty * .14f), pt(ex + ty * .1f, ey - tx * .1f), pt(ex - ty * .1f, ey + tx * .1f), true)
        }
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
            G.Artboard -> {
                drawRect(tint, pt(.2f, .2f), sz(.6f, .6f), style = st)
                drawLine(tint, pt(.08f, .32f), pt(.08f, .08f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                drawLine(tint, pt(.08f, .08f), pt(.32f, .08f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                drawLine(tint, pt(.92f, .68f), pt(.92f, .92f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                drawLine(tint, pt(.92f, .92f), pt(.68f, .92f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
            }
            G.Sketch -> drawPath(Path().apply {
                moveTo(.12f * w, .7f * w)
                cubicTo(.2f * w, .2f * w, .45f * w, .2f * w, .5f * w, .5f * w)
                cubicTo(.55f * w, .8f * w, .78f * w, .8f * w, .88f * w, .3f * w)
            }, tint, style = st)
            G.Theme -> {
                drawCircle(tint, radius = w * 0.34f, center = pt(.5f, .5f), style = st)
                drawPath(Path().apply { arcTo(Rect(.16f * w, .16f * w, .84f * w, .84f * w), -90f, 180f, true); close() }, tint)
            }
            G.AlignL -> { ln(.14f, .12f, .14f, .88f); bar(.24f, .26f, .6f, .16f); bar(.24f, .58f, .36f, .16f) }
            G.AlignC -> { ln(.5f, .12f, .5f, .88f); bar(.2f, .26f, .6f, .16f); bar(.32f, .58f, .36f, .16f) }
            G.AlignR -> { ln(.86f, .12f, .86f, .88f); bar(.16f, .26f, .6f, .16f); bar(.4f, .58f, .36f, .16f) }
            G.AlignT -> { ln(.12f, .14f, .88f, .14f); bar(.26f, .24f, .16f, .6f); bar(.58f, .24f, .16f, .36f) }
            G.AlignM -> { ln(.12f, .5f, .88f, .5f); bar(.26f, .2f, .16f, .6f); bar(.58f, .32f, .16f, .36f) }
            G.AlignB -> { ln(.12f, .86f, .88f, .86f); bar(.26f, .16f, .16f, .6f); bar(.58f, .4f, .16f, .36f) }
            G.RotL -> turn(false)
            G.RotR -> turn(true)
            G.FlipH -> {
                drawLine(tint, pt(.5f, .1f), pt(.5f, .9f), strokeWidth = w * .06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * .1f, w * .08f)))
                tri(pt(.4f, .22f), pt(.4f, .78f), pt(.08f, .78f), false)
                tri(pt(.6f, .22f), pt(.6f, .78f), pt(.92f, .78f), true)
            }
            G.FlipV -> {
                drawLine(tint, pt(.1f, .5f), pt(.9f, .5f), strokeWidth = w * .06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * .1f, w * .08f)))
                tri(pt(.22f, .4f), pt(.78f, .4f), pt(.78f, .08f), false)
                tri(pt(.22f, .6f), pt(.78f, .6f), pt(.78f, .92f), true)
            }
            G.ToFront -> { box(.12f, .12f, .5f, .5f); bar(.38f, .38f, .5f, .5f) }
            G.ToBack -> { bar(.12f, .12f, .5f, .5f); box(.38f, .38f, .5f, .5f) }
            G.Forward -> { ln(.5f, .84f, .5f, .2f); ln(.5f, .2f, .3f, .4f); ln(.5f, .2f, .7f, .4f); bar(.2f, .86f, .6f, .08f) }
            G.Backward -> { ln(.5f, .16f, .5f, .8f); ln(.5f, .8f, .3f, .6f); ln(.5f, .8f, .7f, .6f); bar(.2f, .06f, .6f, .08f) }
            G.Group -> { box(.1f, .1f, .8f, .8f); bar(.24f, .24f, .22f, .22f); bar(.54f, .54f, .22f, .22f) }
            G.Ungroup -> { box(.1f, .1f, .36f, .36f); box(.54f, .54f, .36f, .36f) }
            G.Clip -> {
                val oval = Path().apply { addOval(Rect(.4f * w, .4f * w, .9f * w, .9f * w)) }
                clipPath(oval) { drawRect(tint, pt(.1f, .1f), sz(.55f, .55f)) }
                box(.1f, .1f, .55f, .55f)
                drawOval(tint, pt(.4f, .4f), sz(.5f, .5f), style = st)
            }
            G.Unclip -> { box(.1f, .1f, .55f, .55f); drawOval(tint, pt(.4f, .4f), sz(.5f, .5f), style = st) }
            G.DistH -> { ln(.1f, .12f, .1f, .88f); ln(.9f, .12f, .9f, .88f); bar(.4f, .28f, .2f, .44f) }
            G.DistV -> { ln(.12f, .1f, .88f, .1f); ln(.12f, .9f, .88f, .9f); bar(.28f, .4f, .44f, .2f) }
            G.Dup -> { box(.14f, .14f, .5f, .5f); box(.36f, .36f, .5f, .5f) }
            G.SelAll -> {
                drawRect(tint, pt(.14f, .14f), sz(.72f, .72f), style = Stroke(width = w * .08f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * .14f, w * .1f))))
                bar(.38f, .38f, .24f, .24f)
            }
            G.Trash -> {
                ln(.18f, .27f, .82f, .27f); ln(.4f, .27f, .4f, .16f); ln(.4f, .16f, .6f, .16f); ln(.6f, .16f, .6f, .27f)
                drawPath(Path().apply { moveTo(.28f * w, .36f * w); lineTo(.34f * w, .86f * w); lineTo(.66f * w, .86f * w); lineTo(.72f * w, .36f * w) }, tint, style = st)
            }
            G.Move -> {
                ln(.5f, .1f, .5f, .9f); ln(.1f, .5f, .9f, .5f)
                tri(pt(.5f, .06f), pt(.38f, .2f), pt(.62f, .2f), true); tri(pt(.5f, .94f), pt(.38f, .8f), pt(.62f, .8f), true)
                tri(pt(.06f, .5f), pt(.2f, .38f), pt(.2f, .62f), true); tri(pt(.94f, .5f), pt(.8f, .38f), pt(.8f, .62f), true)
            }
            G.Check -> { ln(.2f, .52f, .42f, .74f, .1f); ln(.42f, .74f, .82f, .26f, .1f) }
            G.Arrange -> {
                drawRect(tint, pt(.14f, .16f), sz(.7f, .18f))
                drawRect(tint, pt(.14f, .41f), sz(.44f, .18f))
                drawRect(tint, pt(.14f, .66f), sz(.58f, .18f))
            }
            G.Eye, G.EyeOff -> {
                drawPath(Path().apply {
                    moveTo(.08f * w, .5f * w)
                    cubicTo(.28f * w, .18f * w, .72f * w, .18f * w, .92f * w, .5f * w)
                    cubicTo(.72f * w, .82f * w, .28f * w, .82f * w, .08f * w, .5f * w)
                    close()
                }, tint, style = st)
                drawCircle(tint, w * .13f, pt(.5f, .5f))
                if (g == G.EyeOff) ln(.18f, .86f, .82f, .14f, .09f)
            }
            G.Lock, G.Unlock -> {
                if (g == G.Lock) bar(.22f, .46f, .56f, .4f) else box(.22f, .46f, .56f, .4f)
                drawPath(Path().apply {
                    moveTo(.34f * w, .46f * w)
                    lineTo(.34f * w, .34f * w)
                    if (g == G.Lock) {
                        cubicTo(.34f * w, .06f * w, .66f * w, .06f * w, .66f * w, .34f * w)
                        lineTo(.66f * w, .46f * w)
                    } else {
                        cubicTo(.34f * w, .06f * w, .66f * w, .06f * w, .66f * w, .24f * w)
                    }
                }, tint, style = st)
            }
            G.Grip -> {
                for (cx in listOf(.36f, .64f)) {
                    for (cy in listOf(.24f, .5f, .76f)) drawCircle(tint, w * .07f, pt(cx, cy))
                }
            }
        }
    }
}
