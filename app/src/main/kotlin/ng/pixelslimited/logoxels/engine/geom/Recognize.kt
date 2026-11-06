package ng.pixelslimited.logoxels.engine.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** What a hand-drawn stroke was recognised as. Angles are radians, clockwise on screen. */
sealed class Shape {
    class Line(val a: P, val b: P) : Shape()
    class Oval(val c: P, val rx: Double, val ry: Double, val angle: Double) : Shape()
    class Rect(val c: P, val w: Double, val h: Double, val angle: Double) : Shape()

    /** Regular polygon; [angle] turns it away from "first corner straight up". */
    class Regular(val c: P, val r: Double, val sides: Int, val angle: Double) : Shape()
    class Star(val c: P, val r: Double, val inner: Double, val points: Int, val angle: Double) : Shape()
    class Poly(val pts: List<P>, val closed: Boolean) : Shape()
    class Curve(val contour: Contour) : Shape()

    fun label(): String = when (this) {
        is Line -> "Line"
        is Oval -> if (abs(rx - ry) < 1e-9) "Circle" else "Oval"
        is Rect -> if (abs(w - h) < 1e-9) "Square" else "Rectangle"
        is Regular -> when (sides) {
            3 -> "Triangle"
            5 -> "Pentagon"
            6 -> "Hexagon"
            8 -> "Octagon"
            else -> "$sides-sided polygon"
        }
        is Star -> "$points-point star"
        is Poly -> "Straightened shape"
        is Curve -> "Smoothed curve"
    }
}

/** Turns a finger-drawn stroke into a clean shape. */
object Recognize {
    private const val STRAIGHT = 0.085
    private const val SNAP = 8.0 * PI / 180.0

    private fun dist(a: P, b: P) = (a - b).len()

    private fun pdist(p: P, a: P, b: P): Double {
        val d = b - a
        val l = d.len()
        if (l < 1e-12) return dist(p, a)
        return abs(d.x * (p.y - a.y) - d.y * (p.x - a.x)) / l
    }

    private fun clean(raw: List<P>): List<P> {
        val out = ArrayList<P>()
        for (p in raw) if (out.isEmpty() || dist(out.last(), p) > 1e-6) out.add(p)
        return out
    }

    private fun resample(pts: List<P>, n: Int, closed: Boolean): List<P> {
        val p = if (closed) pts + pts[0] else pts
        val cum = DoubleArray(p.size)
        for (i in 1 until p.size) cum[i] = cum[i - 1] + dist(p[i - 1], p[i])
        val total = cum.last()
        val count = if (closed) n else n - 1
        val out = ArrayList<P>(n)
        var j = 1
        for (k in 0 until n) {
            val d = total * k / count
            while (j < p.size - 1 && cum[j] < d) j++
            val seg = cum[j] - cum[j - 1]
            val t = if (seg == 0.0) 0.0 else (d - cum[j - 1]) / seg
            out.add(p[j - 1].lerp(p[j], t))
        }
        return out
    }

    private fun smooth3(p: List<P>, closed: Boolean): List<P> {
        val n = p.size
        return List(n) { i ->
            if (!closed && (i == 0 || i == n - 1)) p[i]
            else (p[(i + n - 1) % n] + p[i] + p[(i + 1) % n]) * (1.0 / 3.0)
        }
    }

    private fun dp(p: List<P>, i0: Int, i1: Int, tol: Double, keep: MutableSet<Int>) {
        var worst = -1.0
        var idx = -1
        for (i in i0 + 1 until i1) {
            val d = pdist(p[i], p[i0], p[i1])
            if (d > worst) { worst = d; idx = i }
        }
        if (idx >= 0 && worst > tol) {
            keep.add(idx)
            dp(p, i0, idx, tol, keep)
            dp(p, idx, i1, tol, keep)
        }
    }

    /** Corner indices of a closed loop; the forced start and far points are pruned later if they are not corners. */
    private fun closedVertices(p: List<P>, tol: Double): List<Int> {
        val n = p.size
        val ext = p + p[0]
        var far = 1
        var best = -1.0
        for (i in 0 until n) {
            val d = dist(p[i], p[0])
            if (d > best) { best = d; far = i }
        }
        val keep = sortedSetOf(0, far)
        dp(ext, 0, far, tol, keep)
        dp(ext, far, n, tol, keep)
        return keep.toList()
    }

    private fun prune(p: List<P>, idxs: List<Int>, tol: Double, closed: Boolean): MutableList<Int> {
        val v = idxs.toMutableList()
        val minKeep = if (closed) 3 else 2
        while (v.size > minKeep) {
            var worst = -1
            var wd = tol
            for (j in v.indices) {
                if (!closed && (j == 0 || j == v.size - 1)) continue
                val a = p[v[(j + v.size - 1) % v.size]]
                val c = p[v[(j + 1) % v.size]]
                val d = pdist(p[v[j]], a, c)
                if (d < wd) { wd = d; worst = j }
            }
            if (worst < 0) break
            v.removeAt(worst)
        }
        return v
    }

    /** Largest gap between the samples of an edge and its straight chord, as a share of the chord. */
    private fun straightness(p: List<P>, idxs: List<Int>, closed: Boolean): Double {
        val n = p.size
        val m = idxs.size
        var worst = 0.0
        for (e in 0 until (if (closed) m else m - 1)) {
            val i = idxs[e]
            val j = idxs[(e + 1) % m]
            val a = p[i]
            val b = p[j]
            val l = dist(a, b)
            if (l < 1e-9) return 9.0
            var k = i
            while (true) {
                worst = max(worst, pdist(p[k], a, b) / l)
                if (k == j) break
                k = (k + 1) % n
            }
        }
        return worst
    }

    private fun reduce(angle: Double, step: Double): Double {
        var a = angle % step
        if (a > step / 2) a -= step
        if (a < -step / 2) a += step
        return a
    }

    /** Smooth curve through the stroke (no corners), closed or open. */
    fun smooth(raw: List<P>): Shape.Curve? {
        val pts = clean(raw)
        if (pts.size < 3) return null
        val closed = isClosed(pts)
        val r = smooth3(resample(pts, 40, closed), closed)
        return Shape.Curve(catmull(r, closed))
    }

    private fun isClosed(pts: List<P>): Boolean {
        var l = 0.0
        for (i in 1 until pts.size) l += dist(pts[i - 1], pts[i])
        return dist(pts.first(), pts.last()) < 0.2 * l
    }

    private fun catmull(pts: List<P>, closed: Boolean): Contour {
        val n = pts.size
        fun at(i: Int) = if (closed) pts[((i % n) + n) % n] else pts[i.coerceIn(0, n - 1)]
        val segs = ArrayList<Seg>()
        for (i in 0 until (if (closed) n else n - 1)) {
            val a = at(i)
            val b = at(i + 1)
            segs.add(Seg.cubic(a, a + (at(i + 1) - at(i - 1)) * (1.0 / 6.0), b - (at(i + 2) - at(i)) * (1.0 / 6.0), b))
        }
        return Contour(segs, closed)
    }

    fun shape(raw: List<P>): Shape? {
        val pts = clean(raw)
        if (pts.size < 4) return null
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val diag = hypot(maxX - minX, maxY - minY)
        if (diag < 1e-6) return null
        return if (isClosed(pts)) closedShape(pts, diag) else openShape(pts, diag)
    }

    private fun openShape(pts: List<P>, diag: Double): Shape? {
        val a = pts.first()
        val b = pts.last()
        val chord = dist(a, b)
        if (chord > 1e-6 && pts.maxOf { pdist(it, a, b) } < 0.05 * chord) {
            val ang = atan2(b.y - a.y, b.x - a.x)
            val snapped = (ang / (PI / 4)).roundToInt() * (PI / 4)
            val use = if (abs(ang - snapped) < 5.0 * PI / 180.0) snapped else ang
            return Shape.Line(a, P(a.x + chord * cos(use), a.y + chord * sin(use)))
        }
        val dense = smooth3(smooth3(resample(pts, 240, false), false), false)
        val tol = 0.05 * diag
        val keep = sortedSetOf(0, dense.size - 1)
        dp(dense, 0, dense.size - 1, tol, keep)
        val idxs = prune(dense, keep.toList(), 0.02 * diag, false)
        if (idxs.size in 3..8 && straightness(dense, idxs, false) < STRAIGHT) {
            return Shape.Poly(snapEdges(idxs.map { dense[it] }, false), false)
        }
        return smooth(pts)
    }

    private fun closedShape(pts: List<P>, diag: Double): Shape? {
        val dense = smooth3(smooth3(resample(pts, 240, true), true), true)
        val idxs = prune(dense, closedVertices(dense, 0.05 * diag), 0.02 * diag, true)
        val k = idxs.size
        if (k in 3..14 && straightness(dense, idxs, true) < STRAIGHT) {
            val v = idxs.map { dense[it] }
            polygon(v)?.let { return it }
        }
        ellipse(resample(pts, 72, true))?.let { return it }
        return smooth(pts)
    }

    private fun polygon(v: List<P>): Shape? {
        val k = v.size
        var cx = 0.0
        var cy = 0.0
        for (p in v) { cx += p.x; cy += p.y }
        val c = P(cx / k, cy / k)
        val edges = List(k) { dist(v[it], v[(it + 1) % k]) }
        if (k == 4) rectangle(v, edges, c)?.let { return it }
        val radii = v.map { dist(it, c) }
        if (k >= 6 && k % 2 == 0) {
            val even = (0 until k step 2).map { radii[it] }.average()
            val odd = (1 until k step 2).map { radii[it] }.average()
            val ok = (0 until k).all { abs(radii[it] - (if (it % 2 == 0) even else odd)) < 0.25 * (if (it % 2 == 0) even else odd) }
            val hi = max(even, odd)
            val lo = min(even, odd)
            if (ok && lo / hi > 0.2 && lo / hi < 0.78) {
                val outer = if (even >= odd) 0 else 1
                val rot = reduce(atan2(v[outer].y - c.y, v[outer].x - c.x) + PI / 2, 2 * PI / (k / 2))
                return Shape.Star(c, hi, lo / hi, k / 2, if (abs(rot) < SNAP) 0.0 else rot)
            }
        }
        val meanEdge = edges.average()
        val meanR = radii.average()
        if (edges.all { abs(it - meanEdge) < 0.25 * meanEdge } && radii.all { abs(it - meanR) < 0.2 * meanR }) {
            val rot = reduce(atan2(v[0].y - c.y, v[0].x - c.x) + PI / 2, 2 * PI / k)
            return Shape.Regular(c, meanR, k, if (abs(rot) < SNAP) 0.0 else rot)
        }
        return Shape.Poly(snapEdges(v, true), true)
    }

    private fun rectangle(v: List<P>, e: List<Double>, c: P): Shape? {
        for (i in 0 until 4) {
            val a = v[(i + 3) % 4] - v[i]
            val b = v[(i + 1) % 4] - v[i]
            val ang = Math.toDegrees(acos(((a dot b) / (a.len() * b.len())).coerceIn(-1.0, 1.0)))
            if (abs(ang - 90.0) >= 22.0) return null
        }
        if (e[0] / e[2] > 1.3 || e[2] / e[0] > 1.3 || e[1] / e[3] > 1.3 || e[3] / e[1] > 1.3) return null
        val d0 = (v[1] - v[0]).unit()
        val d2 = (v[2] - v[3]).unit()
        var w = (e[0] + e[2]) / 2
        var h = (e[1] + e[3]) / 2
        val dir = (d0 + d2).unit()
        var phi = atan2(dir.y, dir.x)
        val off = reduce(phi, PI / 2)
        if (abs(off) < SNAP) {
            if (((phi - off) / (PI / 2)).roundToInt() % 2 != 0) { val t = w; w = h; h = t }
            phi = 0.0
        } else {
            phi = reduce(phi, PI)
        }
        if (abs(w - h) < 0.1 * max(w, h)) { w = (w + h) / 2; h = w }
        return Shape.Rect(c, w, h, phi)
    }

    /** Makes near-horizontal and near-vertical edges exactly level. */
    private fun snapEdges(v: List<P>, closed: Boolean): List<P> {
        val xs = v.map { it.x }.toDoubleArray()
        val ys = v.map { it.y }.toDoubleArray()
        val limit = 7.0 * PI / 180.0
        for (e in 0 until (if (closed) v.size else v.size - 1)) {
            val j = (e + 1) % v.size
            val ang = abs(atan2(ys[j] - ys[e], xs[j] - xs[e]))
            if (ang < limit || ang > PI - limit) {
                val m = (ys[e] + ys[j]) / 2; ys[e] = m; ys[j] = m
            } else if (abs(ang - PI / 2) < limit) {
                val m = (xs[e] + xs[j]) / 2; xs[e] = m; xs[j] = m
            }
        }
        return List(v.size) { P(xs[it], ys[it]) }
    }

    private fun ellipse(p: List<P>): Shape? {
        val n = p.size
        val cx = p.sumOf { it.x } / n
        val cy = p.sumOf { it.y } / n
        val cxx = p.sumOf { (it.x - cx) * (it.x - cx) } / n
        val cyy = p.sumOf { (it.y - cy) * (it.y - cy) } / n
        val cxy = p.sumOf { (it.x - cx) * (it.y - cy) } / n
        val th = 0.5 * atan2(2 * cxy, cxx - cyy)
        val co = cos(th)
        val si = sin(th)
        val u = p.map { P((it.x - cx) * co + (it.y - cy) * si, -(it.x - cx) * si + (it.y - cy) * co) }
        val x0 = u.minOf { it.x }
        val x1 = u.maxOf { it.x }
        val y0 = u.minOf { it.y }
        val y1 = u.maxOf { it.y }
        var a = (x1 - x0) / 2
        var b = (y1 - y0) / 2
        if (a < 1e-9 || b < 1e-9) return null
        val ox = (x0 + x1) / 2
        val oy = (y0 + y1) / 2
        val res = u.map { hypot((it.x - ox) / a, (it.y - oy) / b) - 1 }
        val rms = sqrt(res.sumOf { it * it } / n)
        if (rms >= 0.10 || res.maxOf { abs(it) } >= 0.25) return null
        val c = P(cx + ox * co - oy * si, cy + ox * si + oy * co)
        if (abs(a - b) < 0.12 * max(a, b)) {
            val r = (a + b) / 2
            return Shape.Oval(c, r, r, 0.0)
        }
        var ang = th
        if (abs(ang) > PI / 2 - SNAP) { val t = a; a = b; b = t; ang = 0.0 } else if (abs(ang) < SNAP) ang = 0.0
        return Shape.Oval(c, a, b, ang)
    }
}
