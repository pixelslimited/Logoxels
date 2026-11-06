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
import ng.pixelslimited.logoxels.engine.geom.Warp
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** The whole document: shapes plus artboard size and background (null = transparent). */
class Doc(
    val items: List<Item>,
    val w: Float,
    val h: Float,
    val background: Int?,
    /** All artboards (project files only; exports use w, h and background of one board). */
    val boards: List<Board> = emptyList(),
    val active: Int = 0,
)

/** Writes the artboard to SVG, PNG, PDF, PSD, EPS, and saves/loads project files. */
object Export {

    private fun px(v: Float, k: Float) = (v * k).roundToInt().coerceAtLeast(1)

    /** PNG at up to [maxPx] on the long side; transparent unless the document has a background. */
    fun png(doc: Doc, out: OutputStream, maxPx: Int = 2000) {
        val k = maxPx / max(doc.w, doc.h)
        val bmp = Bitmap.createBitmap(px(doc.w, k), px(doc.h, k), Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            doc.background?.let { c.drawColor(it) }
            c.scale(k, k)
            Render.draw(c, doc.items)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            bmp.recycle()
        }
    }

    fun pdf(doc: Doc, out: OutputStream) {
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(px(doc.w, 1f), px(doc.h, 1f), 1).create())
            doc.background?.let { page.canvas.drawColor(it) }
            Render.draw(page.canvas, doc.items)
            pdf.finishPage(page)
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)

    private fun opacity(c: Int) = (c ushr 24) / 255f

    fun svg(doc: Doc): String {
        val defs = StringBuilder()
        val body = StringBuilder()
        doc.background?.let {
            body.append("<rect width=\"${doc.w}\" height=\"${doc.h}\" fill=\"${hex(it)}\"/>\n")
        }
        for (item in doc.items) {
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
                val cap = if (item.dash == 2) "round" else when (item.cap) { 1 -> "butt"; 2 -> "square"; else -> "round" }
                val join = when (item.join) { 1 -> "miter"; 2 -> "bevel"; else -> "round" }
                a.append(" stroke=\"${hex(strokeColor)}\" stroke-width=\"${item.strokeWidth}\" stroke-linejoin=\"$join\" stroke-linecap=\"$cap\"")
                val u = maxOf(item.strokeWidth, 1f)
                if (item.dash == 1) a.append(" stroke-dasharray=\"${u * 3f} ${u * 2f}\"")
                if (item.dash == 2) a.append(" stroke-dasharray=\"0.01 ${u * 2f}\"")
                if (opacity(strokeColor) < 1f) a.append(" stroke-opacity=\"${opacity(strokeColor)}\"")
            }
            if (item.opacity < 0.999f) a.append(" opacity=\"${item.opacity}\"")
            body.append(a).append("/>\n")
        }
        val head = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${doc.w}\" height=\"${doc.h}\" viewBox=\"0 0 ${doc.w} ${doc.h}\">\n"
        val defsTag = if (defs.isEmpty()) "" else "<defs>$defs</defs>\n"
        return head + defsTag + body + "</svg>\n"
    }

    /** Photoshop file: one transparent layer per visible item plus a merged image. */
    fun psd(doc: Doc, out: OutputStream, maxPx: Int = 2000) {
        val k = maxPx / max(doc.w, doc.h)
        val pw = px(doc.w, k)
        val ph = px(doc.h, k)
        val layers = ArrayList<PsdLayer>()
        doc.background?.let { layers.add(PsdLayer("Background", 0, 0, pw, ph, pixels(emptyList(), 0, 0, pw, ph, k, it))) }
        for (item in doc.items) {
            if (!item.visible) continue
            val b = RectF(item.box())
            val pad = if (item.stroke != null) item.strokeWidth / 2f else 0f
            b.inset(-pad - 1f, -pad - 1f)
            val l = maxOf(0, Math.floor((b.left * k).toDouble()).toInt())
            val t = maxOf(0, Math.floor((b.top * k).toDouble()).toInt())
            val r = minOf(pw, Math.ceil((b.right * k).toDouble()).toInt())
            val bt = minOf(ph, Math.ceil((b.bottom * k).toDouble()).toInt())
            if (r <= l || bt <= t) continue
            layers.add(PsdLayer(item.label(), l, t, r - l, bt - t, pixels(listOf(item), l, t, r - l, bt - t, k, null)))
        }
        Psd.write(pw, ph, layers, pixels(doc.items, 0, 0, pw, ph, k, doc.background), out)
    }

    private fun pixels(items: List<Item>, left: Int, top: Int, w: Int, h: Int, k: Float, bg: Int?): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            bg?.let { c.drawColor(it) }
            c.translate(-left.toFloat(), -top.toFloat())
            c.scale(k, k)
            Render.draw(c, items)
            val ints = IntArray(w * h)
            bmp.getPixels(ints, 0, w, 0, 0, w, h)
            val out = ByteArray(w * h * 4)
            for (i in ints.indices) {
                val v = ints[i]
                out[i * 4] = (v shr 16).toByte()
                out[i * 4 + 1] = (v shr 8).toByte()
                out[i * 4 + 2] = v.toByte()
                out[i * 4 + 3] = (v ushr 24).toByte()
            }
            return out
        } finally {
            bmp.recycle()
        }
    }

    private fun rgb(c: Int) = "${PathData.num(((c shr 16) and 0xFF) / 255.0)} ${PathData.num(((c shr 8) and 0xFF) / 255.0)} ${PathData.num((c and 0xFF) / 255.0)}"

    private fun epsDash(item: Item): String {
        val u2 = PathData.num(maxOf(item.strokeWidth, 1f).toDouble() * 2.0)
        val u3 = PathData.num(maxOf(item.strokeWidth, 1f).toDouble() * 3.0)
        return when (item.dash) {
            1 -> "[$u3 $u2] 0 setdash"
            2 -> "[0.01 $u2] 0 setdash"
            else -> "[] 0 setdash"
        }
    }

    private fun psColors(c1: Int, c2: Int) =
        "/Function << /FunctionType 2 /Domain [0 1] /C0 [${rgb(c1)}] /C1 [${rgb(c2)}] /N 1 >>"

    /** Encapsulated PostScript (level 3). Transparency is not supported by EPS, so alpha is ignored. */
    fun eps(doc: Doc): String {
        val w = ceil(doc.w.toDouble()).toInt()
        val h = doc.h.toDouble()
        val sb = StringBuilder("%!PS-Adobe-3.0 EPSF-3.0\n%%BoundingBox: 0 0 $w ${ceil(h).toInt()}\n%%LanguageLevel: 3\n%%EndComments\n")
        doc.background?.let { sb.append("${rgb(it)} setrgbcolor 0 0 ${PathData.num(doc.w.toDouble())} ${PathData.num(h)} rectfill\n") }
        for (item in doc.items) {
            if (!item.visible) continue
            val rp = item.renderPath()
            val path = PathData.postScript(PathBridge.toContours(rp), h)
            if (path.isEmpty()) continue
            val eo = rp.fillType == Path.FillType.EVEN_ODD || rp.fillType == Path.FillType.INVERSE_EVEN_ODD
            val g = item.gradient
            if (item.fill != null && g != null) {
                val v = g.geometry(item.box())
                sb.append("gsave newpath $path ${if (eo) "eoclip" else "clip"} newpath\n")
                if (g.kind == GKind.LINEAR) {
                    sb.append("<< /ShadingType 2 /ColorSpace /DeviceRGB /Coords [${PathData.num(v[0].toDouble())} ${PathData.num(h - v[1])} ${PathData.num(v[2].toDouble())} ${PathData.num(h - v[3])}] ")
                } else {
                    val cx = PathData.num(v[0].toDouble())
                    val cy = PathData.num(h - v[1])
                    sb.append("<< /ShadingType 3 /ColorSpace /DeviceRGB /Coords [$cx $cy 0 $cx $cy ${PathData.num(v[2].toDouble())}] ")
                }
                sb.append("${psColors(g.c1, g.c2)} /Extend [true true] >> shfill grestore\n")
            } else if (item.fill != null) {
                sb.append("${rgb(item.fill!!)} setrgbcolor newpath $path ${if (eo) "eofill" else "fill"}\n")
            }
            val sc = item.stroke
            if (sc != null && item.strokeWidth > 0f) {
                sb.append("${rgb(sc)} setrgbcolor ${PathData.num(item.strokeWidth.toDouble())} setlinewidth ${item.join.let { if (it == 1) 0 else if (it == 2) 2 else 1 }} setlinejoin ${if (item.dash == 2) 1 else when (item.cap) { 1 -> 0; 2 -> 2; else -> 1 }} setlinecap ${epsDash(item)} newpath $path stroke [] 0 setdash\n")
            }
        }
        sb.append("showpage\n%%EOF\n")
        return sb.toString()
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

    private fun textJson(t: TextSpec): JSONObject {
        val o = JSONObject()
            .put("t", t.text).put("x", t.x.toDouble()).put("y", t.y.toDouble())
            .put("font", t.font).put("bold", t.bold).put("italic", t.italic).put("size", t.size.toDouble())
            .put("mode", t.mode.name).put("warp", t.warp.name).put("amount", t.amount.toDouble())
        t.guide?.let { o.put("guide", pathJson(it)) }
        t.area?.let { o.put("area", pathJson(it)) }
        val obs = JSONArray()
        for (p in t.obstacles) obs.put(pathJson(p))
        return o.put("obstacles", obs)
    }

    private fun textFrom(o: JSONObject): TextSpec {
        val obs = o.optJSONArray("obstacles")
        return TextSpec(
            o.getString("t"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
            o.getInt("font"), o.getBoolean("bold"), o.getBoolean("italic"), o.getDouble("size").toFloat(),
            TextMode.valueOf(o.getString("mode")),
            o.optJSONObject("guide")?.let { pathFrom(it) }, o.optJSONObject("area")?.let { pathFrom(it) },
            if (obs == null) emptyList() else List(obs.length()) { pathFrom(obs.getJSONObject(it)) },
            Warp.valueOf(o.getString("warp")), o.getDouble("amount").toFloat(),
        )
    }

    fun projectJson(doc: Doc): String {
        val arr = JSONArray()
        for (it in doc.items) {
            val o = JSONObject()
                .put("id", it.id).put("kind", it.kind.name)
                .put("path", pathJson(it.path)).put("bounds", rectJson(it.bounds))
                .put("fill", it.fill ?: JSONObject.NULL).put("stroke", it.stroke ?: JSONObject.NULL)
                .put("strokeWidth", it.strokeWidth.toDouble()).put("corner", it.corner.toDouble())
                .put("sides", it.sides).put("inner", it.inner.toDouble())
                .put("xf", JSONArray().apply { val v = FloatArray(9); it.xf.getValues(v); v.forEach { f -> put(f.toDouble()) } })
                .put("opacity", it.opacity.toDouble()).put("dash", it.dash).put("cap", it.cap).put("join", it.join)
                .put("group", it.groupId).put("visible", it.visible).put("locked", it.locked)
            val radii = JSONObject()
            for ((k, v) in it.cornerR) radii.put(k.toString(), v.toDouble())
            o.put("cornerR", radii)
            it.gradient?.let { g ->
                o.put("gradient", JSONObject().put("kind", g.kind.name).put("c1", g.c1).put("c2", g.c2).put("angle", g.angle.toDouble()))
            }
            it.text?.let { t -> o.put("text", textJson(t)) }
            arr.put(o)
        }
        val boards = JSONArray()
        for (b in doc.boards) {
            boards.put(
                JSONObject().put("name", b.name)
                    .put("rect", JSONArray().put(b.rect.left.toDouble()).put(b.rect.top.toDouble()).put(b.rect.right.toDouble()).put(b.rect.bottom.toDouble()))
                    .put("bg", b.bg ?: JSONObject.NULL),
            )
        }
        return JSONObject().put("app", "Logoxels").put("version", 1).put("items", arr)
            .put("width", doc.w.toDouble()).put("height", doc.h.toDouble()).put("background", doc.background ?: JSONObject.NULL)
            .put("boards", boards).put("active", doc.active)
            .toString()
    }

    /** Throws on a file that is not a Logoxels project. */
    fun readProject(text: String): Doc {
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
            o.optJSONArray("xf")?.let { a ->
                item.xf.setValues(FloatArray(9) { k -> a.getDouble(k).toFloat() })
            }
            o.optJSONObject("text")?.let { item.text = textFrom(it) }
            item.opacity = o.optDouble("opacity", 1.0).toFloat()
            item.dash = o.optInt("dash", 0)
            item.cap = o.optInt("cap", 0)
            item.join = o.optInt("join", 0)
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
        val boards = ArrayList<Board>()
        root.optJSONArray("boards")?.let { ba ->
            for (i in 0 until ba.length()) {
                val o = ba.getJSONObject(i)
                val r = o.getJSONArray("rect")
                boards.add(
                    Board(
                        o.getString("name"),
                        RectF(r.getDouble(0).toFloat(), r.getDouble(1).toFloat(), r.getDouble(2).toFloat(), r.getDouble(3).toFloat()),
                        if (o.isNull("bg")) null else o.getInt("bg"),
                    ),
                )
            }
        }
        return Doc(
            out, root.optDouble("width", 1000.0).toFloat(), root.optDouble("height", 1000.0).toFloat(),
            if (root.isNull("background")) null else root.optInt("background"),
            boards, root.optInt("active", 0),
        )
    }
}
