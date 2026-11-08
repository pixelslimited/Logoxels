package ng.pixelslimited.logoxels.engine

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Region
import ng.pixelslimited.logoxels.engine.geom.PathBridge
import kotlin.math.roundToInt

/** One connected region of a set of overlapping shapes; [source] is the shape whose look it takes, or null when no closed shape covers it. */
class Face(val path: Path, val source: Item?)

/**
 * Splits shapes into the regions their outlines enclose, the pieces the shape builder merges and erases.
 * Filled shapes, stroke-only shapes and open lines all count: only their outlines matter.
 */
object Faces {
    private val clip = Region(-100000, -100000, 100000, 100000)

    /** Width of the line drawn along every outline; regions are found between these lines, then grown back by half of it. */
    private const val LINE = 1f

    private fun tiny(p: Path): Boolean {
        if (p.isEmpty) return true
        val b = RectF()
        p.computeBounds(b, true)
        return b.width() < 0.2f || b.height() < 0.2f
    }

    private fun outline(path: Path, width: Float): Path {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        return Path().also { paint.getFillPath(path, it) }
    }

    private class Ring(val path: Path, val depth: Int, val parent: Int)

    /** The closed outlines in [path], each with how many outlines enclose it and which one directly encloses it. */
    private fun rings(path: Path): List<Ring> {
        val parts = PathBridge.toContours(path).filter { it.closed }.map { PathBridge.toPath(listOf(it)) }
        val regions = parts.map { region(it) }
        val within = parts.indices.map { i ->
            val at = FloatArray(2)
            if (PathMeasure(parts[i], false).getPosTan(0f, at, null)) {
                parts.indices.filter { j -> j != i && regions[j].contains(at[0].roundToInt(), at[1].roundToInt()) }
            } else {
                emptyList()
            }
        }
        return parts.indices.map { i ->
            val depth = within[i].size
            Ring(parts[i], depth, within[i].firstOrNull { j -> within[j].size == depth - 1 } ?: -1)
        }
    }

    /**
     * The regions of [items] (bottom to top). They do not overlap, and each takes the look of the topmost
     * closed shape over it. Areas enclosed only by gaps between shapes are left out when any closed shape is present.
     */
    fun of(items: List<Item>): List<Face> {
        var lines: Path? = null
        for (item in items) {
            val band = outline(item.renderPath(), LINE)
            val sum = lines
            if (sum == null) lines = band else sum.op(band, Path.Op.UNION)
        }
        val all = lines ?: return emptyList()
        val areas = items.filter { it.fill != null || PathBridge.toContours(it.renderPath()).let { c -> c.isNotEmpty() && c.all { k -> k.closed } } }
            .map { it to it.renderPath() }
        var covered: Path? = null
        for ((_, a) in areas) {
            val sum = covered
            if (sum == null) covered = Path(a) else sum.op(a, Path.Op.UNION)
        }
        val rings = rings(all)
        val out = ArrayList<Face>()
        for ((i, ring) in rings.withIndex()) {
            if (ring.depth % 2 == 0) continue
            val region = Path().apply { fillType = Path.FillType.EVEN_ODD }
            region.addPath(ring.path)
            for (h in rings) if (h.depth == ring.depth + 1 && h.parent == i) region.addPath(h.path)
            // the lines took half their width from each side of the region: give it back
            val grown = Path(region)
            grown.op(outline(region, LINE), Path.Op.UNION)
            if (tiny(grown)) continue
            var source: Item? = null
            if (covered != null) {
                val inCover = Path(grown)
                if (!inCover.op(covered, Path.Op.INTERSECT) || tiny(inCover)) continue
                for ((item, a) in areas.asReversed()) {
                    val over = Path(grown)
                    if (over.op(a, Path.Op.INTERSECT) && !tiny(over)) { source = item; break }
                }
            }
            out.add(Face(grown, source))
        }
        return out
    }

    /** The area of [path] as a region for hit-testing (whole-unit accuracy). */
    fun region(path: Path): Region = Region().also { it.setPath(path, clip) }
}
