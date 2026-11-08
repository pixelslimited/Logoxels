package ng.pixelslimited.logoxels.engine

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import ng.pixelslimited.logoxels.engine.geom.PathBridge
import ng.pixelslimited.logoxels.engine.geom.TextWrap
import ng.pixelslimited.logoxels.engine.geom.Warp
import ng.pixelslimited.logoxels.engine.geom.Warper
import kotlin.math.atan2

enum class TextMode { PLAIN, ON_PATH, AREA }

/** Everything needed to rebuild a live text object's outline. Coordinates are artboard units. */
class TextSpec(
    var text: String,
    var x: Float,
    var y: Float,
    var font: Int = TextLayout.APP,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var size: Float = 120f,
    var mode: TextMode = TextMode.PLAIN,
    var guide: Path? = null,
    var area: Path? = null,
    var obstacles: List<Path> = emptyList(),
    var warp: Warp = Warp.NONE,
    var amount: Float = 0.5f,
) {
    fun copy() = TextSpec(
        text, x, y, font, bold, italic, size, mode,
        guide?.let { Path(it) }, area?.let { Path(it) }, obstacles.map { Path(it) }, warp, amount,
    )
}

object TextLayout {
    val FONTS = listOf(
        "sans-serif" to "Sans", "serif" to "Serif", "monospace" to "Mono",
        "casual" to "Casual", "sans-serif-condensed" to "Condensed",
        "" to "App",
    )

    /** Index of the app's own interface font in [FONTS]; the default for new text. */
    val APP = FONTS.lastIndex

    /** The interface font, set once at startup; the system sans-serif stands in when it is missing. */
    var appFace: Typeface? = null
    private const val OBSTACLE_GAP = 12f

    private fun paint(s: TextSpec) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val style = when {
            s.bold && s.italic -> Typeface.BOLD_ITALIC
            s.bold -> Typeface.BOLD
            s.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val i = s.font.coerceIn(0, FONTS.lastIndex)
        val face = if (i == APP) appFace else null
        typeface = if (face != null) Typeface.create(face, style) else Typeface.create(FONTS[i].first.ifEmpty { "sans-serif" }, style)
        textSize = s.size
    }

    fun build(s: TextSpec): Path {
        val p = paint(s)
        val guide = s.guide
        val area = s.area
        val raw = when {
            s.mode == TextMode.ON_PATH && guide != null -> onPath(s, p, guide)
            s.mode == TextMode.AREA && area != null -> inArea(s, p, area)
            else -> plain(s, p)
        }
        if (s.warp == Warp.NONE || s.mode != TextMode.PLAIN) return raw
        return PathBridge.toPath(Warper.apply(PathBridge.toContours(raw), s.warp, s.amount.toDouble()))
    }

    private fun line(p: Paint, text: String, x: Float, y: Float, into: Path) {
        val g = Path()
        p.getTextPath(text, 0, text.length, x, y, g)
        into.addPath(g)
    }

    private fun plain(s: TextSpec, p: Paint): Path {
        val out = Path()
        val lh = s.size * 1.2f
        s.text.split('\n').forEachIndexed { i, t -> if (t.isNotEmpty()) line(p, t, s.x, s.y + i * lh, out) }
        return out
    }

    private fun onPath(s: TextSpec, p: Paint, guide: Path): Path {
        val out = Path()
        val pm = PathMeasure(guide, false)
        val total = pm.length
        if (total <= 0f) return out
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        var at = 0f
        for (ch in s.text.replace('\n', ' ')) {
            val c = ch.toString()
            val w = p.measureText(c)
            if (at + w / 2f > total) break
            if (c.isNotBlank()) {
                pm.getPosTan(at + w / 2f, pos, tan)
                val g = Path()
                p.getTextPath(c, 0, 1, 0f, 0f, g)
                val m = Matrix()
                m.postTranslate(-w / 2f, 0f)
                m.postRotate(Math.toDegrees(atan2(tan[1].toDouble(), tan[0].toDouble())).toFloat())
                m.postTranslate(pos[0], pos[1])
                g.transform(m)
                out.addPath(g)
            }
            at += w
        }
        return out
    }

    /** Left and right edge of [shape] inside the horizontal band, or null when the band misses it. */
    private fun span(shape: Path, top: Float, bottom: Float): Pair<Float, Float>? {
        val slab = Path().apply { addRect(-100000f, top, 100000f, bottom, Path.Direction.CW) }
        val r = Path(shape)
        if (!r.op(slab, Path.Op.INTERSECT) || r.isEmpty) return null
        val b = RectF()
        r.computeBounds(b, true)
        return b.left to b.right
    }

    /** Usable width of a container for a line occupying [top]..[bottom]: its narrowest point inside the band. */
    private fun inside(shape: Path, top: Float, bottom: Float): Pair<Float, Float>? {
        val a = span(shape, top, top + 2f) ?: return null
        val b = span(shape, bottom - 2f, bottom) ?: return null
        val c = span(shape, (top + bottom) / 2f - 1f, (top + bottom) / 2f + 1f) ?: return null
        val l = maxOf(a.first, b.first, c.first)
        val r = minOf(a.second, b.second, c.second)
        return if (r > l) l to r else null
    }

    private fun inArea(s: TextSpec, p: Paint, area: Path): Path {
        val out = Path()
        val b = RectF()
        area.computeBounds(b, true)
        val words = s.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lh = s.size * 1.2f
        val space = p.measureText(" ")
        var y = b.top + s.size
        var i = 0
        while (i < words.size && y - s.size < b.bottom) {
            val top = y - s.size
            val bottom = y + s.size * 0.25f
            val room = inside(area, top, bottom)
            if (room != null) {
                val cuts = s.obstacles.mapNotNull { o ->
                    span(o, top, bottom)?.let { (it.first - OBSTACLE_GAP) to (it.second + OBSTACLE_GAP) }
                }
                val gap = TextWrap.largestGap(room.first, room.second, cuts)
                if (gap != null) {
                    val n = TextWrap.fit(words, i, gap.second - gap.first, space) { p.measureText(it) }
                    if (n > 0) {
                        line(p, words.subList(i, i + n).joinToString(" "), gap.first, y, out)
                        i += n
                    }
                }
            }
            y += lh
        }
        return out
    }
}
