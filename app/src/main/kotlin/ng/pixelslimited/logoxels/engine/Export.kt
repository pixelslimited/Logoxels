package ng.pixelslimited.logoxels.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import ng.pixelslimited.logoxels.engine.geom.Contour
import ng.pixelslimited.logoxels.engine.geom.P
import ng.pixelslimited.logoxels.engine.geom.PathBridge
import ng.pixelslimited.logoxels.engine.geom.PathData
import ng.pixelslimited.logoxels.engine.geom.Seg
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

/** Writes the artboard (1000 x 1000 units) to SVG, PNG and PDF, and saves/loads project files. */
object Export {
    private const val SIZE = 1000

    private fun draw(canvas: Canvas, items: List<Item>) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        for (item in items) {
            if (!item.visible) continue
            val rp = item.renderPath()
            item.fill?.let {
                fill.color = it
                fill.shader = item.gradient?.shader(item.box())
                canvas.drawPath(rp, fill)
                fill.shader = null
            }
            item.stroke?.let {
                if (item.strokeWidth > 0f) {
                    stroke.color = it
                    stroke.strokeWidth = item.strokeWidth
                    canvas.drawPath(rp, stroke)
                }
            }
        }
    }

    /** Transparent PNG, [px] pixels square. */
    fun png(items: List<Item>, out: OutputStream, px: Int = 2000) {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            c.scale(px / SIZE.toFloat(), px / SIZE.toFloat())
            draw(c, items)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            bmp.recycle()
        }
    }

    fun pdf(items: List<Item>, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(SIZE, SIZE, 1).create())
            draw(page.canvas, items)
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)

    private fun opacity(c: Int) = (c ushr 24) / 255f

    fun svg(items: List<Item>): String {
        val defs = StringBuilder()
        val body = StringBuilder()
        for (item in items) {
            if (!item.visible) continue
            val rp = item.renderPath()
            val d = PathData.of(PathBridge.toContours(rp))
            if (d.isEmpty()) continue
            val evenOdd = rp.fillType == Path.FillType.EVEN_ODD || rp.fillType == Path.FillType.INVERSE_EVEN_ODD
            val a = StringBuilder("<path d=\"$d\"")
            val fillColor = item.fill
            val g = item.gradient
            if (fillColor == null) {
                a.append(" fill=\"none\"")
            } else if (g != null) {
                val id = "g${item.id}"
                val v = g.geometry(item.box())
                if (g.kind == GKind.LINEAR) {
                    defs.append("<linearGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" x1=\"${v[0]}\" y1=\"${v[1]}\" x2=\"${v[2]}\" y2=\"${v[3]}\">")
                } else {
                    defs.append("<radialGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" cx=\"${v[0]}\" cy=\"${v[1]}\" r=\"${v[2]}\">")
                }
                defs.append("<stop offset=\"0\" stop-color=\"${hex(g.c1)}\" stop-opacity=\"${opacity(g.c1)}\"/>")
                defs.append("<stop offset=\"1\" stop-color=\"${hex(g.c2)}\" stop-opacity=\"${opacity(g.c2)}\"/>")
                defs.append(if (g.kind == GKind.LINEAR) "</linearGradient>" else "</radialGradient>")
                a.append(" fill=\"url(#$id)\"")
            } else {
                a.append(" fill=\"${hex(fillColor)}\"")
                if (opacity(fillColor) < 1f) a.append(" fill-opacity=\"${opacity(fillColor)}\"")
            }
            if (evenOdd) a.append(" fill-rule=\"evenodd\"")
            val strokeColor = item.stroke
            if (strokeColor != null && item.strokeWidth > 0f) {
                a.append(" stroke=\"${hex(strokeColor)}\" stroke-width=\"${item.strokeWidth}\" stroke-linejoin=\"round\" stroke-linecap=\"round\"")
                if (opacity(strokeColor) < 1f) a.append(" stroke-opacity=\"${opacity(strokeColor)}\"")
            }
            body.append(a).append("/>\n")
        }
        val head = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$SIZE\" height=\"$SIZE\" viewBox=\"0 0 $SIZE $SIZE\">\n"
        val defsTag = if (defs.isEmpty()) "" else "<defs>$defs</defs>\n"
        return head + defsTag + body + "</svg>\n"
    }

    // ---------- project file ----------

    private fun rectJson(r: RectF) = JSONArray().put(r.left.toDouble()).put(r.top.toDouble()).put(r.right.toDouble()).put(r.bottom.toDouble())

    private fun pathJson(path: Path): JSONObject {
        val cs = JSONArray()
        for (c in PathBridge.toContours(path)) {
            val segs = JSONArray()
            for (s in c.segs) {
                segs.put(JSONArray().put(if (s.line) 1 else 0)
                    .put(s.p0.x).put(s.p0.y).put(s.p1.x).put(s.p1.y).put(s.p2.x).put(s.p2.y).put(s.p3.x).put(s.p3.y))
            }
            cs.put(JSONObject().put("closed", c.closed).put("segs", segs))
        }
        return JSONObject().put("evenOdd", path.fillType == Path.FillType.EVEN_ODD).put("contours", cs)
    }

    private fun pathFrom(o: JSONObject): Path {
        val list = ArrayList<Contour>()
        val cs = o.getJSONArray("contours")
        for (i in 0 until cs.length()) {
            val co = cs.getJSONObject(i)
            val sa = co.getJSONArray("segs")
            val segs = ArrayList<Seg>()
            for (j in 0 until sa.length()) {
                val a = sa.getJSONArray(j)
                fun p(k: Int) = P(a.getDouble(k), a.getDouble(k + 1))
                segs.add(if (a.getInt(0) == 1) Seg.line(p(1), p(7)) else Seg.cubic(p(1), p(3), p(5), p(7)))
            }
            list.add(Contour(segs, co.getBoolean("closed")))
        }
        val path = PathBridge.toPath(list)
        if (o.optBoolean("evenOdd")) path.fillType = Path.FillType.EVEN_ODD
        return path
    }

    fun projectJson(items: List<Item>): String {
        val arr = JSONArray()
        for (it in items) {
            val o = JSONObject()
                .put("id", it.id).put("kind", it.kind.name)
                .put("path", pathJson(it.path)).put("bounds", rectJson(it.bounds))
                .put("fill", it.fill ?: JSONObject.NULL).put("stroke", it.stroke ?: JSONObject.NULL)
                .put("strokeWidth", it.strokeWidth.toDouble()).put("corner", it.corner.toDouble())
                .put("sides", it.sides).put("inner", it.inner.toDouble())
                .put("group", it.groupId).put("visible", it.visible).put("locked", it.locked)
            val radii = JSONObject()
            for ((k, v) in it.cornerR) radii.put(k.toString(), v.toDouble())
            o.put("cornerR", radii)
            it.gradient?.let { g ->
                o.put("gradient", JSONObject().put("kind", g.kind.name).put("c1", g.c1).put("c2", g.c2).put("angle", g.angle.toDouble()))
            }
            arr.put(o)
        }
        return JSONObject().put("app", "Logoxels").put("version", 1).put("items", arr).toString()
    }

    /** Throws on a file that is not a Logoxels project. */
    fun readProject(text: String): List<Item> {
        val root = JSONObject(text)
        require(root.optString("app") == "Logoxels") { "Not a Logoxels project" }
        val arr = root.getJSONArray("items")
        val out = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val b = o.getJSONArray("bounds")
            val item = Item(
                o.getLong("id"), Kind.valueOf(o.getString("kind")), pathFrom(o.getJSONObject("path")),
                RectF(b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat()),
                if (o.isNull("fill")) null else o.getInt("fill"),
                if (o.isNull("stroke")) null else o.getInt("stroke"),
                o.getDouble("strokeWidth").toFloat(), o.getDouble("corner").toFloat(),
                o.getInt("sides"), o.getDouble("inner").toFloat(),
            )
            item.groupId = o.getLong("group")
            item.visible = o.getBoolean("visible")
            item.locked = o.getBoolean("locked")
            val radii = o.getJSONObject("cornerR")
            for (k in radii.keys()) item.setCornerAt(k.toInt(), radii.getDouble(k).toFloat())
            o.optJSONObject("gradient")?.let { g ->
                item.gradient = Gradient(GKind.valueOf(g.getString("kind")), g.getInt("c1"), g.getInt("c2"), g.getDouble("angle").toFloat())
            }
            out.add(item)
        }
        return out
    }
}
