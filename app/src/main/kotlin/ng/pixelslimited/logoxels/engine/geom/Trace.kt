package ng.pixelslimited.logoxels.engine.geom

import kotlin.math.abs

/** Turns a black-and-white grid into closed outlines (bitmap trace). */
object Trace {
    /**
     * Outlines around the filled cells of a [w] x [h] grid, in cell-corner coordinates. Holes come out as
     * separate loops, so fill the result with the even-odd rule. [tol] is the straight-line tolerance in cells.
     */
    fun loops(w: Int, h: Int, tol: Double = 0.9, filled: (Int, Int) -> Boolean): List<List<P>> {
        fun f(x: Int, y: Int) = x in 0 until w && y in 0 until h && filled(x, y)
        val stride = w + 1
        val next = HashMap<Int, ArrayList<Int>>()
        fun edge(x0: Int, y0: Int, x1: Int, y1: Int) {
            next.getOrPut(y0 * stride + x0) { ArrayList(2) }.add(y1 * stride + x1)
        }
        for (y in 0 until h) for (x in 0 until w) {
            if (!f(x, y)) continue
            if (!f(x, y - 1)) edge(x, y, x + 1, y)
            if (!f(x + 1, y)) edge(x + 1, y, x + 1, y + 1)
            if (!f(x, y + 1)) edge(x + 1, y + 1, x, y + 1)
            if (!f(x - 1, y)) edge(x, y + 1, x, y)
        }
        val out = ArrayList<List<P>>()
        while (next.isNotEmpty()) {
            val start = next.keys.first()
            val ring = ArrayList<P>()
            var v = start
            do {
                val list = next.getValue(v)
                val n = list.removeAt(list.size - 1)
                if (list.isEmpty()) next.remove(v)
                ring.add(P((v % stride).toDouble(), (v / stride).toDouble()))
                v = n
            } while (v != start)
            val simple = simplifyClosed(dropCollinear(ring), tol)
            if (simple.size >= 3) out.add(simple)
        }
        return out
    }

    private fun dropCollinear(r: List<P>): List<P> {
        val n = r.size
        val keep = ArrayList<P>()
        for (i in 0 until n) {
            val a = r[(i + n - 1) % n]
            val b = r[i]
            val c = r[(i + 1) % n]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) > 1e-9) keep.add(b)
        }
        return keep
    }

    private fun simplifyClosed(pts: List<P>, tol: Double): List<P> {
        if (pts.size <= 4) return pts
        var far = 1
        var best = -1.0
        for (i in pts.indices) {
            val d = (pts[i] - pts[0]).len()
            if (d > best) { best = d; far = i }
        }
        val a = douglasPeucker(pts.subList(0, far + 1), tol)
        val b = douglasPeucker(pts.subList(far, pts.size) + pts[0], tol)
        return a.dropLast(1) + b.dropLast(1)
    }

    private fun douglasPeucker(pts: List<P>, tol: Double): List<P> {
        if (pts.size < 3) return pts
        val a = pts.first()
        val b = pts.last()
        val ab = b - a
        val len = ab.len()
        var idx = 0
        var worst = -1.0
        for (i in 1 until pts.size - 1) {
            val d = if (len < 1e-12) (pts[i] - a).len() else abs(ab.x * (pts[i].y - a.y) - ab.y * (pts[i].x - a.x)) / len
            if (d > worst) { worst = d; idx = i }
        }
        if (worst <= tol) return listOf(a, b)
        return douglasPeucker(pts.subList(0, idx + 1), tol).dropLast(1) + douglasPeucker(pts.subList(idx, pts.size), tol)
    }
}
