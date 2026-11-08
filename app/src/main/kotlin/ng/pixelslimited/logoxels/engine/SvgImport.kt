package ng.pixelslimited.logoxels.engine

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.util.Xml
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

/** One drawable from an SVG, already in the SVG's own coordinate space. */
class SvgShape(val path: Path, val fill: Int?, val stroke: Int?, val strokeWidth: Float, val opacity: Float)

/** [w] x [h] is the SVG's drawing size; [skipped] counts elements that were left out (text, images, gradients, clips). */
class SvgDoc(val w: Float, val h: Float, val shapes: List<SvgShape>, val skipped: Int)

/** Reads the common subset of SVG: paths, basic shapes, groups, transforms and flat colours. */
object SvgImport {
    private class St(
        val m: Matrix,
        val fill: Int?,
        val stroke: Int?,
        val sw: Float,
        val fillOp: Float,
        val strokeOp: Float,
        val opacity: Float,
        val evenOdd: Boolean,
    )

    private val ignored = setOf("defs", "clipPath", "mask", "symbol", "metadata", "title", "desc", "style", "script", "filter", "pattern", "marker")
    private val unsupported = setOf("text", "image", "use", "foreignObject", "switch")
    private val gradients = setOf("linearGradient", "radialGradient")
    private val named = mapOf(
        "black" to 0xFF000000.toInt(), "white" to 0xFFFFFFFF.toInt(), "red" to 0xFFFF0000.toInt(),
        "green" to 0xFF008000.toInt(), "lime" to 0xFF00FF00.toInt(), "blue" to 0xFF0000FF.toInt(),
        "yellow" to 0xFFFFFF00.toInt(), "gray" to 0xFF808080.toInt(), "grey" to 0xFF808080.toInt(),
        "orange" to 0xFFFFA500.toInt(), "purple" to 0xFF800080.toInt(), "pink" to 0xFFFFC0CB.toInt(),
        "brown" to 0xFFA52A2A.toInt(), "cyan" to 0xFF00FFFF.toInt(), "aqua" to 0xFF00FFFF.toInt(),
        "magenta" to 0xFFFF00FF.toInt(), "fuchsia" to 0xFFFF00FF.toInt(), "navy" to 0xFF000080.toInt(),
        "teal" to 0xFF008080.toInt(), "silver" to 0xFFC0C0C0.toInt(), "maroon" to 0xFF800000.toInt(),
        "olive" to 0xFF808000.toInt(),
    )
    private val num = Regex("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
    private val call = Regex("(\\w+)\\s*\\(([^)]*)\\)")

    private fun nums(s: String?): List<Float> = if (s == null) emptyList() else num.findAll(s).map { it.value.toFloat() }.toList()

    private fun len(s: String?, def: Float = 0f): Float = nums(s).firstOrNull() ?: def

    /** Returns null when [text] is not an SVG or holds nothing that can be drawn. */
    fun parse(text: String): SvgDoc? {
        val p = Xml.newPullParser()
        try {
            p.setInput(StringReader(text.removePrefix("\uFEFF")))
            var ev = p.eventType
            val stack = ArrayList<St>()
            val shapes = ArrayList<SvgShape>()
            var skipped = 0
            var skipDepth = -1
            var vbW = 0f
            var vbH = 0f
            var rootSeen = false
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    val name = p.name
                    if (skipDepth >= 0) {
                        // inside an ignored subtree
                    } else if (!rootSeen) {
                        if (name != "svg") return null
                        rootSeen = true
                        val base = Matrix()
                        val vb = nums(p.getAttributeValue(null, "viewBox"))
                        if (vb.size == 4 && vb[2] > 0f && vb[3] > 0f) {
                            vbW = vb[2]; vbH = vb[3]
                            base.setTranslate(-vb[0], -vb[1])
                        } else {
                            vbW = len(p.getAttributeValue(null, "width"))
                            vbH = len(p.getAttributeValue(null, "height"))
                        }
                        stack.add(style(p, St(base, 0xFF000000.toInt(), null, 1f, 1f, 1f, 1f, false)))
                    } else if (name in ignored) {
                        skipDepth = p.depth
                    } else if (name in gradients || name in unsupported) {
                        skipped++
                        skipDepth = p.depth
                    } else if (name == "g" || name == "a") {
                        stack.add(style(p, stack.last()))
                    } else {
                        val st = style(p, stack.last())
                        val path = build(name, p)
                        if (path != null) {
                            // a line has no inside, so only its stroke counts
                            val f = if (name == "line") null else st.fill?.let { withAlpha(it, st.fillOp) }
                            val s = st.stroke?.let { withAlpha(it, st.strokeOp) }
                            if (f != null || s != null) {
                                path.transform(st.m)
                                if (st.evenOdd) path.fillType = Path.FillType.EVEN_ODD
                                if (isUrl(p.getAttributeValue(null, "fill")) || isUrl(styleMap(p)["fill"])) skipped++
                                shapes.add(SvgShape(path, f, s, st.sw * scale(st.m), st.opacity))
                            }
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    if (skipDepth >= 0) {
                        if (p.depth == skipDepth) skipDepth = -1
                    } else if (p.name == "g" || p.name == "a") {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex)
                    }
                }
                ev = p.next()
            }
            if (!rootSeen || shapes.isEmpty()) return null
            if (vbW <= 0f || vbH <= 0f) {
                val r = RectF()
                val b = RectF()
                var first = true
                for (s in shapes) {
                    s.path.computeBounds(b, true)
                    if (first) { r.set(b); first = false } else r.union(b)
                }
                if (r.width() <= 0f || r.height() <= 0f) return null
                // the drawing is not anchored at 0,0: shift it there so placement can centre it
                val shift = Matrix().apply { setTranslate(-r.left, -r.top) }
                for (s in shapes) s.path.transform(shift)
                vbW = r.width(); vbH = r.height()
            }
            return SvgDoc(vbW, vbH, shapes, skipped)
        } catch (e: Exception) {
            return null
        }
    }

    private fun withAlpha(c: Int, a: Float): Int {
        val base = (c ushr 24) / 255f
        val v = (base * a.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (c and 0xFFFFFF) or (v shl 24)
    }

    private fun scale(m: Matrix): Float {
        val v = FloatArray(9)
        m.getValues(v)
        return sqrt(abs(v[0] * v[4] - v[1] * v[3]))
    }

    private fun isUrl(v: String?) = v != null && v.trim().startsWith("url(")

    private fun styleMap(p: XmlPullParser): Map<String, String> {
        val s = p.getAttributeValue(null, "style") ?: return emptyMap()
        val out = HashMap<String, String>()
        for (part in s.split(';')) {
            val i = part.indexOf(':')
            if (i > 0) out[part.substring(0, i).trim()] = part.substring(i + 1).trim()
        }
        return out
    }

    /** The style in force inside this element: the parent's, changed by the element's own attributes. */
    private fun style(p: XmlPullParser, parent: St): St {
        val sm = styleMap(p)
        fun prop(n: String): String? = sm[n] ?: p.getAttributeValue(null, n)
        val m = Matrix(parent.m)
        p.getAttributeValue(null, "transform")?.let { m.preConcat(transform(it)) }
        val fill = prop("fill")?.let { if (isUrl(it)) 0xFF808080.toInt() else color(it, parent.fill) } ?: parent.fill
        val stroke = prop("stroke")?.let { if (isUrl(it)) 0xFF808080.toInt() else color(it, parent.stroke) } ?: parent.stroke
        return St(
            m, fill, stroke,
            prop("stroke-width")?.let { len(it, parent.sw) } ?: parent.sw,
            prop("fill-opacity")?.let { len(it, 1f) } ?: parent.fillOp,
            prop("stroke-opacity")?.let { len(it, 1f) } ?: parent.strokeOp,
            parent.opacity * (prop("opacity")?.let { len(it, 1f) } ?: 1f).coerceIn(0f, 1f),
            prop("fill-rule")?.let { it.trim() == "evenodd" } ?: parent.evenOdd,
        )
    }

    private fun color(v: String, inherited: Int?): Int? {
        val s = v.trim().lowercase()
        if (s == "none" || s == "transparent") return null
        if (s == "currentcolor") return 0xFF000000.toInt()
        if (s == "inherit") return inherited
        named[s]?.let { return it }
        if (s.startsWith("#")) {
            val h = s.substring(1)
            return try {
                when (h.length) {
                    3 -> (0xFF000000.toInt()) or (h.map { "$it$it" }.joinToString("").toInt(16))
                    6 -> (0xFF000000.toInt()) or h.toInt(16)
                    8 -> (h.substring(6).toInt(16) shl 24) or h.substring(0, 6).toInt(16)
                    else -> 0xFF000000.toInt()
                }
            } catch (e: NumberFormatException) {
                0xFF000000.toInt()
            }
        }
        if (s.startsWith("rgb")) {
            val n = nums(s)
            if (n.size >= 3) {
                val pct = s.contains('%')
                fun ch(x: Float) = (if (pct) x * 2.55f else x).toInt().coerceIn(0, 255)
                return (0xFF shl 24) or (ch(n[0]) shl 16) or (ch(n[1]) shl 8) or ch(n[2])
            }
        }
        return 0xFF000000.toInt()
    }

    private fun transform(s: String): Matrix {
        val out = Matrix()
        for (c in call.findAll(s)) {
            val a = nums(c.groupValues[2])
            val t = Matrix()
            when (c.groupValues[1]) {
                "matrix" -> if (a.size == 6) t.setValues(floatArrayOf(a[0], a[2], a[4], a[1], a[3], a[5], 0f, 0f, 1f))
                "translate" -> if (a.isNotEmpty()) t.setTranslate(a[0], a.getOrElse(1) { 0f })
                "scale" -> if (a.isNotEmpty()) t.setScale(a[0], a.getOrElse(1) { a[0] })
                "rotate" -> if (a.isNotEmpty()) {
                    if (a.size >= 3) t.setRotate(a[0], a[1], a[2]) else t.setRotate(a[0])
                }
                "skewX" -> if (a.isNotEmpty()) t.setSkew(tan(Math.toRadians(a[0].toDouble())).toFloat(), 0f)
                "skewY" -> if (a.isNotEmpty()) t.setSkew(0f, tan(Math.toRadians(a[0].toDouble())).toFloat())
            }
            out.preConcat(t)
        }
        return out
    }

    private fun attr(p: XmlPullParser, n: String) = len(p.getAttributeValue(null, n))

    /** The outline of a shape element in its own coordinates, or null for elements that draw nothing. */
    private fun build(name: String, p: XmlPullParser): Path? {
        val path = Path()
        when (name) {
            "path" -> {
                val d = p.getAttributeValue(null, "d") ?: return null
                return try {
                    PathParser.createPathFromPathData(d)
                } catch (e: RuntimeException) {
                    null
                }
            }
            "rect" -> {
                val w = attr(p, "width")
                val h = attr(p, "height")
                if (w <= 0f || h <= 0f) return null
                val x = attr(p, "x")
                val y = attr(p, "y")
                val rxA = p.getAttributeValue(null, "rx")
                val ryA = p.getAttributeValue(null, "ry")
                val rx = (if (rxA != null) len(rxA) else len(ryA)).coerceIn(0f, w / 2f)
                val ry = (if (ryA != null) len(ryA) else len(rxA)).coerceIn(0f, h / 2f)
                path.addRoundRect(RectF(x, y, x + w, y + h), rx, ry, Path.Direction.CW)
            }
            "circle" -> {
                val r = attr(p, "r")
                if (r <= 0f) return null
                path.addCircle(attr(p, "cx"), attr(p, "cy"), r, Path.Direction.CW)
            }
            "ellipse" -> {
                val rx = attr(p, "rx")
                val ry = attr(p, "ry")
                if (rx <= 0f || ry <= 0f) return null
                val cx = attr(p, "cx")
                val cy = attr(p, "cy")
                path.addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW)
            }
            "line" -> {
                path.moveTo(attr(p, "x1"), attr(p, "y1"))
                path.lineTo(attr(p, "x2"), attr(p, "y2"))
            }
            "polygon", "polyline" -> {
                val pts = nums(p.getAttributeValue(null, "points"))
                if (pts.size < 4) return null
                path.moveTo(pts[0], pts[1])
                var i = 2
                while (i + 1 < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
                if (name == "polygon") path.close()
            }
            else -> return null
        }
        return path
    }
}
