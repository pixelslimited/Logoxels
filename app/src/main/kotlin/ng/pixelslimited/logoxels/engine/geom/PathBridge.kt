package ng.pixelslimited.logoxels.engine.geom

import android.graphics.Path
import androidx.graphics.path.PathIterator
import androidx.graphics.path.PathSegment

/** Converts between android.graphics.Path and the pure-Kotlin contour model. */
object PathBridge {
    private const val EPS = 1e-6

    fun toContours(path: Path): List<Contour> {
        val it = PathIterator(path, PathIterator.ConicEvaluation.AsQuadratics, 0.05f)
        val pts = FloatArray(8)
        val out = ArrayList<Contour>()
        var segs = ArrayList<Seg>()
        var start = P(0.0, 0.0)
        var cur = start
        var open = false

        fun pt(i: Int) = P(pts[i].toDouble(), pts[i + 1].toDouble())
        fun finish(closed: Boolean) {
            if (open && segs.isNotEmpty()) out.add(Contour(segs, closed))
            segs = ArrayList()
            open = false
        }
        fun begin(p: P) {
            if (!open) {
                open = true
                start = p
                cur = p
            }
        }

        while (it.hasNext()) {
            when (it.next(pts, 0)) {
                PathSegment.Type.Move -> {
                    finish(false)
                    start = pt(0)
                    cur = start
                    open = true
                }
                PathSegment.Type.Line -> {
                    begin(pt(0))
                    val b = pt(2)
                    if ((b - cur).len() > EPS) segs.add(Seg.line(cur, b))
                    cur = b
                }
                PathSegment.Type.Quadratic -> {
                    begin(pt(0))
                    val b = pt(4)
                    segs.add(Seg.quad(cur, pt(2), b))
                    cur = b
                }
                PathSegment.Type.Cubic -> {
                    begin(pt(0))
                    val b = pt(6)
                    segs.add(Seg.cubic(cur, pt(2), pt(4), b))
                    cur = b
                }
                PathSegment.Type.Conic -> {
                    // not produced with AsQuadratics; treat as a straight join to the end point
                    begin(pt(0))
                    val b = pt(4)
                    if ((b - cur).len() > EPS) segs.add(Seg.line(cur, b))
                    cur = b
                }
                PathSegment.Type.Close -> {
                    if (open) {
                        if ((cur - start).len() > EPS) segs.add(Seg.line(cur, start))
                        cur = start
                        finish(true)
                    }
                }
                PathSegment.Type.Done -> Unit
            }
        }
        finish(false)
        return out
    }

    fun toPath(contours: List<Contour>): Path {
        val p = Path()
        for (c in contours) {
            if (c.segs.isEmpty()) continue
            val f = c.segs[0].p0
            p.moveTo(f.x.toFloat(), f.y.toFloat())
            for (s in c.segs) {
                if (s.line) {
                    p.lineTo(s.p3.x.toFloat(), s.p3.y.toFloat())
                } else {
                    p.cubicTo(
                        s.p1.x.toFloat(), s.p1.y.toFloat(),
                        s.p2.x.toFloat(), s.p2.y.toFloat(),
                        s.p3.x.toFloat(), s.p3.y.toFloat(),
                    )
                }
            }
            if (c.closed) p.close()
        }
        return p
    }
}
