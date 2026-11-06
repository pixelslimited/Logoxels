package ng.pixelslimited.logoxels.engine.geom

/** Line-breaking helpers for text that fills a shape or flows around objects. */
object TextWrap {
    /** How many words starting at [from] fit on one line of [width], with [space] between words. */
    fun fit(words: List<String>, from: Int, width: Float, space: Float, widthOf: (String) -> Float): Int {
        var used = 0f
        var n = 0
        for (i in from until words.size) {
            val w = widthOf(words[i])
            val total = if (n == 0) w else used + space + w
            if (total > width) break
            used = total
            n++
        }
        return n
    }

    /** The widest piece of [l]..[r] left after removing every cut interval, or null when nothing is left. */
    fun largestGap(l: Float, r: Float, cuts: List<Pair<Float, Float>>): Pair<Float, Float>? {
        var pieces = listOf(l to r)
        for (c in cuts) {
            val nxt = ArrayList<Pair<Float, Float>>()
            for (p in pieces) {
                if (c.second <= p.first || c.first >= p.second) { nxt.add(p); continue }
                if (c.first > p.first) nxt.add(p.first to c.first)
                if (c.second < p.second) nxt.add(c.second to p.second)
            }
            pieces = nxt
        }
        return pieces.maxByOrNull { it.second - it.first }?.takeIf { it.second - it.first > 0f }
    }
}
