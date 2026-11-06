package ng.pixelslimited.logoxels.engine.geom

import kotlin.math.abs
import kotlin.math.roundToLong

/** SVG path-data text for a list of contours. */
object PathData {
    fun num(v: Double): String {
        val r = (v * 100.0).roundToLong()
        if (r == 0L) return "0"
        val sign = if (r < 0) "-" else ""
        val a = abs(r)
        val whole = a / 100
        val frac = (a % 100).toInt()
        return when {
            frac == 0 -> "$sign$whole"
            frac % 10 == 0 -> "$sign$whole.${frac / 10}"
            else -> "$sign$whole.${if (frac < 10) "0$frac" else "$frac"}"
        }
    }

    private fun pt(p: P) = "${num(p.x)} ${num(p.y)}"

    /** PostScript path ops with y flipped (PostScript's origin is bottom-left) inside a page of [h] units. */
    fun postScript(contours: List<Contour>, h: Double): String {
        fun q(p: P) = "${num(p.x)} ${num(h - p.y)}"
        val sb = StringBuilder()
        for (c in contours) {
            if (c.segs.isEmpty()) continue
            sb.append(q(c.segs[0].p0)).append(" moveto ")
            for (s in c.segs) {
                if (s.line) sb.append(q(s.p3)).append(" lineto ")
                else sb.append(q(s.p1)).append(' ').append(q(s.p2)).append(' ').append(q(s.p3)).append(" curveto ")
            }
            if (c.closed) sb.append("closepath ")
        }
        return sb.toString()
    }

    fun of(contours: List<Contour>): String {
        val sb = StringBuilder()
        for (c in contours) {
            if (c.segs.isEmpty()) continue
            sb.append('M').append(pt(c.segs[0].p0))
            for (s in c.segs) {
                if (s.line) sb.append('L').append(pt(s.p3))
                else sb.append('C').append(pt(s.p1)).append(' ').append(pt(s.p2)).append(' ').append(pt(s.p3))
            }
            if (c.closed) sb.append('Z')
        }
        return sb.toString()
    }
}
