package ng.pixelslimited.logoxels.engine

import android.graphics.CornerPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Kind { RECT, ELLIPSE, POLYGON, STAR, PATH }

/** One vector object. Geometry lives in artboard units (1000 x 1000). */
class Item(
    val id: Long,
    var kind: Kind,
    var path: Path,
    var bounds: RectF,
    var fill: Int?,
    var stroke: Int?,
    var strokeWidth: Float,
    var corner: Float = 0f,
    var sides: Int = 5,
    var inner: Float = 0.5f,
) {
    val parametric: Boolean get() = kind != Kind.PATH

    fun copyWithId(newId: Long) =
        Item(newId, kind, Path(path), RectF(bounds), fill, stroke, strokeWidth, corner, sides, inner)

    fun copy() = copyWithId(id)

    fun box(): RectF = RectF().also { path.computeBounds(it, true) }

    fun rebuild() {
        if (parametric) path = buildShape(kind, bounds, sides, inner)
    }

    fun offset(dx: Float, dy: Float) {
        path.offset(dx, dy)
        bounds.offset(dx, dy)
    }

    /** Re-derive this item from [orig] transformed by [m] (used for live resize). */
    fun applyFrom(orig: Item, m: Matrix) {
        if (parametric) {
            val r = RectF()
            m.mapRect(r, orig.bounds)
            bounds = r
            rebuild()
        } else {
            val p = Path(orig.path)
            p.transform(m)
            path = p
        }
    }

    /** Geometry with corner rounding baked in. Rounds straight-edge corners. */
    fun renderPath(): Path {
        if (corner <= 0f || kind == Kind.ELLIPSE) return path
        val out = Path()
        Paint().apply {
            style = Paint.Style.FILL
            pathEffect = CornerPathEffect(corner)
        }.getFillPath(path, out)
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
        Kind.PATH -> Unit
    }
    return p
}
