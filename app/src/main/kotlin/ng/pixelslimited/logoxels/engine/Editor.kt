package ng.pixelslimited.logoxels.engine

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import ng.pixelslimited.logoxels.engine.geom.Rounder
import java.util.Collections
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Tool { SELECT, RECT, ELLIPSE, POLYGON, STAR, LINE, CORNERS, PEN, KNIFE }
enum class PfOp { UNION, MINUS_FRONT, INTERSECT, EXCLUDE }
enum class AlignTo { LEFT, CENTER, RIGHT, TOP, MIDDLE, BOTTOM }
enum class Mode { NONE, CREATE, MOVE, RESIZE, TRANSFORM, PICK, PEN, KNIFE }

/** A pen-tool point with its two Bezier handles (equal to [p] when the point is a sharp corner). */
class Anchor(val p: Offset, val hin: Offset, val hout: Offset)

private const val DARK = 0xFF111827.toInt()

/** Document + interaction state. UI reads [version] to know when to redraw. */
class Editor {
    val artboard = RectF(0f, 0f, 1000f, 1000f)

    var items: List<Item> = emptyList()
        private set
    var version by mutableIntStateOf(0)
        private set
    var selection by mutableStateOf(emptySet<Long>())
        private set
    var tool by mutableStateOf(Tool.SELECT)
    var multi by mutableStateOf(false)
    var lockRatio by mutableStateOf(false)
    var nextFill by mutableIntStateOf(0xFF1E88E5.toInt())
        private set
    /** Corner number picked with the Corners tool (see Rounder.nodes). */
    var cornerSel by mutableStateOf<Int?>(null)
        private set

    /** Points of the path being drawn with the pen tool. */
    var pen by mutableStateOf(emptyList<Anchor>())
        private set

    /** Cut line being dragged with the knife tool, in artboard units. */
    var knife by mutableStateOf<Pair<Offset, Offset>?>(null)
        private set

    var scale by mutableFloatStateOf(1f)
        private set
    var tx by mutableFloatStateOf(0f)
        private set
    var ty by mutableFloatStateOf(0f)
        private set

    private var fitted = false
    private var nextId = 1L
    private var nextGroup = 1L
    private var penClosing = false
    private val undoStack = ArrayDeque<List<Item>>()
    private val redoStack = ArrayDeque<List<Item>>()

    private fun touch() { version++ }

    // ---------- history ----------
    private fun snapshot() = items.map { it.copy() }

    private fun pushUndo() {
        undoStack.addLast(snapshot())
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        touch()
    }

    private fun change(block: () -> Unit) {
        pushUndo()
        block()
        touch()
    }

    val canUndo: Boolean get() { version; return undoStack.isNotEmpty() }
    val canRedo: Boolean get() { version; return redoStack.isNotEmpty() }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(snapshot())
        items = undoStack.removeLast()
        keepValidSelection()
        touch()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(snapshot())
        items = redoStack.removeLast()
        keepValidSelection()
        touch()
    }

    private fun keepValidSelection() {
        val ids = items.map { it.id }.toSet()
        selection = selection.filter { it in ids }.toSet()
        if (selection.size != 1) cornerSel = null
    }

    // ---------- view ----------
    fun onViewSize(w: Float, h: Float) {
        if (fitted || w <= 0f || h <= 0f) return
        fitted = true
        val s = min(w, h) * 0.92f / artboard.width()
        scale = s
        tx = (w - artboard.width() * s) / 2f
        ty = (h - artboard.height() * s) / 2f
    }

    fun toDoc(p: Offset) = Offset((p.x - tx) / scale, (p.y - ty) / scale)
    fun toScreen(x: Float, y: Float) = Offset(x * scale + tx, y * scale + ty)

    fun transformView(zoom: Float, pan: Offset, centroid: Offset) {
        val ns = (scale * zoom).coerceIn(0.1f, 20f)
        val k = ns / scale
        tx = centroid.x - (centroid.x - tx) * k + pan.x
        ty = centroid.y - (centroid.y - ty) * k + pan.y
        scale = ns
    }

    // ---------- selection ----------
    fun selectedItems(): List<Item> = items.filter { it.id in selection }

    fun selectionBox(): RectF? {
        val sel = selectedItems()
        if (sel.isEmpty()) return null
        val r = sel[0].box()
        for (i in 1 until sel.size) r.union(sel[i].box())
        return r
    }

    fun selectAll() {
        selection = items.filter { it.visible && !it.locked }.map { it.id }.toSet()
        touch()
    }

    private val hitClip = Region(-20000, -20000, 20000, 20000)

    private fun regionHas(p: Path, x: Int, y: Int): Boolean {
        val r = Region()
        r.setPath(p, hitClip)
        return r.contains(x, y)
    }

    private fun stroked(p: Path, w: Float): Path {
        val out = Path()
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = w
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }.getFillPath(p, out)
        return out
    }

    private fun hitTest(d: Offset): Item? {
        val tol = 12f / scale
        val x = d.x.roundToInt()
        val y = d.y.roundToInt()
        for (i in items.indices.reversed()) {
            val item = items[i]
            if (!item.visible || item.locked) continue
            val rp = item.renderPath()
            if (item.fill != null && regionHas(rp, x, y)) return item
            val w = when {
                item.stroke != null -> max(item.strokeWidth, tol * 2f)
                item.fill == null -> tol * 2f
                else -> 0f
            }
            if (w > 0f && regionHas(stroked(rp, w), x, y)) return item
        }
        return null
    }

    // ---------- gestures ----------
    private var downDoc = Offset.Zero
    private var lastDoc = Offset.Zero
    private var lastScreen = Offset.Zero
    private var creating: Item? = null
    private var pushed = false
    private var handle = -1
    private var origSel: List<Item> = emptyList()
    private var origBox = RectF()
    private var downHitId: Long? = null
    private var downScreen = Offset.Zero
    private var downGroup: Set<Long> = emptySet()
    private var pickRadius = 0f
    private var wasSelected = false

    private fun corner(b: RectF, i: Int) = when (i) {
        0 -> Offset(b.left, b.top)
        1 -> Offset(b.right, b.top)
        2 -> Offset(b.right, b.bottom)
        else -> Offset(b.left, b.bottom)
    }

    fun beginGesture(screen: Offset, handleRadius: Float): Mode {
        downDoc = toDoc(screen)
        lastDoc = downDoc
        lastScreen = screen
        downScreen = screen
        pickRadius = handleRadius
        creating = null
        pushed = false
        downHitId = null
        if (tool == Tool.CORNERS) return Mode.PICK
        if (tool == Tool.PEN) {
            penClosing = pen.size >= 3 && (toScreen(pen[0].p.x, pen[0].p.y) - screen).getDistance() <= handleRadius
            if (!penClosing) pen = pen + Anchor(downDoc, downDoc, downDoc)
            return Mode.PEN
        }
        if (tool == Tool.KNIFE) {
            knife = downDoc to downDoc
            return Mode.KNIFE
        }
        if (tool != Tool.SELECT) return Mode.CREATE
        val box = selectionBox()
        if (box != null) {
            for (i in 0..3) {
                val c = corner(box, i)
                if ((toScreen(c.x, c.y) - screen).getDistance() <= handleRadius) {
                    handle = i
                    origBox = RectF(box)
                    origSel = selectedItems().map { it.copy() }
                    return Mode.RESIZE
                }
            }
        }
        val hit = hitTest(downDoc) ?: return Mode.NONE
        downHitId = hit.id
        wasSelected = hit.id in selection
        downGroup = groupIds(hit)
        if (!wasSelected) {
            selection = if (multi) selection + downGroup else downGroup
            cornerSel = null
        }
        touch()
        return Mode.MOVE
    }

    fun dragGesture(mode: Mode, screen: Offset) {
        val doc = toDoc(screen)
        when (mode) {
            Mode.CREATE -> dragCreate(doc)
            Mode.MOVE -> {
                if (!pushed) { pushUndo(); pushed = true }
                val dx = doc.x - lastDoc.x
                val dy = doc.y - lastDoc.y
                selectedItems().forEach { it.offset(dx, dy) }
                touch()
            }
            Mode.RESIZE -> dragResize(doc)
            Mode.NONE, Mode.PICK -> {
                tx += screen.x - lastScreen.x
                ty += screen.y - lastScreen.y
            }
            Mode.PEN -> if (!penClosing && pen.isNotEmpty()) {
                val a = pen.last()
                pen = pen.dropLast(1) + Anchor(a.p, Offset(2f * a.p.x - doc.x, 2f * a.p.y - doc.y), doc)
            }
            Mode.KNIFE -> knife = downDoc to doc
            Mode.TRANSFORM -> Unit
        }
        lastDoc = doc
        lastScreen = screen
    }

    fun endGesture(mode: Mode, moved: Boolean) {
        when (mode) {
            Mode.CREATE -> finishCreate(moved)
            Mode.MOVE -> {
                val id = downHitId
                if (!moved && multi && wasSelected && id != null) { selection = selection - downGroup; touch() }
            }
            Mode.NONE -> if (!moved && !multi && selection.isNotEmpty()) { selection = emptySet(); touch() }
            Mode.PICK -> if (!moved) pickCorner()
            Mode.PEN -> if (penClosing && !moved) penFinish(true)
            Mode.KNIFE -> {
                val k = knife
                knife = null
                if (k != null && moved) cut(k.first, k.second)
            }
            else -> Unit
        }
        creating = null
    }

    private fun pickCorner() {
        val item = selectedItems().singleOrNull()
        if (item != null) {
            var best = -1
            var bestD = pickRadius
            for (n in item.cornerNodes()) {
                val d = (toScreen(n.at.x.toFloat(), n.at.y.toFloat()) - downScreen).getDistance()
                if (d <= bestD) { bestD = d; best = n.index }
            }
            if (best >= 0) { cornerSel = best; touch(); return }
        }
        val hit = hitTest(downDoc)
        selection = if (hit != null) setOf(hit.id) else emptySet()
        cornerSel = null
        touch()
    }

    fun cornerNodes(): List<Rounder.Node> = selectedItems().singleOrNull()?.cornerNodes() ?: emptyList()

    private fun kindFor(t: Tool) = when (t) {
        Tool.RECT -> Kind.RECT
        Tool.ELLIPSE -> Kind.ELLIPSE
        Tool.POLYGON -> Kind.POLYGON
        Tool.STAR -> Kind.STAR
        else -> Kind.PATH
    }

    private fun newShape(kind: Kind, r: RectF): Item {
        val sides = if (kind == Kind.POLYGON) 6 else 5
        return Item(nextId++, kind, buildShape(kind, r, sides, 0.5f), RectF(r), nextFill, null, 0f, 0f, sides, 0.5f)
    }

    private fun newLine(a: Offset, b: Offset): Item {
        val p = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y) }
        return Item(nextId++, Kind.PATH, p, RectF(), null, DARK, 10f)
    }

    private fun dragCreate(doc: Offset) {
        var c = creating
        if (c == null) {
            pushUndo()
            c = if (tool == Tool.LINE) newLine(downDoc, doc)
            else newShape(kindFor(tool), RectF(downDoc.x, downDoc.y, downDoc.x, downDoc.y))
            items = items + c
            selection = setOf(c.id)
            creating = c
        }
        if (c.kind == Kind.PATH) {
            c.path = Path().apply { moveTo(downDoc.x, downDoc.y); lineTo(doc.x, doc.y) }
        } else {
            c.bounds = RectF(min(downDoc.x, doc.x), min(downDoc.y, doc.y), max(downDoc.x, doc.x), max(downDoc.y, doc.y))
            c.rebuild()
        }
        touch()
    }

    private fun finishCreate(moved: Boolean) {
        val c = creating
        if (c == null) {
            if (moved) return
            pushUndo()
            val h = 90f
            val d = downDoc
            val item = if (tool == Tool.LINE) newLine(Offset(d.x - h, d.y + h), Offset(d.x + h, d.y - h))
            else newShape(kindFor(tool), RectF(d.x - h, d.y - h, d.x + h, d.y + h))
            items = items + item
            selection = setOf(item.id)
            touch()
        } else {
            val b = c.box()
            if (b.width() < 4f && b.height() < 4f) {
                items = items - c
                undoStack.removeLastOrNull()
                selection = emptySet()
                touch()
            }
        }
    }

    private fun dragResize(doc: Offset) {
        if (!pushed) { pushUndo(); pushed = true }
        val grab = corner(origBox, handle)
        val anchor = corner(origBox, (handle + 2) % 4)
        var sx = if (grab.x == anchor.x) 1f else (doc.x - anchor.x) / (grab.x - anchor.x)
        var sy = if (grab.y == anchor.y) 1f else (doc.y - anchor.y) / (grab.y - anchor.y)
        if (lockRatio) {
            val s = if (abs(sx) > abs(sy)) sx else sy
            sx = s
            sy = s
        }
        if (abs(sx) < 0.02f) sx = 0.02f
        if (abs(sy) < 0.02f) sy = 0.02f
        val m = Matrix().apply { setScale(sx, sy, anchor.x, anchor.y) }
        for (o in origSel) items.firstOrNull { it.id == o.id }?.applyFrom(o, m)
        touch()
    }

    // ---------- style ----------
    private var sliding = false

    private fun slide(block: () -> Unit) {
        if (!sliding) { pushUndo(); sliding = true }
        block()
        touch()
    }

    fun endSlide() { sliding = false }

    fun setFill(c: Int?) {
        if (c != null) nextFill = c
        if (selection.isNotEmpty()) change { selectedItems().forEach { it.fill = c } }
    }

    fun setStroke(c: Int?) {
        if (selection.isEmpty()) return
        change {
            selectedItems().forEach {
                it.stroke = c
                if (c != null && it.strokeWidth == 0f) it.strokeWidth = 10f
            }
        }
    }

    fun setStrokeWidth(v: Float) = slide {
        selectedItems().forEach {
            it.strokeWidth = v
            if (v > 0f && it.stroke == null) it.stroke = DARK
        }
    }

    /** Every corner of the selection gets radius [v] (clears individual radii). */
    fun setCorner(v: Float) = slide {
        selectedItems().forEach {
            it.corner = v
            it.clearCornerOverrides()
        }
    }

    /** Only the corner picked with the Corners tool. */
    fun setCornerSelected(v: Float) = slide {
        val s = cornerSel
        if (s != null) selectedItems().singleOrNull()?.setCornerAt(s, v)
    }

    fun resetCorners() {
        if (selection.isEmpty()) return
        change {
            selectedItems().forEach {
                it.corner = 0f
                it.clearCornerOverrides()
            }
            cornerSel = null
        }
    }

    fun setSides(n: Int) = slide {
        cornerSel = null
        selectedItems().forEach {
            if (it.kind == Kind.POLYGON || it.kind == Kind.STAR) {
                it.sides = n
                it.clearCornerOverrides()
                it.rebuild()
            }
        }
    }

    fun setInner(v: Float) = slide {
        selectedItems().forEach { if (it.kind == Kind.STAR) { it.inner = v; it.rebuild() } }
    }

    // ---------- pen ----------
    fun penFinish(closed: Boolean) {
        val a = pen
        if (a.size < 2) {
            pen = emptyList()
            return
        }
        val cl = closed && a.size >= 3
        val path = Path()
        path.moveTo(a[0].p.x, a[0].p.y)
        fun link(from: Anchor, to: Anchor) {
            if (from.hout == from.p && to.hin == to.p) path.lineTo(to.p.x, to.p.y)
            else path.cubicTo(from.hout.x, from.hout.y, to.hin.x, to.hin.y, to.p.x, to.p.y)
        }
        for (i in 1 until a.size) link(a[i - 1], a[i])
        if (cl) {
            link(a.last(), a[0])
            path.close()
        }
        val item = Item(nextId++, Kind.PATH, path, RectF(), if (cl) nextFill else null, if (cl) null else DARK, if (cl) 0f else 10f)
        pushUndo()
        items = items + item
        selection = setOf(item.id)
        pen = emptyList()
        tool = Tool.SELECT
        touch()
    }

    fun penUndoPoint() { pen = pen.dropLast(1) }

    fun penCancel() { pen = emptyList() }

    // ---------- knife ----------
    private fun cutPiece(src: Item, p: Path) =
        Item(nextId++, Kind.PATH, p, RectF(), src.fill, src.stroke, src.strokeWidth).also { it.groupId = src.groupId }

    /** Slices every shape crossed by the segment a-b along the (extended) line through it. */
    private fun cut(a: Offset, b: Offset) {
        val len = hypot(b.x - a.x, b.y - a.y)
        if (len < 6f / scale) return
        val dx = (b.x - a.x) / len
        val dy = (b.y - a.y) / len
        val nx = -dy
        val ny = dx
        val big = 6000f
        fun half(sign: Float) = Path().apply {
            moveTo(a.x - dx * big, a.y - dy * big)
            lineTo(a.x + dx * big, a.y + dy * big)
            lineTo(a.x + dx * big + nx * big * sign, a.y + dy * big + ny * big * sign)
            lineTo(a.x - dx * big + nx * big * sign, a.y - dy * big + ny * big * sign)
            close()
        }
        val left = half(1f)
        val right = half(-1f)
        val strip = stroked(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y) }, 2f)
        val pool = if (selection.isNotEmpty()) selectedItems() else items
        val replaced = HashMap<Long, List<Item>>()
        for (item in pool) {
            if (!item.visible || item.locked || item.fill == null) continue
            val shape = item.renderPath()
            val crossed = Path(shape)
            if (!crossed.op(strip, Path.Op.INTERSECT) || crossed.isEmpty) continue
            val l = Path(shape)
            val r = Path(shape)
            if (!l.op(left, Path.Op.INTERSECT) || !r.op(right, Path.Op.INTERSECT) || l.isEmpty || r.isEmpty) continue
            replaced[item.id] = listOf(cutPiece(item, l), cutPiece(item, r))
        }
        if (replaced.isEmpty()) return
        change {
            val out = ArrayList<Item>()
            for (item in items) {
                val pieces = replaced[item.id]
                if (pieces == null) out.add(item) else out.addAll(pieces)
            }
            items = out
            selection = replaced.values.flatten().map { it.id }.toSet()
            cornerSel = null
        }
    }

    // ---------- groups, clipping, layers ----------
    private fun groupIds(item: Item): Set<Long> =
        if (item.groupId == 0L) setOf(item.id) else items.filter { it.groupId == item.groupId }.map { it.id }.toSet()

    fun group() {
        val sel = selectedItems()
        if (sel.size < 2) return
        change {
            val g = nextGroup++
            sel.forEach { it.groupId = g }
            val top = items.indexOfLast { it.id in selection }
            val at = items.subList(0, top + 1).count { it.id !in selection }
            val rest = items.filter { it.id !in selection }.toMutableList()
            rest.addAll(at, sel)
            items = rest
        }
    }

    fun ungroup() {
        val sel = selectedItems()
        if (sel.none { it.groupId != 0L }) return
        change { sel.forEach { it.groupId = 0L } }
    }

    /** The top-most selected shape trims the others to its outline and is removed. */
    fun clipToTop() {
        val sel = selectedItems()
        if (sel.size < 2) return
        val mask = sel.last()
        val targets = sel.dropLast(1).filter { it.fill != null }
        if (targets.isEmpty()) return
        val maskPath = mask.renderPath()
        change {
            val dead = HashSet<Long>()
            dead.add(mask.id)
            for (t in targets) {
                val r = Path(t.renderPath())
                if (!r.op(maskPath, Path.Op.INTERSECT) || r.isEmpty) {
                    dead.add(t.id)
                    continue
                }
                t.kind = Kind.PATH
                t.path = r
                t.corner = 0f
                t.clearCornerOverrides()
            }
            items = items.filter { it.id !in dead }
            selection = targets.map { it.id }.filter { it !in dead }.toSet()
            cornerSel = null
        }
    }

    fun selectFromLayers(item: Item) {
        if (item.locked || !item.visible) return
        val ids = groupIds(item)
        selection = if (multi) { if (item.id in selection) selection - ids else selection + ids } else ids
        cornerSel = null
        touch()
    }

    fun toggleVisible(item: Item) = change {
        item.visible = !item.visible
        if (!item.visible) selection = selection - item.id
    }

    fun toggleLock(item: Item) = change {
        item.locked = !item.locked
        if (item.locked) selection = selection - item.id
    }

    fun moveLayer(item: Item, up: Boolean) {
        val i = items.indexOfFirst { it.id == item.id }
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j !in items.indices) return
        change {
            val l = items.toMutableList()
            Collections.swap(l, i, j)
            items = l
        }
    }

    // ---------- pathfinder ----------
    fun combine(op: PfOp) {
        val sel = selectedItems()
        if (sel.size < 2) return
        val aop = when (op) {
            PfOp.UNION -> Path.Op.UNION
            PfOp.MINUS_FRONT -> Path.Op.DIFFERENCE
            PfOp.INTERSECT -> Path.Op.INTERSECT
            PfOp.EXCLUDE -> Path.Op.XOR
        }
        val result = Path(sel[0].renderPath())
        for (i in 1 until sel.size) result.op(sel[i].renderPath(), aop)
        val base = sel[0]
        val merged = Item(nextId++, Kind.PATH, result, RectF(), base.fill ?: sel.firstNotNullOfOrNull { it.fill }, base.stroke, base.strokeWidth)
        val idx = items.indexOfFirst { it.id == base.id }
        change {
            val list = items.filter { it.id !in selection }.toMutableList()
            list.add(min(idx, list.size), merged)
            items = list
            selection = setOf(merged.id)
        }
    }

    // ---------- align / distribute ----------
    fun align(a: AlignTo) {
        val sel = selectedItems()
        if (sel.isEmpty()) return
        val ref = if (sel.size == 1) RectF(artboard) else selectionBox() ?: return
        change {
            sel.forEach {
                val b = it.box()
                when (a) {
                    AlignTo.LEFT -> it.offset(ref.left - b.left, 0f)
                    AlignTo.CENTER -> it.offset(ref.centerX() - b.centerX(), 0f)
                    AlignTo.RIGHT -> it.offset(ref.right - b.right, 0f)
                    AlignTo.TOP -> it.offset(0f, ref.top - b.top)
                    AlignTo.MIDDLE -> it.offset(0f, ref.centerY() - b.centerY())
                    AlignTo.BOTTOM -> it.offset(0f, ref.bottom - b.bottom)
                }
            }
        }
    }

    fun distribute(horizontal: Boolean) {
        val sel = selectedItems()
        if (sel.size < 3) return
        val sorted = sel.sortedBy { if (horizontal) it.box().left else it.box().top }
        val first = sorted.first().box()
        val last = sorted.last().box()
        var total = 0f
        sorted.forEach { val b = it.box(); total += if (horizontal) b.width() else b.height() }
        val span = if (horizontal) last.right - first.left else last.bottom - first.top
        val gap = (span - total) / (sorted.size - 1)
        var pos = if (horizontal) first.left else first.top
        change {
            sorted.forEach {
                val b = it.box()
                if (horizontal) { it.offset(pos - b.left, 0f); pos += b.width() + gap }
                else { it.offset(0f, pos - b.top); pos += b.height() + gap }
            }
        }
    }

    // ---------- order / edit ----------
    fun reorder(front: Boolean, toEnd: Boolean) {
        if (selection.isEmpty()) return
        val list = items.toMutableList()
        change {
            if (toEnd) {
                val s = list.filter { it.id in selection }
                list.removeAll(s)
                if (front) list.addAll(s) else list.addAll(0, s)
            } else if (front) {
                for (i in list.size - 2 downTo 0) {
                    if (list[i].id in selection && list[i + 1].id !in selection) Collections.swap(list, i, i + 1)
                }
            } else {
                for (i in 1 until list.size) {
                    if (list[i].id in selection && list[i - 1].id !in selection) Collections.swap(list, i, i - 1)
                }
            }
            items = list
        }
    }

    fun deleteSelected() {
        if (selection.isEmpty()) return
        change {
            items = items.filter { it.id !in selection }
            selection = emptySet()
        }
    }

    fun duplicateSelected() {
        val sel = selectedItems()
        if (sel.isEmpty()) return
        change {
            val groupMap = HashMap<Long, Long>()
            val copies = sel.map { s ->
                s.copyWithId(nextId++).also { c ->
                    c.offset(24f, 24f)
                    if (s.groupId != 0L) c.groupId = groupMap.getOrPut(s.groupId) { nextGroup++ }
                }
            }
            items = items + copies
            selection = copies.map { it.id }.toSet()
        }
    }
}
