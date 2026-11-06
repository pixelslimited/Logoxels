package ng.pixelslimited.logoxels.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path as CPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ng.pixelslimited.logoxels.engine.AlignTo
import ng.pixelslimited.logoxels.engine.Editor
import ng.pixelslimited.logoxels.engine.Export
import ng.pixelslimited.logoxels.engine.GKind
import ng.pixelslimited.logoxels.engine.Icons
import ng.pixelslimited.logoxels.engine.ImageLoad
import ng.pixelslimited.logoxels.engine.Kind
import ng.pixelslimited.logoxels.engine.Mode
import ng.pixelslimited.logoxels.engine.PfOp
import ng.pixelslimited.logoxels.engine.TextLayout
import ng.pixelslimited.logoxels.engine.TextMode
import ng.pixelslimited.logoxels.engine.geom.Warp
import ng.pixelslimited.logoxels.engine.Tool
import kotlin.math.floor
import kotlin.math.roundToInt
import android.graphics.Paint as APaint
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

private enum class FileOp { OPEN, SAVE, SVG, PNG, PDF, PSD, EPS, AI }

internal enum class Panel { NONE, STYLE, COMBINE, ARRANGE, LAYERS, GRID, ICONS, TRACE }

private val Ink = Color(0xFF111827)
private val Muted = Color(0xFF9CA3AF)
private val Accent = Color(0xFF2563EB)
private val palette = listOf(
    0xFF111827, 0xFFFFFFFF, 0xFFEF4444, 0xFFF97316, 0xFFFACC15, 0xFF22C55E,
    0xFF14B8A6, 0xFF1E88E5, 0xFF6366F1, 0xFFA855F7, 0xFFEC4899, 0xFF78716C,
).map { it.toInt() }

/** Keeps the document in app storage: restores it on start and saves shortly after every change. */
@Composable
private fun Autosave(ed: Editor) {
    val ctx = LocalContext.current
    val saveFile = remember { File(ctx.filesDir, "autosave.logoxels.json") }
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (ed.items.isEmpty() && saveFile.exists()) {
            val text = withContext(Dispatchers.IO) { runCatching { saveFile.readText() }.getOrNull() }
            if (text != null) {
                runCatching { Export.readProject(text) }.onSuccess { if (ed.items.isEmpty()) ed.loadProject(it) }
            }
        }
        restored = true
    }
    LaunchedEffect(ed.version, restored) {
        if (!restored) return@LaunchedEffect
        delay(800)
        val json = Export.projectJson(ed.items)
        withContext(Dispatchers.IO) {
            val tmp = File(saveFile.path + ".tmp")
            tmp.writeText(json)
            tmp.renameTo(saveFile)
        }
    }
}

@Composable
fun EditorScreen(ed: Editor) {
    var panel by remember { mutableStateOf(Panel.NONE) }
    val ctx = LocalContext.current
    Autosave(ed)
    fun report(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    fun write(uri: android.net.Uri?, block: (OutputStream) -> Unit) {
        if (uri == null) return
        try {
            val out = ctx.contentResolver.openOutputStream(uri) ?: error("Cannot open file")
            out.use(block)
            report("Saved")
        } catch (e: Exception) {
            report("Save failed: ${e.message}")
        }
    }
    val svgOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { u ->
        write(u) { it.write(Export.svg(ed.items).toByteArray()) }
    }
    val pngOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { u ->
        write(u) { Export.png(ed.items, it) }
    }
    val pdfOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        write(u) { Export.pdf(ed.items, it) }
    }
    val psdOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/vnd.adobe.photoshop")) { u ->
        write(u) { Export.psd(ed.items, it) }
    }
    val epsOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/postscript")) { u ->
        write(u) { it.write(Export.eps(ed.items).toByteArray()) }
    }
    // Illustrator opens PDF-compatible files, so .ai is the PDF export under Illustrator's name.
    val aiOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/illustrator")) { u ->
        write(u) { Export.pdf(ed.items, it) }
    }
    val projOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        write(u) { it.write(Export.projectJson(ed.items).toByteArray()) }
    }
    val traceIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            val src = try {
                ImageLoad.decode({ ctx.contentResolver.openInputStream(u) })
            } catch (e: Exception) {
                null
            }
            if (src == null) report("Could not read that image") else ed.traceLoad(src)
        }
    }
    val projIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            try {
                val text = ctx.contentResolver.openInputStream(u)?.use { it.readBytes().decodeToString() } ?: error("Cannot open file")
                ed.loadProject(Export.readProject(text))
            } catch (e: Exception) {
                report("Open failed: ${e.message}")
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFECEFF1)).safeDrawingPadding()) {
        TopBar(ed) { op ->
            when (op) {
                FileOp.SVG -> svgOut.launch("logo.svg")
                FileOp.PNG -> pngOut.launch("logo.png")
                FileOp.PDF -> pdfOut.launch("logo.pdf")
                FileOp.PSD -> psdOut.launch("logo.psd")
                FileOp.EPS -> epsOut.launch("logo.eps")
                FileOp.AI -> aiOut.launch("logo.ai")
                FileOp.SAVE -> projOut.launch("logo.logoxels.json")
                FileOp.OPEN -> projIn.launch(arrayOf("*/*"))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { EditorCanvas(ed) }
        when (ed.tool) {
            Tool.CORNERS -> CornersPanel(ed)
            Tool.PEN -> PenPanel(ed)
            Tool.KNIFE -> KnifePanel()
            Tool.NODES -> NodesPanel(ed)
            Tool.SCISSORS -> ScissorsPanel()
            Tool.TEXT -> TextPanel(ed)
            Tool.EYEDROPPER -> EyedropperPanel()
            else -> when (panel) {
                Panel.STYLE -> StylePanel(ed)
                Panel.COMBINE -> CombinePanel(ed)
                Panel.ARRANGE -> ArrangePanel(ed)
                Panel.LAYERS -> LayersPanel(ed)
                Panel.GRID -> GridPanel(ed)
                Panel.ICONS -> IconsPanel(ed)
                Panel.TRACE -> TracePanel(ed) { traceIn.launch(arrayOf("image/*")) }
                Panel.NONE -> Unit
            }
        }
        BottomBar(ed, panel) { panel = if (panel == it) Panel.NONE else it }
    }
}

// ---------------- canvas ----------------

@Composable
private fun EditorCanvas(ed: Editor) {
    val handleR = with(LocalDensity.current) { 24.dp.toPx() }
    val artPaint = remember { APaint().apply { color = android.graphics.Color.WHITE } }
    val gridPaint = remember {
        APaint().apply {
            color = android.graphics.Color.argb(60, 17, 24, 39)
            style = APaint.Style.STROKE
        }
    }
    val rulerBg = remember { APaint().apply { color = android.graphics.Color.WHITE } }
    val rulerInk = remember { APaint(APaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(75, 85, 99) } }
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { ed.onViewSize(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(ed) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var mode = ed.beginGesture(down.position, handleR)
                    var moved = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            mode = Mode.TRANSFORM
                            moved = true
                            ed.transformView(event.calculateZoom(), event.calculatePan(), event.calculateCentroid())
                        } else if (pressed == 1 && mode != Mode.TRANSFORM) {
                            val p = event.changes.first { it.pressed }.position
                            if (!moved && (p - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (moved) ed.dragGesture(mode, p)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })
                    ed.endGesture(mode, moved)
                }
            },
    ) {
        ed.version
        val s = ed.scale
        val tx = ed.tx
        val ty = ed.ty
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.translate(tx, ty)
            nc.scale(s, s)
            nc.drawRect(ed.artboard, artPaint)
            if (ed.showGrid) {
                gridPaint.strokeWidth = 1f / s
                val a = ed.artboard
                var gx = a.left
                while (gx <= a.right + 0.01f) { nc.drawLine(gx, a.top, gx, a.bottom, gridPaint); gx += ed.gridSize }
                var gy = a.top
                while (gy <= a.bottom + 0.01f) { nc.drawLine(a.left, gy, a.right, gy, gridPaint); gy += ed.gridSize }
            }
            Render.draw(nc, ed.items)
            nc.restore()
        }
        ed.selectionBox()?.let { b ->
            val tl = ed.toScreen(b.left, b.top)
            val br = ed.toScreen(b.right, b.bottom)
            drawRect(
                Accent, tl, Size(br.x - tl.x, br.y - tl.y),
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
            )
            for (p in listOf(tl, Offset(br.x, tl.y), br, Offset(tl.x, br.y))) {
                val o = Offset(p.x - 10f, p.y - 10f)
                drawRect(Color.White, o, Size(20f, 20f))
                drawRect(Accent, o, Size(20f, 20f), style = Stroke(width = 3f))
            }
        }
        if (ed.tool == Tool.SELECT) {
            val bx = ed.selectionBox()
            val rh = ed.rotateHandle()
            if (bx != null && rh != null) {
                val top = ed.toScreen(bx.centerX(), bx.top)
                drawLine(Accent, top, rh, 2f)
                drawCircle(Color.White, 13f, rh)
                drawCircle(Accent, 13f, rh, style = Stroke(width = 3f))
                drawCircle(Accent, 4f, rh)
            }
        }
        if (ed.tool == Tool.NODES) {
            val orange = Color(0xFFEA580C)
            val picked = ed.nodeSel
            for ((ci, np) in ed.nodePaths().withIndex()) {
                for ((ni, nd) in np.nodes.withIndex()) {
                    val c = ed.toScreen(nd.p.x.toFloat(), nd.p.y.toFloat())
                    val on = picked != null && picked.first == ci && picked.second == ni
                    if (on) {
                        for (h in listOf(nd.hin, nd.hout)) {
                            if (h.x == nd.p.x && h.y == nd.p.y) continue
                            val hs = ed.toScreen(h.x.toFloat(), h.y.toFloat())
                            drawLine(orange, c, hs, 2f)
                            drawCircle(Color.White, 9f, hs)
                            drawCircle(orange, 9f, hs, style = Stroke(width = 3f))
                        }
                    }
                    val o = Offset(c.x - 10f, c.y - 10f)
                    drawRect(if (on) orange else Color.White, o, Size(20f, 20f))
                    drawRect(if (on) orange else Accent, o, Size(20f, 20f), style = Stroke(width = 3f))
                }
            }
        }
        if (ed.tool == Tool.CORNERS) {
            val picked = ed.cornerSel
            val orange = Color(0xFFEA580C)
            for (n in ed.cornerNodes()) {
                val c = ed.toScreen(n.at.x.toFloat(), n.at.y.toFloat())
                val on = n.index == picked
                drawCircle(Color.White, 13f, c)
                drawCircle(if (on) orange else Accent, 13f, c, style = Stroke(width = 4f))
                if (on) drawCircle(orange, 7f, c)
            }
        }
        val pen = ed.pen
        if (pen.isNotEmpty()) {
            fun sp(o: Offset) = ed.toScreen(o.x, o.y)
            val route = CPath()
            val s0 = sp(pen[0].p)
            route.moveTo(s0.x, s0.y)
            for (i in 1 until pen.size) {
                val c1 = sp(pen[i - 1].hout)
                val c2 = sp(pen[i].hin)
                val e = sp(pen[i].p)
                route.cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
            }
            drawPath(route, Accent, style = Stroke(width = 3f))
            pen.forEachIndexed { i, a ->
                val c = sp(a.p)
                if (a.hout != a.p) {
                    val h1 = sp(a.hout)
                    val h2 = sp(a.hin)
                    drawLine(Accent, c, h1, 2f)
                    drawLine(Accent, c, h2, 2f)
                    drawCircle(Accent, 6f, h1)
                    drawCircle(Accent, 6f, h2)
                }
                val o = Offset(c.x - 9f, c.y - 9f)
                drawRect(if (i == 0) Color(0xFF16A34A) else Color.White, o, Size(18f, 18f))
                drawRect(Accent, o, Size(18f, 18f), style = Stroke(width = 3f))
            }
        }
        val magenta = Color(0xFFDB2777)
        for (g in ed.guides) {
            if (g.vertical) {
                val x = ed.toScreen(g.pos, 0f).x
                drawLine(magenta, Offset(x, 0f), Offset(x, size.height), 2f)
            } else {
                val y = ed.toScreen(0f, g.pos).y
                drawLine(magenta, Offset(0f, y), Offset(size.width, y), 2f)
            }
        }
        for (g in ed.snapHints) {
            val cyan = Color(0xFF06B6D4)
            if (g.vertical) {
                val x = ed.toScreen(g.pos, 0f).x
                drawLine(cyan, Offset(x, 0f), Offset(x, size.height), 2f)
            } else {
                val y = ed.toScreen(0f, g.pos).y
                drawLine(cyan, Offset(0f, y), Offset(size.width, y), 2f)
            }
        }
        if (ed.showRulers) {
            val th = 22.dp.toPx()
            rulerInk.textSize = 12.sp.toPx()
            val steps = floatArrayOf(10f, 25f, 50f, 100f, 250f, 500f, 1000f)
            val step = steps.firstOrNull { it * ed.scale >= 56f } ?: 1000f
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.drawRect(0f, 0f, size.width, th, rulerBg)
                nc.drawRect(0f, 0f, th, size.height, rulerBg)
                rulerInk.strokeWidth = 1f
                var v = floor(ed.toDoc(Offset(0f, 0f)).x / step) * step
                val endX = ed.toDoc(Offset(size.width, 0f)).x
                while (v <= endX) {
                    val sx = ed.toScreen(v, 0f).x
                    if (sx >= th) {
                        nc.drawLine(sx, th * 0.55f, sx, th, rulerInk)
                        nc.drawText(v.roundToInt().toString(), sx + 4f, th * 0.5f, rulerInk)
                    }
                    v += step
                }
                var u = floor(ed.toDoc(Offset(0f, 0f)).y / step) * step
                val endY = ed.toDoc(Offset(0f, size.height)).y
                while (u <= endY) {
                    val sy = ed.toScreen(0f, u).y
                    if (sy >= th) {
                        nc.drawLine(th * 0.55f, sy, th, sy, rulerInk)
                        nc.save()
                        nc.rotate(-90f, th * 0.5f, sy - 4f)
                        nc.drawText(u.roundToInt().toString(), th * 0.5f, sy - 4f, rulerInk)
                        nc.restore()
                    }
                    u += step
                }
            }
        }
        ed.knife?.let { (a, b) ->
            val sa = ed.toScreen(a.x, a.y)
            val sb = ed.toScreen(b.x, b.y)
            drawLine(Color(0xFFEA580C), sa, sb, 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f)))
        }
    }
}

// ---------------- bars ----------------

@Composable
private fun TopBar(ed: Editor, onFile: (FileOp) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Logoxels", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink, modifier = Modifier.weight(1f).padding(start = 4.dp))
        Box {
            TextButton({ menu = true }) { Text("File", fontSize = 12.sp) }
            DropdownMenu(menu, { menu = false }) {
                for ((label, op) in listOf(
                    "Open project" to FileOp.OPEN, "Save project" to FileOp.SAVE,
                    "Export SVG" to FileOp.SVG, "Export PNG" to FileOp.PNG, "Export PDF" to FileOp.PDF,
                    "Export PSD (layers)" to FileOp.PSD, "Export EPS" to FileOp.EPS, "Export AI" to FileOp.AI,
                )) {
                    DropdownMenuItem({ Text(label, fontSize = 14.sp) }, { menu = false; onFile(op) })
                }
            }
        }
        IconBtn(G.Undo, ed.canUndo) { ed.undo() }
        IconBtn(G.Redo, ed.canRedo) { ed.redo() }
        Spacer(Modifier.width(4.dp))
        FilterChip(ed.multi, { ed.multi = !ed.multi }, { Text("Multi", fontSize = 12.sp) })
        Spacer(Modifier.width(4.dp))
        FilterChip(ed.lockRatio, { ed.lockRatio = !ed.lockRatio }, { Text("Ratio", fontSize = 12.sp) })
    }
}

@Composable
private fun IconBtn(g: G, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Glyph(g, if (enabled) Ink else Muted) }
}

@Composable
private fun BottomBar(ed: Editor, panel: Panel, onPanel: (Panel) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolBtn(G.Select, "Select", ed.tool == Tool.SELECT) { ed.tool = Tool.SELECT }
        ToolBtn(G.Rect, "Rect", ed.tool == Tool.RECT) { ed.tool = Tool.RECT }
        ToolBtn(G.Oval, "Oval", ed.tool == Tool.ELLIPSE) { ed.tool = Tool.ELLIPSE }
        ToolBtn(G.Polygon, "Polygon", ed.tool == Tool.POLYGON) { ed.tool = Tool.POLYGON }
        ToolBtn(G.Star, "Star", ed.tool == Tool.STAR) { ed.tool = Tool.STAR }
        ToolBtn(G.Line, "Line", ed.tool == Tool.LINE) { ed.tool = Tool.LINE }
        ToolBtn(G.Pen, "Pen", ed.tool == Tool.PEN) { ed.tool = Tool.PEN }
        ToolBtn(G.Text, "Text", ed.tool == Tool.TEXT) { ed.tool = Tool.TEXT }
        ToolBtn(G.Eyedropper, "Eyedrop", ed.tool == Tool.EYEDROPPER) { ed.tool = Tool.EYEDROPPER }
        ToolBtn(G.Knife, "Knife", ed.tool == Tool.KNIFE) { ed.tool = Tool.KNIFE }
        ToolBtn(G.Nodes, "Nodes", ed.tool == Tool.NODES) { ed.tool = Tool.NODES }
        ToolBtn(G.Scissors, "Scissors", ed.tool == Tool.SCISSORS) { ed.tool = Tool.SCISSORS }
        ToolBtn(G.Corner, "Corners", ed.tool == Tool.CORNERS) { ed.tool = Tool.CORNERS }
        VerticalDivider(Modifier.height(36.dp).padding(horizontal = 4.dp))
        ToolBtn(G.Style, "Style", panel == Panel.STYLE) { onPanel(Panel.STYLE) }
        ToolBtn(G.Union, "Combine", panel == Panel.COMBINE) { onPanel(Panel.COMBINE) }
        ToolBtn(G.Arrange, "Arrange", panel == Panel.ARRANGE) { onPanel(Panel.ARRANGE) }
        ToolBtn(G.Layers, "Layers", panel == Panel.LAYERS) { onPanel(Panel.LAYERS) }
        ToolBtn(G.Grid, "Grid", panel == Panel.GRID) { onPanel(Panel.GRID) }
        ToolBtn(G.Library, "Shapes", panel == Panel.ICONS) { onPanel(Panel.ICONS) }
        ToolBtn(G.Trace, "Trace", panel == Panel.TRACE) { onPanel(Panel.TRACE) }
    }
}

@Composable
private fun ToolBtn(g: G, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFCCFBF1) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(g, Ink)
        Text(label, fontSize = 12.sp, color = Ink)
    }
}

// ---------------- panels ----------------

@Composable
private fun PanelBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        content = content,
    )
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
}

@Composable
private fun Swatch(color: Int?, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) Accent else Color(0xFFD1D5DB)
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (color == null) Color.White else Color(color))
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .clickable(onClick = onClick),
    ) {
        if (color == null) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(Color(0xFFEF4444), Offset(size.width * .2f, size.height * .8f), Offset(size.width * .8f, size.height * .2f), strokeWidth = 3f)
            }
        }
    }
}

@Composable
private fun ColorRow(current: Int?, allowNone: Boolean, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowNone) Swatch(null, current == null) { onPick(null) }
        if (current != null && current !in palette) Swatch(current, true) { open = true }
        palette.forEach { c -> Swatch(c, current == c) { onPick(c) } }
        Box(
            Modifier.size(36.dp).clip(CircleShape).border(1.dp, Color(0xFFD1D5DB), CircleShape).clickable { open = true },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(Ink, Offset(size.width * .3f, size.height / 2f), Offset(size.width * .7f, size.height / 2f), strokeWidth = 3f)
                drawLine(Ink, Offset(size.width / 2f, size.height * .3f), Offset(size.width / 2f, size.height * .7f), strokeWidth = 3f)
            }
        }
    }
    if (open) ColorDialog(current ?: palette[0], { onPick(it); open = false }, { open = false })
}

@Composable
private fun ColorDialog(initial: Int, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(start[0]) }
    var s by remember { mutableFloatStateOf(start[1]) }
    var v by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    fun syncHex() { hex = "%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)) and 0xFFFFFF) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onDone(color) }) { Text("Use", fontSize = 14.sp) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", fontSize = 14.sp) } },
        title = { Text("Colour", fontSize = 16.sp) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(color)))
                Label("Hue")
                Slider(h, { h = it; syncHex() }, valueRange = 0f..360f)
                Label("Saturation")
                Slider(s, { s = it; syncHex() }, valueRange = 0f..1f)
                Label("Brightness")
                Slider(v, { v = it; syncHex() }, valueRange = 0f..1f)
                OutlinedTextField(
                    hex,
                    { t ->
                        hex = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        if (hex.length == 6) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(0xFF000000.toInt() or hex.toInt(16), hsv)
                            h = hsv[0]; s = hsv[1]; v = hsv[2]
                        }
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text("Hex", fontSize = 12.sp) },
                    singleLine = true,
                )
            }
        },
    )
}

@Composable
private fun EyedropperPanel() {
    PanelBox {
        Label("Select shapes first, then tap the shape whose colour you want to copy. With nothing selected it sets the colour for new shapes.")
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onEnd: () -> Unit, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = Ink, modifier = Modifier.width(104.dp))
        Slider(value.coerceIn(range), onChange, Modifier.weight(1f), valueRange = range, steps = steps, onValueChangeFinished = onEnd)
        Text(value.roundToInt().toString(), fontSize = 12.sp, color = Ink, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun StylePanel(ed: Editor) {
    ed.version
    val sel = ed.selectedItems()
    val first = sel.firstOrNull()
    PanelBox {
        if (first == null) {
            Label("Fill for new shapes")
            ColorRow(ed.nextFill, false) { ed.setFill(it) }
            Label("Select a shape to change its fill, stroke and corners.")
            return@PanelBox
        }
        Label("Fill")
        val grad = first.gradient
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(grad == null, { ed.setFillType(null) }, { Text("Solid", fontSize = 12.sp) })
            FilterChip(grad?.kind == GKind.LINEAR, { ed.setFillType(GKind.LINEAR) }, { Text("Linear", fontSize = 12.sp) })
            FilterChip(grad?.kind == GKind.RADIAL, { ed.setFillType(GKind.RADIAL) }, { Text("Radial", fontSize = 12.sp) })
        }
        if (grad == null) {
            ColorRow(first.fill, true) { ed.setFill(it) }
        } else {
            Label("Start colour")
            ColorRow(grad.c1, false) { c -> if (c != null) ed.setGradientColor(false, c) }
            Label("End colour")
            ColorRow(grad.c2, false) { c -> if (c != null) ed.setGradientColor(true, c) }
            if (grad.kind == GKind.LINEAR) SliderRow("Angle", grad.angle, 0f..360f, 0, ed::endSlide) { ed.setGradientAngle(it) }
        }
        Label("Stroke")
        ColorRow(first.stroke, true) { ed.setStroke(it) }
        SliderRow("Stroke width", first.strokeWidth, 0f..60f, 0, ed::endSlide) { ed.setStrokeWidth(it) }
        SliderRow("Round all corners", first.corner, 0f..300f, 0, ed::endSlide) { ed.setCorner(it) }
        if (sel.size == 1 && (first.kind == Kind.POLYGON || first.kind == Kind.STAR)) {
            SliderRow("Sides", first.sides.toFloat(), 3f..12f, 8, ed::endSlide) { ed.setSides(it.roundToInt()) }
        }
        if (sel.size == 1 && first.kind == Kind.STAR) {
            SliderRow("Inner size %", first.inner * 100f, 20f..90f, 0, ed::endSlide) { ed.setInner(it / 100f) }
        }
    }
}

@Composable
private fun PenPanel(ed: Editor) {
    ed.version
    val n = ed.pen.size
    PanelBox {
        Label("Tap to add corner points. Drag to pull out a curve. Tap the green first point to close.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("Finish open", n >= 2) { ed.penFinish(false) }
            Act("Close shape", n >= 3) { ed.penFinish(true) }
            Act("Undo point", n >= 1) { ed.penUndoPoint() }
            Act("Cancel", n >= 1) { ed.penCancel() }
        }
    }
}

@Composable
private fun KnifePanel() {
    PanelBox {
        Label("Drag a line across shapes to cut them along it. With a selection, only the selected shapes are cut.")
    }
}

@Composable
private fun TextPanel(ed: Editor) {
    ed.version
    val item = ed.textInSelection()
    val spec = item?.text
    PanelBox {
        if (item == null || spec == null) {
            Label("Tap the canvas to add text, or tap existing text to edit it. To put text on a path, in a shape or around objects, turn on Multi and select the text and the shape.")
            return@PanelBox
        }
        var tv by remember(item.id) { mutableStateOf(spec.text) }
        OutlinedTextField(tv, { tv = it; ed.setText(it) }, Modifier.fillMaxWidth(), label = { Text("Text", fontSize = 12.sp) })
        Label("Font")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextLayout.FONTS.forEachIndexed { i, f ->
                FilterChip(spec.font == i, { ed.setFont(i) }, { Text(f.second, fontSize = 12.sp) })
            }
            FilterChip(spec.bold, { ed.setBold(!spec.bold) }, { Text("Bold", fontSize = 12.sp) })
            FilterChip(spec.italic, { ed.setItalic(!spec.italic) }, { Text("Italic", fontSize = 12.sp) })
        }
        SliderRow("Size", spec.size, 20f..400f, 0, ed::endSlide) { ed.setTextSize(it) }
        Label("Layout")
        val others = ed.selectedItems().count { it.kind != Kind.TEXT }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("Plain", true) { ed.textPlain() }
            Act("On path", others >= 1) { ed.textOnPath() }
            Act("In shape", others >= 1) { ed.textInArea(false) }
            Act("Flow around", others >= 2) { ed.textInArea(true) }
            Act("To outlines", true) { ed.textOutlines() }
        }
        if (spec.mode == TextMode.PLAIN) {
            Label("Warp")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (w in Warp.values()) {
                    FilterChip(spec.warp == w, { ed.setWarp(w) }, { Text(w.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) })
                }
            }
            if (spec.warp != Warp.NONE) SliderRow("Bend", spec.amount * 100f, -100f..100f, 0, ed::endSlide) { ed.setWarpAmount(it / 100f) }
        } else {
            Label("In shape and Flow around fill the first selected shape. Plain brings the text back to a free line.")
        }
    }
}

@Composable
private fun IconsPanel(ed: Editor) {
    val icons = remember { Icons.all.map { (name, d) -> Triple(name, d, Icons.path(d).asComposePath()) } }
    PanelBox {
        Label("Tap a shape to add it. Colour, round, cut and combine it like any other shape.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, d, path) in icons) {
                Column(
                    Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable { ed.insertIcon(d) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.size(40.dp)) { scale(size.width / 100f, Offset.Zero) { drawPath(path, Ink) } }
                    Text(name, fontSize = 12.sp, color = Ink)
                }
            }
        }
    }
}

@Composable
private fun TracePanel(ed: Editor, onPick: () -> Unit) {
    ed.version
    PanelBox {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("Choose image", true, onPick)
            FilterChip(ed.traceInvert, { ed.tuneInvert(!ed.traceInvert) }, { Text("Invert", fontSize = 12.sp) })
        }
        if (ed.trace == null) {
            Label("Pick a logo, sketch or photo. Its dark areas become an editable vector shape.")
        } else {
            SliderRow("Threshold", ed.traceThreshold, 1f..254f, 0, ed::endSlide) { ed.tuneThreshold(it) }
        }
    }
}

@Composable
private fun ScissorsPanel() {
    PanelBox {
        Label("Tap an outline to cut it open at that spot. Closed shapes become open paths.")
    }
}

@Composable
private fun NodesPanel(ed: Editor) {
    ed.version
    val has = ed.nodeSel != null
    PanelBox {
        if (ed.selectedItems().size != 1) {
            Label("Tap a shape to edit its points.")
            return@PanelBox
        }
        Label("Drag a point or its handles. Tap the outline to add a point. Editing turns the shape into a free path.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("Smooth", has) { ed.nodeMakeSmooth() }
            Act("Corner", has) { ed.nodeMakeCorner() }
            Act("Delete point", has) { ed.nodeRemove() }
            Act("Cut here", has) { ed.nodeCutHere() }
        }
    }
}

@Composable
private fun GridPanel(ed: Editor) {
    PanelBox {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(ed.showGrid, { ed.showGrid = !ed.showGrid }, { Text("Show grid", fontSize = 12.sp) })
            FilterChip(ed.snapGrid, { ed.snapGrid = !ed.snapGrid }, { Text("Snap to grid", fontSize = 12.sp) })
            FilterChip(ed.snapObjects, { ed.snapObjects = !ed.snapObjects }, { Text("Snap to objects", fontSize = 12.sp) })
            FilterChip(ed.showRulers, { ed.showRulers = !ed.showRulers }, { Text("Rulers", fontSize = 12.sp) })
        }
        SliderRow("Grid size", ed.gridSize, 10f..100f, 8, {}) { ed.gridSize = it }
        Label("Guides: drag one off the canvas to remove it.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("Vertical guide", true) { ed.addGuide(true) }
            Act("Horizontal guide", true) { ed.addGuide(false) }
            Act("Clear guides", ed.guides.isNotEmpty()) { ed.clearGuides() }
        }
    }
}

@Composable
private fun SmallBtn(label: String, onClick: () -> Unit) {
    TextButton(onClick, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(label, fontSize = 12.sp) }
}

@Composable
private fun LayersPanel(ed: Editor) {
    ed.version
    PanelBox {
        if (ed.items.isEmpty()) {
            Label("No shapes yet.")
            return@PanelBox
        }
        for (item in ed.items.asReversed()) {
            val on = item.id in ed.selection
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Color(0xFFCCFBF1) else Color.Transparent)
                    .clickable { ed.selectFromLayers(item) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(item.fill ?: item.stroke ?: 0xFF9CA3AF.toInt()))
                        .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(4.dp)),
                )
                val tag = if (item.groupId != 0L) " - group ${item.groupId}" else ""
                Text(
                    item.label() + tag,
                    fontSize = 12.sp,
                    color = if (item.visible) Ink else Muted,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                SmallBtn("Up") { ed.moveLayer(item, true) }
                SmallBtn("Down") { ed.moveLayer(item, false) }
                SmallBtn(if (item.visible) "Hide" else "Show") { ed.toggleVisible(item) }
                SmallBtn(if (item.locked) "Unlock" else "Lock") { ed.toggleLock(item) }
            }
        }
    }
}

@Composable
private fun CornersPanel(ed: Editor) {
    ed.version
    val item = ed.selectedItems().singleOrNull()
    PanelBox {
        if (item == null) {
            Label("Tap a shape to select it. Its corners appear as dots.")
            return@PanelBox
        }
        if (item.kind == Kind.ELLIPSE) {
            Label("An oval has no corners. Merge it with other shapes using Combine, then round the new corners here.")
            return@PanelBox
        }
        SliderRow("All corners", item.corner, 0f..300f, 0, ed::endSlide) { ed.setCorner(it) }
        val sel = ed.cornerSel
        if (sel == null) {
            Label("Tap a corner dot on the canvas to round only that corner.")
        } else {
            SliderRow("Corner ${sel + 1}", item.radiusAt(sel), 0f..300f, 0, ed::endSlide) { ed.setCornerSelected(it) }
        }
        Act("Reset corners", true) { ed.resetCorners() }
    }
}

@Composable
private fun CombineBtn(g: G, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Ink else Muted
    Column(
        Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(g, tint)
        Text(label, fontSize = 12.sp, color = tint)
    }
}

@Composable
private fun CombinePanel(ed: Editor) {
    ed.version
    val ok = ed.selection.size >= 2
    PanelBox {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            CombineBtn(G.Union, "Unite", ok) { ed.combine(PfOp.UNION) }
            CombineBtn(G.Minus, "Minus front", ok) { ed.combine(PfOp.MINUS_FRONT) }
            CombineBtn(G.Intersect, "Intersect", ok) { ed.combine(PfOp.INTERSECT) }
            CombineBtn(G.Exclude, "Exclude", ok) { ed.combine(PfOp.EXCLUDE) }
        }
        Label(if (ok) "Result keeps the bottom shape's colours." else "Turn on Multi, then tap two or more shapes.")
    }
}

@Composable
private fun Act(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun ArrangePanel(ed: Editor) {
    ed.version
    val n = ed.selection.size
    PanelBox {
        Label(if (n == 1) "Align to artboard" else "Align to selection")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("Left", n > 0) { ed.align(AlignTo.LEFT) }
            Act("Center", n > 0) { ed.align(AlignTo.CENTER) }
            Act("Right", n > 0) { ed.align(AlignTo.RIGHT) }
            Act("Top", n > 0) { ed.align(AlignTo.TOP) }
            Act("Middle", n > 0) { ed.align(AlignTo.MIDDLE) }
            Act("Bottom", n > 0) { ed.align(AlignTo.BOTTOM) }
        }
        Label("Distribute (3 or more)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("Horizontally", n >= 3) { ed.distribute(true) }
            Act("Vertically", n >= 3) { ed.distribute(false) }
        }
        Label("Group and clip")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("Group", n >= 2) { ed.group() }
            Act("Ungroup", ed.selectedItems().any { it.groupId != 0L }) { ed.ungroup() }
            Act("Clip to top shape", n >= 2) { ed.clipToTop() }
        }
        Label("Rotate and flip")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("Rotate left", n > 0) { ed.rotate90(false) }
            Act("Rotate right", n > 0) { ed.rotate90(true) }
            Act("Flip horizontal", n > 0) { ed.flip(true) }
            Act("Flip vertical", n > 0) { ed.flip(false) }
        }
        Label("Order and edit")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("To front", n > 0) { ed.reorder(front = true, toEnd = true) }
            Act("Forward", n > 0) { ed.reorder(front = true, toEnd = false) }
            Act("Backward", n > 0) { ed.reorder(front = false, toEnd = false) }
            Act("To back", n > 0) { ed.reorder(front = false, toEnd = true) }
            Act("Duplicate", n > 0) { ed.duplicateSelected() }
            Act("Select all", true) { ed.selectAll() }
            Act("Delete", n > 0) { ed.deleteSelected() }
        }
    }
}
