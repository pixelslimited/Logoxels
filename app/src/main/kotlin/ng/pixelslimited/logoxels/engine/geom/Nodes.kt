package ng.pixelslimited.logoxels.engine.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** An editable path point. A handle equal to [p] means "no handle". */
class EditNode(var p: P, var hin: P, var hout: P)

class NodePath(var closed: Boolean, val nodes: MutableList<EditNode>)

/** Closest point of an outline: segment [seg] starts at node number [seg] of contour [contour]. */
class PathHit(val contour: Int, val seg: Int, val t: Double, val dist: Double, val at: P)

/** Point-level editing of outlines, kept free of Android types so it can be unit-tested. */
object Nodes {
    fun same(a: P, b: P) = abs(a.x - b.x) < 1e-9 && abs(a.y - b.y) < 1e-9

    private fun segCount(np: NodePath) = if (np.closed) np.nodes.size else np.nodes.size - 1

    fun fromContours(cs: List<Contour>): MutableList<NodePath> {
        val out = ArrayList<NodePath>()
        for (c in cs) {
            val n = c.segs.size
            if (n == 0) continue
            val nodes = ArrayList<EditNode>()
            val count = if (c.closed) n else n + 1
            for (i in 0 until count) {
                val next = if (i < n) c.segs[i] else null
                val prev = if (c.closed) c.segs[(i - 1 + n) % n] else if (i > 0) c.segs[i - 1] else null
                val p = next?.p0 ?: c.segs[n - 1].p3
                val hout = if (next == null || next.line) p else next.p1
                val hin = if (prev == null || prev.line) p else prev.p2
                nodes.add(EditNode(p, hin, hout))
            }
            out.add(NodePath(c.closed, nodes))
        }
        return out
    }

    fun segAt(np: NodePath, i: Int): Seg {
        val a = np.nodes[i]
        val b = np.nodes[(i + 1) % np.nodes.size]
        return if (same(a.hout, a.p) && same(b.hin, b.p)) Seg.line(a.p, b.p) else Seg.cubic(a.p, a.hout, b.hin, b.p)
    }

    fun toContours(nps: List<NodePath>): List<Contour> {
        val out = ArrayList<Contour>()
        for (np in nps) {
            if (np.nodes.size < 2) continue
            val segs = ArrayList<Seg>()
            for (i in 0 until segCount(np)) segs.add(segAt(np, i))
            out.add(Contour(segs, np.closed))
        }
        return out
    }

    /** The point on any outline closest to [p]. */
    fun nearest(nps: List<NodePath>, p: P): PathHit? {
        var best: PathHit? = null
        for ((ci, np) in nps.withIndex()) {
            if (np.nodes.size < 2) continue
            for (si in 0 until segCount(np)) {
                val s = segAt(np, si)
                var bt = 0.0
                var bd = Double.MAX_VALUE
                val steps = 32
                for (k in 0..steps) {
                    val t = k.toDouble() / steps
                    val d = (s.at(t) - p).len()
                    if (d < bd) { bd = d; bt = t }
                }
                var lo = max(0.0, bt - 1.0 / steps)
                var hi = min(1.0, bt + 1.0 / steps)
                for (iter in 0 until 20) {
                    val m1 = lo + (hi - lo) / 3.0
                    val m2 = hi - (hi - lo) / 3.0
                    if ((s.at(m1) - p).len() < (s.at(m2) - p).len()) hi = m2 else lo = m1
                }
                val t = (lo + hi) / 2.0
                val d = (s.at(t) - p).len()
                val cur = best
                if (cur == null || d < cur.dist) best = PathHit(ci, si, t, d, s.at(t))
            }
        }
        return best
    }

    /** Adds a point on segment [si] at parameter [t] without changing the shape; returns its index. */
    fun insert(np: NodePath, si: Int, t: Double): Int {
        val a = np.nodes[si]
        val b = np.nodes[(si + 1) % np.nodes.size]
        val s = segAt(np, si)
        val q: EditNode
        if (s.line) {
            val at = s.at(t)
            q = EditNode(at, at, at)
        } else {
            val (l, r) = s.split(t)
            q = EditNode(l.p3, l.p2, r.p1)
            a.hout = l.p1
            b.hin = r.p2
        }
        np.nodes.add(si + 1, q)
        return si + 1
    }

    fun makeCorner(np: NodePath, i: Int) {
        val nd = np.nodes[i]
        nd.hin = nd.p
        nd.hout = nd.p
    }

    /** Gives point [i] straight-through handles aimed along its neighbours. */
    fun makeSmooth(np: NodePath, i: Int) {
        val n = np.nodes.size
        val nd = np.nodes[i]
        val prev = if (np.closed) np.nodes[(i - 1 + n) % n] else np.nodes.getOrNull(i - 1)
        val next = if (np.closed) np.nodes[(i + 1) % n] else np.nodes.getOrNull(i + 1)
        val dir = when {
            prev != null && next != null -> (next.p - prev.p).unit()
            next != null -> (next.p - nd.p).unit()
            prev != null -> (nd.p - prev.p).unit()
            else -> P(0.0, 0.0)
        }
        nd.hin = if (prev != null) nd.p - dir * ((nd.p - prev.p).len() / 3.0) else nd.p
        nd.hout = if (next != null) nd.p + dir * ((next.p - nd.p).len() / 3.0) else nd.p
    }

    /** Removes point [i]; refuses when too few points would remain. */
    fun delete(np: NodePath, i: Int): Boolean {
        val least = if (np.closed) 3 else 2
        if (np.nodes.size <= least) return false
        np.nodes.removeAt(i)
        return true
    }

    /** Splits the outline at point [i]: a closed shape becomes one open path, an open path becomes two. */
    fun cut(np: NodePath, i: Int): List<NodePath> {
        val n = np.nodes.size
        val nd = np.nodes[i]
        if (np.closed) {
            val list = ArrayList<EditNode>()
            list.add(EditNode(nd.p, nd.p, nd.hout))
            for (k in 1 until n) list.add(np.nodes[(i + k) % n])
            list.add(EditNode(nd.p, nd.hin, nd.p))
            return listOf(NodePath(false, list))
        }
        if (i <= 0 || i >= n - 1) return listOf(np)
        val a = ArrayList<EditNode>()
        for (k in 0 until i) a.add(np.nodes[k])
        a.add(EditNode(nd.p, nd.hin, nd.p))
        val b = ArrayList<EditNode>()
        b.add(EditNode(nd.p, nd.p, nd.hout))
        for (k in i + 1 until n) b.add(np.nodes[k])
        return listOf(NodePath(false, a), NodePath(false, b))
    }
}
