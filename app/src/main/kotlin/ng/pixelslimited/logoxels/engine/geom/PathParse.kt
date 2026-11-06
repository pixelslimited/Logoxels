package ng.pixelslimited.logoxels.engine.geom

/** Reads absolute SVG path data (M L H V C Q Z) into contours. */
object PathParse {
    private val token = Regex("[MLHVCQZ]|-?\\d*\\.?\\d+")

    fun parse(d: String): List<Contour> {
        val tok = token.findAll(d).map { it.value }.toList()
        val out = ArrayList<Contour>()
        var segs = ArrayList<Seg>()
        var cur = P(0.0, 0.0)
        var start = cur
        var cmd = ' '
        var i = 0
        fun num() = tok[i++].toDouble()
        fun flush(closed: Boolean) {
            if (segs.isNotEmpty()) out.add(Contour(segs, closed))
            segs = ArrayList()
        }
        while (i < tok.size) {
            if (tok[i][0].isLetter()) {
                cmd = tok[i][0]
                i++
                if (cmd == 'Z') {
                    if ((cur - start).len() > 1e-9) segs.add(Seg.line(cur, start))
                    cur = start
                    flush(true)
                    continue
                }
            }
            when (cmd) {
                'M' -> { flush(false); cur = P(num(), num()); start = cur; cmd = 'L' }
                'L' -> { val p = P(num(), num()); segs.add(Seg.line(cur, p)); cur = p }
                'H' -> { val p = P(num(), cur.y); segs.add(Seg.line(cur, p)); cur = p }
                'V' -> { val p = P(cur.x, num()); segs.add(Seg.line(cur, p)); cur = p }
                'C' -> { val a = P(num(), num()); val b = P(num(), num()); val p = P(num(), num()); segs.add(Seg.cubic(cur, a, b, p)); cur = p }
                'Q' -> { val a = P(num(), num()); val p = P(num(), num()); segs.add(Seg.quad(cur, a, p)); cur = p }
                else -> throw IllegalArgumentException("Unsupported path command '$cmd'")
            }
        }
        flush(false)
        return out
    }
}
