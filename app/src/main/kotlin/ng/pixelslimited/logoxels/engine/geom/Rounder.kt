package ng.pixelslimited.logoxels.engine.geom

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** Plain 2D point/vector in doubles. No Android types, so the maths is unit-testable on the JVM. */
class P(val x: Double, val y: Double) {
    operator fun plus(o: P) = P(x + o.x, y + o.y)
    operator fun minus(o: P) = P(x - o.x, y - o.y)
    operator fun times(k: Double) = P(x * k, y * k)
    infix fun dot(o: P) = x * o.x + y * o.y
    fun len() = hypot(x, y)
    fun unit(): P {
        val l = len()
        return if (l > 1e-12) P(x / l, y / l) else P(0.0, 0.0)
    }
    fun lerp(o: P, t: Double) = P(x + (o.x - x) * t, y + (o.y - y) * t)
}

/** One cubic Bezier segment. Straight lines are stored as cubics with [line] = true. */
class Seg(val p0: P, val p1: P, val p2: P, val p3: P, val line: Boolean) {
    companion object {
        private const val STEPS = 64
        fun line(a: P, b: P) = Seg(a, a.lerp(b, 1.0 / 3.0), a.lerp(b, 2.0 / 3.0), b, true)
        fun quad(a: P, c: P, b: P) = Seg(a, a.lerp(c, 2.0 / 3.0), b.lerp(c, 2.0 / 3.0), b, false)
        fun cubic(a: P, c1: P, c2: P, b: P) = Seg(a, c1, c2, b, false)
    }

    private val table: DoubleArray by lazy {
        val t = DoubleArray(STEPS + 1)
        var prev = at(0.0)
        for (i in 1..STEPS) {
            val q = at(i.toDouble() / STEPS)
            t[i] = t[i - 1] + (q - prev).len()
            prev = q
        }
        t
    }

    val length: Double get() = table[STEPS]

    fun at(t: Double): P {
        val u = 1.0 - t
        val a = u * u * u
        val b = 3.0 * u * u * t
        val c = 3.0 * u * t * t
        val d = t * t * t
        return P(a * p0.x + b * p1.x + c * p2.x + d * p3.x, a * p0.y + b * p1.y + c * p2.y + d * p3.y)
    }

    private fun deriv(t: Double): P {
        val u = 1.0 - t
        val a = p1 - p0
        val b = p2 - p1
        val c = p3 - p2
        return (a * (u * u) + b * (2.0 * u * t) + c * (t * t)) * 3.0
    }

    /** Unit direction of travel at [t]; falls back to neighbouring control points on degenerate ends. */
    fun tangent(t: Double): P {
        val d = deriv(t).unit()
        if (d.len() > 0.5) return d
        val first = if (t < 0.5) (p2 - p0).unit() else (p3 - p1).unit()
        if (first.len() > 0.5) return first
        val second = if (t < 0.5) (p3 - p1).unit() else (p2 - p0).unit()
        if (second.len() > 0.5) return second
        return (p3 - p0).unit()
    }

    /** Parameter t at which the arc length from the start equals [d]. */
    fun tAtLen(d: Double): Double {
        if (d <= 0.0) return 0.0
        if (d >= length) return 1.0
        for (i in 1..STEPS) {
            if (table[i] >= d) {
                val f = (d - table[i - 1]) / max(table[i] - table[i - 1], 1e-12)
                return (i - 1 + f) / STEPS
            }
        }
        return 1.0
    }

    fun split(t: Double): Pair<Seg, Seg> {
        val a = p0.lerp(p1, t)
        val b = p1.lerp(p2, t)
        val c = p2.lerp(p3, t)
        val d = a.lerp(b, t)
        val e = b.lerp(c, t)
        val f = d.lerp(e, t)
        return Seg(p0, a, d, f, line) to Seg(f, e, c, p3, line)
    }

    /** The part of this segment between parameters [t0] and [t1]. */
    fun sub(t0: Double, t1: Double): Seg {
        val r = if (t0 > 0.0) split(t0).second else this
        val k = if (t0 < 1.0) (t1 - t0) / (1.0 - t0) else 1.0
        return if (k < 1.0) r.split(k).first else r
    }
}

class Contour(val segs: List<Seg>, val closed: Boolean)

/**
 * Rounds the corners of any outline made of lines and curves (rectangles, stars, boolean-op results...).
 * Each corner is replaced by a tangent-continuous fillet; curved neighbours are split exactly,
 * so smooth parts are never flattened.
 */
object Rounder {
    class Node(val index: Int, val at: P)

    private const val MIN_TURN = 0.0175 // about 1 degree: below this a join counts as smooth

    fun nodeCount(c: Contour) = if (c.closed) c.segs.size else max(0, c.segs.size - 1)

    /** Every join between segments, numbered in a stable order (used for per-corner radii and for the UI dots). */
    fun nodes(contours: List<Contour>): List<Node> {
        val out = ArrayList<Node>()
        var idx = 0
        for (c in contours) {
            val range = if (c.closed) 0 until c.segs.size else 1 until c.segs.size
            for (i in range) out.add(Node(idx++, c.segs[i].p0))
        }
        return out
    }

    fun round(contours: List<Contour>, radiusOf: (Int) -> Double): List<Contour> {
        var base = 0
        val res = ArrayList<Contour>(contours.size)
        for (c in contours) {
            res.add(roundOne(c, base, radiusOf))
            base += nodeCount(c)
        }
        return res
    }

    private fun roundOne(c: Contour, base: Int, radiusOf: (Int) -> Double): Contour {
        val segs = c.segs
        val n = segs.size
        if (n == 0) return c
        val first = if (c.closed) 0 else 1
        val turn = HashMap<Int, Double>()
        val radius = HashMap<Int, Double>()
        for (i in first until n) {
            val r = radiusOf(base + if (c.closed) i else i - 1)
            if (r <= 0.0) continue
            val prev = segs[(i - 1 + n) % n]
            val phi = acos((prev.tangent(1.0) dot segs[i].tangent(0.0)).coerceIn(-1.0, 1.0))
            if (phi > MIN_TURN && phi < PI - 0.01) {
                turn[i] = phi
                radius[i] = r
            }
        }
        if (turn.isEmpty()) return c

        val lens = DoubleArray(n) { segs[it].length }
        val tEnd = DoubleArray(n) { 1.0 }
        val tStart = DoubleArray(n) { 0.0 }
        val fillet = HashMap<Int, Seg>()
        for ((i, phi) in turn) {
            val pi = (i - 1 + n) % n
            // a side shared by two rounded corners gives each of them at most half of its length
            val capP = if (turn.containsKey(pi)) lens[pi] / 2.0 else lens[pi]
            val capN = if (turn.containsKey((i + 1) % n)) lens[i] / 2.0 else lens[i]
            val d = min(radius.getValue(i) * tan(phi / 2.0), min(capP, capN))
            if (d <= 1e-9) continue
            val tp = segs[pi].tAtLen(lens[pi] - d)
            val tn = segs[i].tAtLen(d)
            tEnd[pi] = tp
            tStart[i] = tn
            val a = segs[pi].at(tp)
            val b = segs[i].at(tn)
            val ta = segs[pi].tangent(tp)
            val tb = segs[i].tangent(tn)
            val ph = acos((ta dot tb).coerceIn(-1.0, 1.0))
            val chord = (b - a).len()
            fillet[i] = if (ph < 1e-6 || chord < 1e-9) {
                Seg.line(a, b)
            } else {
                val rr = chord / (2.0 * sin(ph / 2.0))
                val k = 4.0 / 3.0 * tan(ph / 4.0) * rr
                Seg.cubic(a, a + ta * k, b - tb * k, b)
            }
        }

        val out = ArrayList<Seg>()
        for (j in 0 until n) {
            if (tEnd[j] - tStart[j] > 1e-9) out.add(segs[j].sub(tStart[j], tEnd[j]))
            val nj = (j + 1) % n
            if (c.closed || j + 1 < n) fillet[nj]?.let { out.add(it) }
        }
        return Contour(out, c.closed)
    }
}
