package ng.pixelslimited.logoxels.engine

import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import ng.pixelslimited.logoxels.engine.geom.PathBridge
import ng.pixelslimited.logoxels.engine.geom.Rounder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What a clipped shape looked like before clipping, so it can be released again. */
class ClipSource(val orig: Item, val mask: Item)

enum class Kind { RECT, ELLIPSE, POLYGON, STAR, PATH, TEXT }

/** One vector object. Geometry lives in artboard units. */
class Item(
    val id: Long,
    var kind: Kind,
    path: Path,
    var bounds: RectF,
    var fill: Int?,
    var stroke: Int?,
    var strokeWidth: Float,
    corner: Float = 0f,
    var sides: Int = 5,
    var inner: Float = 0.5f,
) {
    private var cache: Path? = null

    /** Sharp (unrounded) outline. */
    var path: Path = path
        set(v) {
            field = v
            cache = null
        }

    /** Radius applied to every corner that has no individual radius. */
    var corner: Float = corner
        set(v) {
            field = v
            cache = null
        }

    /** Individual corner radii, keyed by corner number (see [Rounder.nodes]). */
    val cornerR = HashMap<Int, Float>()

    /** 0 = not grouped; items sharing a non-zero id move and select together. */
    var groupId = 0L
    var visible = true
    var locked = false

    /** Whole-object transparency, 0 to 1. */
    var opacity = 1f

    /** Stroke style: dash 0 solid, 1 dashed, 2 dotted; cap 0 round, 1 flat, 2 square; join 0 round, 1 sharp, 2 bevel. */
    var dash = 0
    var cap = 0
    var join = 0

    /** When set, replaces the flat [fill] colour while drawing. */
    var gradient: Gradient? = null

    val parametric: Boolean get() = kind != Kind.PATH

    /** Set while [kind] is TEXT: the live text this outline is built from. */
    var text: TextSpec? = null

    /** Rotation, flip and resize of a live shape: the outline is [bounds] built as a shape, then moved by this. */
    val xf = Matrix()

    /** Set while this shape is trimmed by a clip; [clipDelta] is every move and resize since. */
    var clipSrc: ClipSource? = null
    val clipDelta = Matrix()

    fun radiusAt(i: Int): Float = cornerR[i] ?: corner

    fun setCornerAt(i: Int, v: Float) {
        cornerR[i] = v
        cache = null
    }

    fun clearCornerOverrides() {
        cornerR.clear()
        cache = null
    }

    fun copyWithId(newId: Long) =
        Item(newId, kind, Path(path), RectF(bounds), fill, stroke, strokeWidth, corner, sides, inner)
            .also {
                it.cornerR.putAll(cornerR)
                it.groupId = groupId
                it.visible = visible
                it.locked = locked
                it.gradient = gradient?.copy()
                it.xf.set(xf)
                it.opacity = opacity
                it.dash = dash
                it.cap = cap
                it.join = join
                it.text = text?.copy()
                it.clipSrc = clipSrc
                it.clipDelta.set(clipDelta)
            }

    fun copy() = copyWithId(id)

    /** Sets width, caps, joins and dashes on a stroke [p]; colour is left to the caller. */
    fun configureStroke(p: Paint) {
        val w = maxOf(strokeWidth, 1f)
        p.strokeWidth = strokeWidth
        p.strokeCap = when {
            dash == 2 -> Paint.Cap.ROUND
            cap == 1 -> Paint.Cap.BUTT
            cap == 2 -> Paint.Cap.SQUARE
            else -> Paint.Cap.ROUND
        }
        p.strokeJoin = when (join) {
            1 -> Paint.Join.MITER
            2 -> Paint.Join.BEVEL
            else -> Paint.Join.ROUND
        }
        p.strokeMiter = 4f
        p.pathEffect = when (dash) {
            1 -> DashPathEffect(floatArrayOf(w * 3f, w * 2f), 0f)
            2 -> DashPathEffect(floatArrayOf(0.01f, w * 2f), 0f)
            else -> null
        }
    }

    fun label(): String = when (kind) {
        Kind.RECT -> "Rectangle"
        Kind.ELLIPSE -> "Oval"
        Kind.POLYGON -> "Polygon"
        Kind.STAR -> "Star"
        Kind.PATH -> "Path"
        Kind.TEXT -> "Text"
    } + " " + id

    fun box(): RectF = RectF().also { path.computeBounds(it, true) }

    fun rebuild() {
        if (!parametric) return
        val base = if (kind == Kind.TEXT) text?.let { TextLayout.build(it) } ?: Path() else buildShape(kind, bounds, sides, inner)
        base.transform(xf)
        path = base
    }

    /** Turns a live shape or text into a plain path: the outline stays, the editable properties go. */
    fun flatten() {
        kind = Kind.PATH
        text = null
    }

    fun offset(dx: Float, dy: Float) {
        path.offset(dx, dy)
        if (parametric) xf.postTranslate(dx, dy)
        if (clipSrc != null) clipDelta.postTranslate(dx, dy)
        cache?.offset(dx, dy)
    }

    /** Re-derive this item from [orig] transformed by [m] (live resize, rotate, flip). Live shapes stay live. */
    fun applyFrom(orig: Item, m: Matrix) {
        if (parametric) {
            bounds = RectF(orig.bounds)
            xf.set(orig.xf)
            xf.postConcat(m)
            rebuild()
        } else {
            val p = Path(orig.path)
            p.transform(m)
            path = p
        }
        if (clipSrc != null) {
            clipDelta.set(orig.clipDelta)
            clipDelta.postConcat(m)
        }
    }

    /** Corner points of the sharp outline, for the corner-editing dots. */
    fun cornerNodes(): List<Rounder.Node> = Rounder.nodes(PathBridge.toContours(path))

    /** Outline with corner rounding applied. Ovals have no corners, so they are returned as is. */
    fun renderPath(): Path {
        val any = corner > 0f || cornerR.values.any { it > 0f }
        if (kind == Kind.ELLIPSE || !any) return path
        cache?.let { return it }
        val out = PathBridge.toPath(Rounder.round(PathBridge.toContours(path)) { radiusAt(it).toDouble() })
        out.fillType = path.fillType
        cache = out
        return out
    }
}

fun buildShape(kind: Kind, r: RectF, sides: Int, inner: Float): Path {
    val p = Path()
    when (kind) {
        Kind.RECT -> p.addRect(r, Path.Direction.CW)
        Kind.ELLIPSE -> p.addOval(r, Path.Direction.CW)
        Kind.POLYGON, Kind.STAR -> {
            val cx = r.centerX()
            val cy = r.centerY()
            val rx = r.width() / 2f
            val ry = r.height() / 2f
            val n = if (kind == Kind.STAR) sides * 2 else sides
            for (i in 0 until n) {
                val a = -PI / 2 + i * 2 * PI / n
                val k = if (kind == Kind.STAR && i % 2 == 1) inner else 1f
                val x = cx + (rx * k * cos(a)).toFloat()
                val y = cy + (ry * k * sin(a)).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
        }
        Kind.PATH, Kind.TEXT -> Unit
    }
    return p
}
