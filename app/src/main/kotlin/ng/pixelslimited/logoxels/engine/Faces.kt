package ng.pixelslimited.logoxels.engine

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Region
import ng.pixelslimited.logoxels.engine.geom.PathBridge
import kotlin.math.roundToInt

/** One connected region of an overlapping set of shapes, with the shape whose look it takes. */
class Face(val path: Path, val source: Item)

/** Splits overlapping shapes into their regions, the pieces the shape builder merges and erases. */
object Faces {
    private val clip = Region(-100000, -100000, 100000, 100000)

    private fun tiny(p: Path): Boolean {
        if (p.isEmpty) return true
        val b = RectF()
        p.computeBounds(b, true)
        return b.width() < 0.2f || b.height() < 0.2f
    }

    /**
     * The regions of [items] (bottom to top). They do not overlap, together cover the same area,
     * and each takes the look of the topmost shape over it.
     */
    fun of(items: List<Item>): List<Face> {
        var faces = ArrayList<Face>()
        for (item in items) {
            val shape = item.renderPath()
            val next = ArrayList<Face>()
            val fresh = Path(shape)
            for (f in faces) {
                val inside = Path(f.path)
                val outside = Path(f.path)
                if (inside.op(shape, Path.Op.INTERSECT) && !tiny(inside)) next.add(Face(inside, item))
                if (outside.op(shape, Path.Op.DIFFERENCE) && !tiny(outside)) next.add(Face(outside, f.source))
                fresh.op(f.path, Path.Op.DIFFERENCE)
            }
            if (!tiny(fresh)) next.add(Face(fresh, item))
            faces = next
        }
        return faces.flatMap { f -> split(f.path).map { Face(it, f.source) } }
    }

    /** The area of [path] as a region for hit-testing (whole-unit accuracy). */
    fun region(path: Path): Region = Region().also { it.setPath(path, clip) }

    /** The separate pieces of [path]: each outer outline together with the holes inside it. */
    fun split(path: Path): List<Path> {
        val parts = PathBridge.toContours(path).filter { it.closed }.map { PathBridge.toPath(listOf(it)) }
        if (parts.size < 2) return listOf(path)
        val regions = parts.map { region(it) }
        val starts = parts.map { p ->
            val pos = FloatArray(2)
            if (PathMeasure(p, false).getPosTan(0f, pos, null)) pos else null
        }
        // contours containing contour i: an even count means an outline, an odd count a hole
        val parents = parts.indices.map { i ->
            val at = starts[i]
            if (at == null) emptyList() else parts.indices.filter { j -> j != i && regions[j].contains(at[0].roundToInt(), at[1].roundToInt()) }
        }
        val out = ArrayList<Path>()
        for (i in parts.indices) {
            if (parents[i].size % 2 != 0) continue
            val piece = Path().apply { fillType = Path.FillType.EVEN_ODD }
            piece.addPath(parts[i])
            for (h in parts.indices) {
                if (parents[h].size == parents[i].size + 1 && i in parents[h]) piece.addPath(parts[h])
            }
            out.add(piece)
        }
        return out.ifEmpty { listOf(path) }
    }
}
