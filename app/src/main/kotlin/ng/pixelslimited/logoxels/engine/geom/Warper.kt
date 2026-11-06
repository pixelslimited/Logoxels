package ng.pixelslimited.logoxels.engine.geom

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class Warp { NONE, ARCH, WAVE, FLAG, BULGE, RISE }

/** Envelope distortion of an outline (text warp). [amount] runs from -1 to 1. */
object Warper {
    private const val STEPS = 8

    fun map(w: Warp, amount: Double, p: P, left: Double, width: Double, top: Double, height: Double): P {
        if (w == Warp.NONE || width <= 0.0) return p
        val u = (p.x - left) / width
        val cy = top + height / 2.0
        val bell = 1.0 - (2.0 * u - 1.0) * (2.0 * u - 1.0)
        return when (w) {
            Warp.NONE -> p
            Warp.ARCH -> P(p.x, p.y - amount * width * 0.25 * bell)
            Warp.WAVE -> P(p.x, p.y + amount * height * sin(2.0 * PI * u))
            Warp.FLAG -> P(p.x, p.y + amount * height * sin(2.0 * PI * u) * u)
            Warp.BULGE -> P(p.x, cy + (p.y - cy) * (1.0 + amount * bell))
            Warp.RISE -> P(p.x, cy + (p.y - cy) * (1.0 + amount * (2.0 * u - 1.0)))
        }
    }

    fun apply(cs: List<Contour>, w: Warp, amount: Double): List<Contour> {
        if (w == Warp.NONE || cs.isEmpty()) return cs
        var l = Double.MAX_VALUE
        var r = -Double.MAX_VALUE
        var t = Double.MAX_VALUE
        var b = -Double.MAX_VALUE
        for (c in cs) for (s in c.segs) for (p in listOf(s.p0, s.p1, s.p2, s.p3)) {
            l = min(l, p.x); r = max(r, p.x); t = min(t, p.y); b = max(b, p.y)
        }
        fun f(p: P) = map(w, amount, p, l, r - l, t, b - t)
        return cs.map { c ->
            val segs = ArrayList<Seg>()
            for (s in c.segs) for (k in 0 until STEPS) {
                val q = s.sub(k.toDouble() / STEPS, (k + 1).toDouble() / STEPS)
                segs.add(Seg(f(q.p0), f(q.p1), f(q.p2), f(q.p3), false))
            }
            Contour(segs, c.closed)
        }
    }
}
