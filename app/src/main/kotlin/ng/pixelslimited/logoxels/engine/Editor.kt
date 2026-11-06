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
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Tool { SELECT, RECT, ELLIPSE, POLYGON, STAR, LINE }
enum class PfOp { UNION, MINUS_FRONT, INTERSECT, EXCLUDE }
enum class AlignTo { LEFT, CENTER, RIGHT, TOP, MIDDLE, BOTTOM }
enum class Mode { NONE, CREATE, MOVE, RESIZE, TRANSFORM }

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

    var scale by mutableFloatStateOf(1f)
        private set
    var tx by mutableFloatStateOf(0f)
        private set
    var ty by mutableFloatStateOf(0f)
        private set

    private var fitted = false
    private var nextId = 1L
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
        selection = items.map { it.id }.toSet()
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
        creating = null
        pushed = false
        downHitId = null
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
        if (!wasSelected) selection = if (multi) selection + hit.id else setOf(hit.id)
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
            Mode.NONE -> {
                tx += screen.x - lastScreen.x
                ty += screen.y - lastScreen.y
            }
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
                if (!moved && multi && wasSelected && id != null) { selection = selection - id; touch() }
            }
            Mode.NONE -> if (!moved && !multi && selection.isNotEmpty()) { selection = emptySet(); touch() }
            else -> Unit
        }
        creating = null
    }

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

    fun setCorner(v: Float) = slide { selectedItems().forEach { it.corner = v } }

    fun setSides(n: Int) = slide {
        selectedItems().forEach { if (it.kind == Kind.POLYGON || it.kind == Kind.STAR) { it.sides = n; it.rebuild() } }
    }

    fun setInner(v: Float) = slide {
        selectedItems().forEach { if (it.kind == Kind.STAR) { it.inner = v; it.rebuild() } }
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
            val copies = sel.map { it.copyWithId(nextId++).also { c -> c.offset(24f, 24f) } }
            items = items + copies
            selection = copies.map { it.id }.toSet()
        }
    }
}
