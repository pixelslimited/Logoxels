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
import ng.pixelslimited.logoxels.engine.geom.NodePath
import ng.pixelslimited.logoxels.engine.geom.Nodes
import ng.pixelslimited.logoxels.engine.geom.P
import ng.pixelslimited.logoxels.engine.geom.PathBridge
import ng.pixelslimited.logoxels.engine.geom.PathHit
import ng.pixelslimited.logoxels.engine.geom.Rounder
import ng.pixelslimited.logoxels.engine.geom.Trace
import ng.pixelslimited.logoxels.engine.geom.Warp
import java.util.Collections
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Tool { SELECT, RECT, ELLIPSE, POLYGON, STAR, LINE, CORNERS, PEN, KNIFE, NODES, SCISSORS, TEXT, EYEDROPPER }
enum class PfOp { UNION, MINUS_FRONT, INTERSECT, EXCLUDE }
enum class AlignTo { LEFT, CENTER, RIGHT, TOP, MIDDLE, BOTTOM }
enum class Mode { NONE, CREATE, MOVE, RESIZE, TRANSFORM, PICK, PEN, KNIFE, NODE, ROTATE, GUIDE }

/** A pen-tool point with its two Bezier handles (equal to [p] when the point is a sharp corner). */
class Anchor(val p: Offset, val hin: Offset, val hout: Offset)

private const val DARK = 0xFF111827.toInt()
private const val ROT_OFFSET = 64f
private const val GUIDE_GRAB = 16f

/** A ruler guide: a vertical line at x = [pos], or a horizontal line at y = [pos]. */
class Guide(val vertical: Boolean, val pos: Float)

/** Document + interaction state. UI reads [version] to know when to redraw. */
class Editor {
    val artboard = RectF(0f, 0f, 1000f, 1000f)

    /** Artboard fill; null leaves it transparent in exports. */
    var background by mutableStateOf<Int?>(null)
        private set

    fun doc() = Doc(items, artboard.width(), artboard.height(), background)

    fun setArtboard(w: Float, h: Float) {
        artboard.set(0f, 0f, w.coerceIn(100f, 8000f), h.coerceIn(100f, 8000f))
        touch()
    }

    fun setBackground(c: Int?) {
        background = c
        touch()
    }

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

    /** Corner point picked with the Nodes tool: (contour, point). */
    var nodeSel by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    var showGrid by mutableStateOf(false)
    var snapGrid by mutableStateOf(false)
    var snapObjects by mutableStateOf(true)
    var showRulers by mutableStateOf(true)
    var gridSize by mutableFloatStateOf(50f)
    var guides by mutableStateOf(emptyList<Guide>())
        private set

    /** Alignment lines flashed while dragging with snapping on. */
    var snapHints by mutableStateOf(emptyList<Guide>())
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
        textEditing = 0L
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

    /** Replaces the whole document with [list] (opening a project) and clears history. */
    fun loadProject(doc: Doc) {
        val list = doc.items
        artboard.set(0f, 0f, doc.w, doc.h)
        background = doc.background
        items = list
        nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1
        nextGroup = (list.maxOfOrNull { it.groupId } ?: 0L) + 1
        undoStack.clear()
        redoStack.clear()
        trace = null
        traceItem = 0L
        selection = emptySet()
        cornerSel = null
        nodeSel = null
        pen = emptyList()
        fitView()
        touch()
    }

    private fun keepValidSelection() {
        val ids = items.map { it.id }.toSet()
        selection = selection.filter { it in ids }.toSet()
        if (selection.size != 1) cornerSel = null
    }

    // ---------- view ----------
    private var viewW = 0f
    private var viewH = 0f

    fun onViewSize(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        viewW = w
        viewH = h
        if (fitted) return
        fitted = true
        fitView()
    }

    /** Centres the whole artboard in the view. */
    fun fitView() {
        if (viewW <= 0f || viewH <= 0f) return
        val s = min(viewW / artboard.width(), viewH / artboard.height()) * 0.92f
        scale = s
        tx = (viewW - artboard.width() * s) / 2f
        ty = (viewH - artboard.height() * s) / 2f
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
            if (item.kind == Kind.TEXT) {
                val tb = item.box()
                tb.inset(-tol, -tol)
                if (tb.contains(d.x, d.y)) return item
            }
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

    // ---------- snapping ----------
    private fun targetLines(exclude: Set<Long>): Pair<List<Float>, List<Float>> {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        if (snapObjects) {
            xs.add(artboard.left); xs.add(artboard.centerX()); xs.add(artboard.right)
            ys.add(artboard.top); ys.add(artboard.centerY()); ys.add(artboard.bottom)
            for (o in items) {
                if (o.id in exclude || !o.visible) continue
                val b = o.box()
                xs.add(b.left); xs.add(b.centerX()); xs.add(b.right)
                ys.add(b.top); ys.add(b.centerY()); ys.add(b.bottom)
            }
            for (g in guides) if (g.vertical) xs.add(g.pos) else ys.add(g.pos)
        }
        return xs to ys
    }

    /** Smallest move that puts one of [values] on a target line (or grid line); returns (move, line position). */
    private fun bestSnap(values: List<Float>, targets: List<Float>, tol: Float): Pair<Float, Float>? {
        var bestD = Float.MAX_VALUE
        var bestPos = 0f
        var found = false
        for (v in values) {
            if (snapGrid) {
                val g = (v / gridSize).roundToInt() * gridSize
                val d = g - v
                if (abs(d) <= tol && abs(d) < abs(bestD)) { bestD = d; bestPos = g; found = true }
            }
            for (t in targets) {
                val d = t - v
                if (abs(d) <= tol && abs(d) < abs(bestD)) { bestD = d; bestPos = t; found = true }
            }
        }
        return if (found) bestD to bestPos else null
    }

    private fun hintsFor(ax: Pair<Float, Float>?, ay: Pair<Float, Float>?) {
        val h = ArrayList<Guide>()
        if (ax != null) h.add(Guide(true, ax.second))
        if (ay != null) h.add(Guide(false, ay.second))
        snapHints = h
    }

    private fun snapMove(box: RectF, dx: Float, dy: Float): Offset {
        snapHints = emptyList()
        if (!snapGrid && !snapObjects) return Offset.Zero
        val tol = 10f / scale
        val (xs, ys) = targetLines(selection)
        val ax = bestSnap(listOf(box.left + dx, box.centerX() + dx, box.right + dx), xs, tol)
        val ay = bestSnap(listOf(box.top + dy, box.centerY() + dy, box.bottom + dy), ys, tol)
        hintsFor(ax, ay)
        return Offset(ax?.first ?: 0f, ay?.first ?: 0f)
    }

    private fun snapPoint(p: Offset, exclude: Set<Long>): Offset {
        snapHints = emptyList()
        if (!snapGrid && !snapObjects) return p
        val tol = 10f / scale
        val (xs, ys) = targetLines(exclude)
        val ax = bestSnap(listOf(p.x), xs, tol)
        val ay = bestSnap(listOf(p.y), ys, tol)
        hintsFor(ax, ay)
        return Offset(p.x + (ax?.first ?: 0f), p.y + (ay?.first ?: 0f))
    }

    fun addGuide(vertical: Boolean) {
        guides = guides + Guide(vertical, if (vertical) artboard.centerX() else artboard.centerY())
    }

    fun clearGuides() { guides = emptyList() }

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
    private var moveStart = Offset.Zero
    private var rotCenter = Offset.Zero
    private var rotStart = 0f
    private var guideIdx = -1
    private var nodeData: MutableList<NodePath>? = null
    private var nodeTarget: Item? = null
    private var nodeDrag = 0
    private var nodeCollinear = false
    private var nodeOpp = 0.0

    private fun corner(b: RectF, i: Int) = when (i) {
        0 -> Offset(b.left, b.top)
        1 -> Offset(b.right, b.top)
        2 -> Offset(b.right, b.bottom)
        else -> Offset(b.left, b.bottom)
    }

    /** Screen position of the rotation handle that floats above the selection, or null. */
    fun rotateHandle(): Offset? {
        val b = selectionBox() ?: return null
        val top = toScreen(b.centerX(), b.top)
        return Offset(top.x, top.y - ROT_OFFSET)
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
        when (tool) {
            Tool.CORNERS, Tool.SCISSORS, Tool.TEXT, Tool.EYEDROPPER -> return Mode.PICK
            Tool.NODES -> return beginNodes(screen, handleRadius)
            Tool.PEN -> {
                penClosing = pen.size >= 3 && (toScreen(pen[0].p.x, pen[0].p.y) - screen).getDistance() <= handleRadius
                if (!penClosing) {
                    downDoc = snapPoint(downDoc, emptySet())
                    pen = pen + Anchor(downDoc, downDoc, downDoc)
                }
                return Mode.PEN
            }
            Tool.KNIFE -> {
                knife = downDoc to downDoc
                return Mode.KNIFE
            }
            Tool.SELECT -> Unit
            else -> {
                downDoc = snapPoint(downDoc, emptySet())
                return Mode.CREATE
            }
        }
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
            val rh = rotateHandle()
            if (rh != null && (rh - screen).getDistance() <= handleRadius) {
                origSel = selectedItems().map { it.copy() }
                rotCenter = Offset(box.centerX(), box.centerY())
                rotStart = atan2(downDoc.y - rotCenter.y, downDoc.x - rotCenter.x)
                return Mode.ROTATE
            }
        }
        for ((gi, g) in guides.withIndex()) {
            val dist = if (g.vertical) toScreen(g.pos, 0f).x - screen.x else toScreen(0f, g.pos).y - screen.y
            if (abs(dist) <= GUIDE_GRAB) {
                guideIdx = gi
                return Mode.GUIDE
            }
        }
        val hit = hitTest(downDoc) ?: return Mode.NONE
        downHitId = hit.id
        wasSelected = hit.id in selection
        downGroup = groupIds(hit)
        if (!wasSelected) {
            selection = if (multi) selection + downGroup else downGroup
            cornerSel = null
            nodeSel = null
        }
        touch()
        return Mode.MOVE
    }

    fun dragGesture(mode: Mode, screen: Offset) {
        val doc = toDoc(screen)
        when (mode) {
            Mode.CREATE -> dragCreate(snapPoint(doc, emptySet()))
            Mode.MOVE -> {
                if (!pushed) {
                    pushUndo()
                    pushed = true
                    origSel = selectedItems().map { it.copy() }
                    origBox = selectionBox() ?: RectF()
                    moveStart = downDoc
                }
                var dx = doc.x - moveStart.x
                var dy = doc.y - moveStart.y
                val adj = snapMove(origBox, dx, dy)
                dx += adj.x
                dy += adj.y
                val m = Matrix().apply { setTranslate(dx, dy) }
                for (o in origSel) items.firstOrNull { it.id == o.id }?.applyFrom(o, m)
                touch()
            }
            Mode.RESIZE -> dragResize(snapPoint(doc, selection))
            Mode.ROTATE -> {
                if (!pushed) { pushUndo(); pushed = true }
                var deg = Math.toDegrees((atan2(doc.y - rotCenter.y, doc.x - rotCenter.x) - rotStart).toDouble()).toFloat()
                if (snapGrid || snapObjects) {
                    val s = (deg / 15f).roundToInt() * 15f
                    if (abs(s - deg) < 4f) deg = s
                }
                val m = Matrix().apply { setRotate(deg, rotCenter.x, rotCenter.y) }
                for (o in origSel) items.firstOrNull { it.id == o.id }?.applyFrom(o, m)
                touch()
            }
            Mode.GUIDE -> {
                val g = guides.getOrNull(guideIdx)
                if (g != null) {
                    val list = guides.toMutableList()
                    list[guideIdx] = Guide(g.vertical, if (g.vertical) doc.x else doc.y)
                    guides = list
                }
            }
            Mode.NODE -> dragNode(doc)
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
            Mode.PICK -> if (!moved) {
                when (tool) {
                    Tool.NODES -> nodeTap()
                    Tool.SCISSORS -> scissorsTap()
                    Tool.TEXT -> textTap()
                    Tool.EYEDROPPER -> eyedropTap()
                    else -> pickCorner()
                }
            }
            Mode.PEN -> if (penClosing && !moved) penFinish(true)
            Mode.KNIFE -> {
                val k = knife
                knife = null
                if (k != null && moved) cut(k.first, k.second)
            }
            Mode.GUIDE -> {
                val g = guides.getOrNull(guideIdx)
                val limit = if (g != null && g.vertical) artboard.width() else artboard.height()
                if (g != null && (g.pos < -150f || g.pos > limit + 150f)) {
                    guides = guides.filterIndexed { i, _ -> i != guideIdx }
                    touch()
                }
            }
            else -> Unit
        }
        creating = null
        nodeData = null
        nodeTarget = null
        snapHints = emptyList()
    }

    // ---------- node editing and scissors ----------
    private fun near(a: P, b: P) = Nodes.same(a, b)

    private fun writeNodes(item: Item, data: List<NodePath>) {
        val ft = item.path.fillType
        item.path = PathBridge.toPath(Nodes.toContours(data)).also { it.fillType = ft }
    }

    /** Points and handles of the single selected shape, for drawing the node overlay. */
    fun nodePaths(): List<NodePath> =
        selectedItems().singleOrNull()?.let { Nodes.fromContours(PathBridge.toContours(it.path)) } ?: emptyList()

    private fun beginNodes(screen: Offset, r: Float): Mode {
        val item = selectedItems().singleOrNull() ?: return Mode.PICK
        val data = Nodes.fromContours(PathBridge.toContours(item.path))
        val sel = nodeSel
        if (sel != null && sel.first in data.indices && sel.second in data[sel.first].nodes.indices) {
            val nd = data[sel.first].nodes[sel.second]
            for (which in 2..3) {
                val h = if (which == 2) nd.hin else nd.hout
                if (!near(h, nd.p) && (toScreen(h.x.toFloat(), h.y.toFloat()) - screen).getDistance() <= r) {
                    return startNodeDrag(item, data, sel, which)
                }
            }
        }
        for ((ci, np) in data.withIndex()) {
            for ((ni, nd) in np.nodes.withIndex()) {
                if ((toScreen(nd.p.x.toFloat(), nd.p.y.toFloat()) - screen).getDistance() <= r) {
                    val picked = ci to ni
                    nodeSel = picked
                    touch()
                    return startNodeDrag(item, data, picked, 1)
                }
            }
        }
        return Mode.PICK
    }

    private fun startNodeDrag(item: Item, data: MutableList<NodePath>, sel: Pair<Int, Int>, which: Int): Mode {
        nodeData = data
        nodeTarget = item
        nodeDrag = which
        val nd = data[sel.first].nodes[sel.second]
        nodeCollinear = !near(nd.hin, nd.p) && !near(nd.hout, nd.p) &&
            abs(((nd.hin - nd.p).unit() dot (nd.hout - nd.p).unit()) + 1.0) < 0.02
        nodeOpp = if (which == 2) (nd.hout - nd.p).len() else (nd.hin - nd.p).len()
        return Mode.NODE
    }

    private fun dragNode(doc: Offset) {
        val data = nodeData ?: return
        val item = nodeTarget ?: return
        val s = nodeSel ?: return
        val nd = data.getOrNull(s.first)?.nodes?.getOrNull(s.second) ?: return
        if (!pushed) {
            pushUndo()
            pushed = true
            item.flatten()
        }
        val q = snapPoint(doc, setOf(item.id))
        val t = P(q.x.toDouble(), q.y.toDouble())
        when (nodeDrag) {
            1 -> {
                val d = t - nd.p
                nd.p = t
                nd.hin = nd.hin + d
                nd.hout = nd.hout + d
            }
            2 -> {
                nd.hin = t
                if (nodeCollinear) nd.hout = nd.p - (t - nd.p).unit() * nodeOpp
            }
            3 -> {
                nd.hout = t
                if (nodeCollinear) nd.hin = nd.p - (t - nd.p).unit() * nodeOpp
            }
        }
        writeNodes(item, data)
        touch()
    }

    /** Tap with the Nodes tool: add a point on the outline, otherwise select the shape under the finger. */
    private fun nodeTap() {
        val item = selectedItems().singleOrNull()
        if (item != null) {
            val data = Nodes.fromContours(PathBridge.toContours(item.path))
            val hit = Nodes.nearest(data, P(downDoc.x.toDouble(), downDoc.y.toDouble()))
            if (hit != null && hit.dist <= pickRadius / scale) {
                pushUndo()
                item.flatten()
                val idx = Nodes.insert(data[hit.contour], hit.seg, hit.t)
                writeNodes(item, data)
                item.clearCornerOverrides()
                nodeSel = hit.contour to idx
                touch()
                return
            }
        }
        val h = hitTest(downDoc)
        selection = if (h != null) setOf(h.id) else emptySet()
        nodeSel = null
        touch()
    }

    private fun editNodes(topology: Boolean, block: (MutableList<NodePath>, Int, Int) -> Unit) {
        val item = selectedItems().singleOrNull() ?: return
        val s = nodeSel ?: return
        val data = Nodes.fromContours(PathBridge.toContours(item.path))
        if (s.first !in data.indices || s.second !in data[s.first].nodes.indices) return
        change {
            item.flatten()
            block(data, s.first, s.second)
            writeNodes(item, data)
            if (topology) item.clearCornerOverrides()
        }
    }

    fun nodeMakeSmooth() = editNodes(false) { d, c, n -> Nodes.makeSmooth(d[c], n) }

    fun nodeMakeCorner() = editNodes(false) { d, c, n -> Nodes.makeCorner(d[c], n) }

    fun nodeRemove() = editNodes(true) { d, c, n ->
        if (Nodes.delete(d[c], n)) nodeSel = c to (n - 1).coerceAtLeast(0)
    }

    fun nodeCutHere() = editNodes(true) { d, c, n ->
        val parts = Nodes.cut(d[c], n)
        d.removeAt(c)
        d.addAll(c, parts)
        nodeSel = null
    }

    /** Tap with the Scissors tool: cut the nearest outline open at the tapped spot. */
    private fun scissorsTap() {
        val p = P(downDoc.x.toDouble(), downDoc.y.toDouble())
        var bestItem: Item? = null
        var bestData: MutableList<NodePath>? = null
        var bestHit: PathHit? = null
        for (item in items) {
            if (!item.visible || item.locked) continue
            val data = Nodes.fromContours(PathBridge.toContours(item.path))
            val h = Nodes.nearest(data, p) ?: continue
            val cur = bestHit
            if (h.dist <= pickRadius / scale && (cur == null || h.dist < cur.dist)) {
                bestItem = item
                bestData = data
                bestHit = h
            }
        }
        val item = bestItem
        val data = bestData
        val hit = bestHit
        if (item == null || data == null || hit == null) return
        change {
            item.flatten()
            val np = data[hit.contour]
            val idx = Nodes.insert(np, hit.seg, hit.t)
            val parts = Nodes.cut(np, idx)
            data.removeAt(hit.contour)
            data.addAll(hit.contour, parts)
            writeNodes(item, data)
            item.clearCornerOverrides()
            selection = setOf(item.id)
            nodeSel = null
        }
    }

    // ---------- opacity and stroke style ----------
    fun setOpacity(v: Float) = slide { selectedItems().forEach { it.opacity = v } }

    fun setDash(v: Int) { if (selection.isNotEmpty()) change { selectedItems().forEach { it.dash = v } } }

    fun setCap(v: Int) { if (selection.isNotEmpty()) change { selectedItems().forEach { it.cap = v } } }

    fun setJoin(v: Int) { if (selection.isNotEmpty()) change { selectedItems().forEach { it.join = v } } }

    // ---------- eyedropper ----------
    /** Copies the colour of the tapped shape onto the selection (or onto new shapes when nothing is selected). */
    private fun eyedropTap() {
        val hit = hitTest(downDoc) ?: return
        val c = hit.gradient?.c1 ?: hit.fill ?: hit.stroke ?: return
        if (selection.isEmpty() || hit.id in selection) {
            nextFill = c
            touch()
        } else {
            setFill(c)
        }
    }

    // ---------- text ----------
    private var textEditing = 0L

    /** The one live text object in the selection; other selected shapes act as its path, container or obstacles. */
    fun textInSelection(): Item? = selectedItems().filter { it.kind == Kind.TEXT }.singleOrNull()

    private fun textTap() {
        val hit = hitTest(downDoc)
        if (hit != null) {
            selection = if (multi) selection + hit.id else setOf(hit.id)
        } else if (!multi) {
            pushUndo()
            val item = Item(nextId++, Kind.TEXT, Path(), RectF(), nextFill, null, 0f)
            item.text = TextSpec("Text", downDoc.x, downDoc.y)
            item.rebuild()
            items = items + item
            selection = setOf(item.id)
        }
        cornerSel = null
        nodeSel = null
        touch()
    }

    /** Edits the live text; consecutive edits to the same text share one undo step. */
    private fun editText(block: (TextSpec) -> Unit) {
        val item = textInSelection() ?: return
        val spec = item.text ?: return
        if (textEditing != item.id) {
            pushUndo()
            textEditing = item.id
        }
        block(spec)
        item.rebuild()
        touch()
    }

    fun setText(v: String) = editText { it.text = v }
    fun setFont(i: Int) = editText { it.font = i }
    fun setBold(v: Boolean) = editText { it.bold = v }
    fun setItalic(v: Boolean) = editText { it.italic = v }
    fun setTextSize(v: Float) = editText { it.size = v }
    fun setWarp(w: Warp) = editText { it.warp = w }
    fun setWarpAmount(v: Float) = editText { it.amount = v }
    fun textPlain() = editText { it.mode = TextMode.PLAIN }

    private fun textOthers() = selectedItems().filter { it.kind != Kind.TEXT }

    fun textOnPath() {
        val g = textOthers().firstOrNull() ?: return
        editText { s ->
            s.mode = TextMode.ON_PATH
            s.guide = Path(g.renderPath())
            textInSelection()?.xf?.reset()
        }
    }

    /** Fills the first other selected shape with the text; with [flow], the remaining shapes are kept clear. */
    fun textInArea(flow: Boolean) {
        val others = textOthers()
        val c = others.firstOrNull() ?: return
        editText { s ->
            s.mode = TextMode.AREA
            s.area = Path(c.renderPath())
            s.obstacles = if (flow) others.drop(1).map { Path(it.renderPath()) } else emptyList()
            textInSelection()?.xf?.reset()
        }
    }

    fun textOutlines() {
        val item = textInSelection() ?: return
        change { item.flatten() }
    }

    // ---------- shape library ----------
    fun insertIcon(d: String) {
        change {
            val item = Item(nextId++, Kind.PATH, Icons.placed(d, artboard.centerX(), artboard.centerY(), min(artboard.width(), artboard.height()) * 0.3f), RectF(), nextFill, null, 0f)
            items = items + item
            selection = setOf(item.id)
            tool = Tool.SELECT
        }
    }

    // ---------- image trace ----------
    var trace by mutableStateOf<TraceSource?>(null)
        private set
    var traceThreshold by mutableFloatStateOf(128f)
        private set
    var traceInvert by mutableStateOf(false)
        private set
    var traceSmooth by mutableStateOf(true)
        private set
    private var traceItem = 0L

    fun traceLoad(src: TraceSource) {
        trace = src
        traceItem = 0L
        traceRun()
    }

    fun tuneThreshold(v: Float) {
        traceThreshold = v
        traceRun()
    }

    fun tuneSmooth(v: Boolean) {
        traceSmooth = v
        traceRun()
        endSlide()
    }

    fun tuneInvert(v: Boolean) {
        traceInvert = v
        traceRun()
        endSlide()
    }

    /** Traces the loaded picture; re-tuning updates the same shape instead of adding another. */
    private fun traceRun() {
        val src = trace ?: return
        val thr = traceThreshold.toInt()
        val inv = traceInvert
        val loops = Trace.loops(src.w, src.h, if (traceSmooth) 1.3 else 0.9) { x, y -> (src.gray[y * src.w + x] < thr) != inv }
        val k = 0.8f * min(artboard.width() / src.w, artboard.height() / src.h)
        val ox = (artboard.width() - src.w * k) / 2f
        val oy = (artboard.height() - src.h * k) / 2f
        val placed = loops.map { loop -> loop.map { P(ox + it.x * k, oy + it.y * k) } }
        val p = if (traceSmooth) {
            PathBridge.toPath(placed.map { Trace.smoothContour(it) })
        } else {
            Path().also { out ->
                for (loop in placed) {
                    loop.forEachIndexed { i, pt -> if (i == 0) out.moveTo(pt.x.toFloat(), pt.y.toFloat()) else out.lineTo(pt.x.toFloat(), pt.y.toFloat()) }
                    out.close()
                }
            }
        }
        p.fillType = Path.FillType.EVEN_ODD
        val existing = items.firstOrNull { it.id == traceItem }
        if (existing != null) {
            slide { existing.path = p }
            selection = setOf(existing.id)
        } else {
            change {
                val item = Item(nextId++, Kind.PATH, p, RectF(), DARK, null, 0f)
                traceItem = item.id
                items = items + item
                selection = setOf(item.id)
            }
        }
    }

    // ---------- gradients, rotate, flip ----------
    fun setFillType(kind: GKind?) {
        if (selection.isEmpty()) return
        change {
            selectedItems().forEach { item ->
                if (kind == null) {
                    item.gradient = null
                } else {
                    val g = item.gradient
                    if (g != null) {
                        g.kind = kind
                    } else {
                        val c1 = item.fill ?: nextFill
                        item.fill = c1
                        item.gradient = Gradient(kind, c1, Gradient.lighter(c1), 0f)
                    }
                }
            }
        }
    }

    fun setGradientColor(second: Boolean, c: Int) {
        if (selection.isEmpty()) return
        change {
            selectedItems().forEach { item ->
                val g = item.gradient
                if (g != null) {
                    if (second) {
                        g.c2 = c
                    } else {
                        g.c1 = c
                        item.fill = c
                    }
                }
            }
        }
    }

    fun setGradientAngle(v: Float) = slide { selectedItems().forEach { it.gradient?.angle = v } }

    private fun transformSelection(make: (Float, Float) -> Matrix) {
        val box = selectionBox() ?: return
        val m = make(box.centerX(), box.centerY())
        val origs = selectedItems().map { it.copy() }
        change {
            for (o in origs) items.firstOrNull { it.id == o.id }?.applyFrom(o, m)
        }
    }

    fun rotate90(clockwise: Boolean) =
        transformSelection { cx, cy -> Matrix().apply { setRotate(if (clockwise) 90f else -90f, cx, cy) } }

    fun flip(horizontal: Boolean) = transformSelection { cx, cy ->
        Matrix().apply { if (horizontal) setScale(-1f, 1f, cx, cy) else setScale(1f, -1f, cx, cy) }
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
        if (selection.isNotEmpty()) change { selectedItems().forEach { it.fill = c; it.gradient = null } }
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
        Item(nextId++, Kind.PATH, p, RectF(), src.fill, src.stroke, src.strokeWidth).also {
            it.groupId = src.groupId
            it.gradient = src.gradient?.copy()
        }

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
                t.flatten()
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
            .also { it.gradient = base.gradient?.copy() }
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
