package ng.pixelslimited.logoxels.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
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
import ng.pixelslimited.logoxels.engine.Picture
import ng.pixelslimited.logoxels.engine.SvgDoc
import ng.pixelslimited.logoxels.engine.SvgImport
import ng.pixelslimited.logoxels.engine.Render
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

private enum class FileOp { IMPORT, OPEN, SAVE, SVG, PNG, PDF, PSD, EPS, AI }

private sealed class Loaded {
    class Pic(val pic: Picture) : Loaded()
    class Svg(val doc: SvgDoc) : Loaded()
}

/** Reads a picked or dropped file as an SVG or a picture; null when it is neither. Runs off the main thread. */
private fun readImport(ctx: android.content.Context, u: android.net.Uri): Loaded? {
    val raw = runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() ?: return null
    if (raw.size > 40_000_000) return null
    val head = raw.copyOf(minOf(raw.size, 2048)).decodeToString()
    if (head.contains("<svg", ignoreCase = true)) return SvgImport.parse(raw.decodeToString())?.let { Loaded.Svg(it) }
    return Picture.decode(raw)?.let { Loaded.Pic(it) }
}

private tailrec fun android.content.Context.hostActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.hostActivity()
    else -> null
}

internal enum class Panel { NONE, STYLE, COMBINE, ARRANGE, LAYERS, GRID, ICONS, TRACE, ARTBOARD }

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
        val json = Export.projectJson(ed.projectDoc())
        withContext(Dispatchers.IO) {
            val tmp = File(saveFile.path + ".tmp")
            tmp.writeText(json)
            tmp.renameTo(saveFile)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun EditorScreen(ed: Editor) {
    var panel by remember { mutableStateOf(Panel.NONE) }
    val ctx = LocalContext.current
    Autosave(ed)
    LaunchedEffect(Pal.dark) { ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().putBoolean("dark", Pal.dark).apply() }
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
    var pngDialog by remember { mutableStateOf(false) }
    var pngSize by remember { mutableStateOf(2048) }
    var pngClear by remember { mutableStateOf(false) }
    var pngSel by remember { mutableStateOf(false) }
    /** File name stem for exports: the active artboard's name, reduced to safe characters. */
    fun fileBase() = ed.boards.getOrNull(ed.activeBoard)?.name.orEmpty()
        .replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifEmpty { "logo" }
    val svgOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { u ->
        write(u) { it.write(Export.svg(ed.doc()).toByteArray()) }
    }
    val pngOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { u ->
        write(u) { Export.png(ed.exportDoc(pngSel, pngClear), it, pngSize) }
    }
    val pdfOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        write(u) { Export.pdf(ed.doc(), it) }
    }
    val psdOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/vnd.adobe.photoshop")) { u ->
        write(u) { Export.psd(ed.doc(), it) }
    }
    val epsOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/postscript")) { u ->
        write(u) { it.write(Export.eps(ed.doc()).toByteArray()) }
    }
    // Illustrator opens PDF-compatible files, so .ai is the PDF export under Illustrator's name.
    val aiOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/illustrator")) { u ->
        write(u) { Export.pdf(ed.doc(), it) }
    }
    val projOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        write(u) { it.write(Export.projectJson(ed.projectDoc()).toByteArray()) }
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
    val scope = rememberCoroutineScope()
    fun importFiles(uris: List<android.net.Uri>, at: Offset?) {
        scope.launch {
            var n = 0
            var bad = 0
            var skipped = 0
            for (u in uris) {
                when (val l = withContext(Dispatchers.IO) { readImport(ctx, u) }) {
                    is Loaded.Pic -> ed.importPicture(l.pic, at, n++)
                    is Loaded.Svg -> { ed.importSvg(l.doc, at, n++); skipped += l.doc.skipped }
                    null -> bad++
                }
            }
            val notes = listOfNotNull(
                if (bad > 0) "$bad file(s) could not be read" else null,
                if (skipped > 0) "SVG text, images and gradients are not imported" else null,
            )
            if (notes.isNotEmpty()) report(notes.joinToString(". "))
        }
    }
    val importIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        if (list.isNotEmpty()) importFiles(list, null)
    }
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    val dropHold = remember { arrayOfNulls<Any>(1) }
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val de = event.toAndroidDragEvent()
                val clip = de.clipData ?: return false
                val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                if (uris.isEmpty()) return false
                // keep the read permission for as long as this screen lives; the files are read after this call returns
                dropHold[0] = ctx.hostActivity()?.requestDragAndDropPermissions(de)
                importFiles(uris, Offset(de.x - canvasOrigin.x, de.y - canvasOrigin.y))
                return true
            }
        }
    }
    var updating by remember { mutableStateOf(false) }
    if (updating) UpdateDialog { updating = false }
    if (pngDialog) {
        AlertDialog(
            onDismissRequest = { pngDialog = false },
            title = { Text("Export PNG", fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("Size (long side, px)")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (v in listOf(512, 1024, 2048, 4096)) {
                            FilterChip(pngSize == v, { pngSize = v }, { Text(v.toString(), fontSize = 12.sp) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(pngClear, { pngClear = !pngClear }, { Text("Transparent", fontSize = 12.sp) })
                        FilterChip(pngSel, { pngSel = !pngSel }, { Text("Selection only", fontSize = 12.sp) }, enabled = ed.selection.isNotEmpty())
                    }
                }
            },
            confirmButton = {
                TextButton({
                    pngDialog = false
                    if (ed.selection.isEmpty()) pngSel = false
                    pngOut.launch(fileBase() + ".png")
                }) { Text("Export", fontSize = 12.sp) }
            },
            dismissButton = { TextButton({ pngDialog = false }) { Text("Cancel", fontSize = 12.sp) } },
        )
    }
    Column(Modifier.fillMaxSize().background(Back).safeDrawingPadding()) {
        TopBar(ed, { updating = true }) { op ->
            when (op) {
                FileOp.SVG -> svgOut.launch(fileBase() + ".svg")
                FileOp.PNG -> pngDialog = true
                FileOp.PDF -> pdfOut.launch(fileBase() + ".pdf")
                FileOp.PSD -> psdOut.launch(fileBase() + ".psd")
                FileOp.EPS -> epsOut.launch(fileBase() + ".eps")
                FileOp.AI -> aiOut.launch(fileBase() + ".ai")
                FileOp.SAVE -> projOut.launch(fileBase() + ".logoxels.json")
                FileOp.IMPORT -> importIn.launch(arrayOf("*/*"))
                FileOp.OPEN -> projIn.launch(arrayOf("*/*"))
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { canvasOrigin = it.positionInRoot() }
                .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
        ) {
            EditorCanvas(ed)
            QuickBar(ed, Modifier.align(Alignment.BottomCenter).padding(end = 76.dp))
            BoardBar(ed, Modifier.align(Alignment.TopCenter))
            ToolDial(ed, panel) { panel = if (panel == it) Panel.NONE else it }
        }
        when (ed.tool) {
            Tool.CORNERS -> CornersPanel(ed)
            Tool.PEN -> PenPanel(ed)
            Tool.KNIFE -> KnifePanel()
            Tool.NODES -> NodesPanel(ed)
            Tool.SCISSORS -> ScissorsPanel()
            Tool.TEXT -> TextPanel(ed)
            Tool.EYEDROPPER -> EyedropperPanel()
            Tool.SKETCH -> SketchPanel(ed)
            else -> when (panel) {
                Panel.STYLE -> StylePanel(ed)
                Panel.COMBINE -> CombinePanel(ed)
                Panel.ARRANGE -> ArrangePanel(ed)
                Panel.LAYERS -> LayersPanel(ed)
                Panel.GRID -> GridPanel(ed)
                Panel.ICONS -> IconsPanel(ed)
                Panel.ARTBOARD -> ArtboardPanel(ed)
                Panel.TRACE -> TracePanel(ed) { traceIn.launch(arrayOf("image/*")) }
                Panel.NONE -> Unit
            }
        }
    }
}

// ---------------- canvas ----------------

@Composable
private fun EditorCanvas(ed: Editor) {
    val handleR = with(LocalDensity.current) { 24.dp.toPx() }
    val artPaint = remember { APaint().apply { color = android.graphics.Color.WHITE } }
    val namePaint = remember { APaint().apply { isAntiAlias = true; isFakeBoldText = true } }
    val frame = remember { APaint().apply { style = APaint.Style.STROKE } }
    val gridPaint = remember {
        APaint().apply {
            color = android.graphics.Color.argb(60, 17, 24, 39)
            style = APaint.Style.STROKE
        }
    }
    val rulerBg = remember { APaint().apply { color = android.graphics.Color.WHITE } }
    val rulerInk = remember { APaint(APaint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(75, 85, 99) } }
    LaunchedEffect(ed) {
        snapshotFlow { ed.motionTick }.collectLatest {
            var last = 0L
            while (true) {
                var running = true
                withFrameNanos { t ->
                    val dt = if (last == 0L) 0.016f else ((t - last) / 1e9f).coerceAtMost(0.05f)
                    last = t
                    running = ed.stepMotion(dt)
                }
                if (!running) break
            }
        }
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { ed.onViewSize(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(ed) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var mode = ed.beginGesture(down.position, handleR)
                    var moved = false
                    val velocity = VelocityTracker()
                    if (mode == Mode.NONE && ed.tool == Tool.SELECT) {
                        var held = true
                        val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (held) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull()
                                if (c == null || !c.pressed || e.changes.size > 1 ||
                                    (c.position - down.position).getDistance() > viewConfiguration.touchSlop
                                ) held = false
                            }
                        } == null
                        if (timedOut) {
                            ed.longPressBoard(down.position)
                            do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        if (!held && !currentEvent.changes.any { it.pressed }) {
                            ed.endGesture(mode, false)
                            return@awaitEachGesture
                        }
                    }
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            mode = Mode.TRANSFORM
                            moved = true
                            val centroid = event.calculateCentroid()
                            ed.transformView(event.calculateZoom(), event.calculatePan(), centroid)
                            velocity.addPosition(event.changes.first().uptimeMillis, centroid)
                        } else if (pressed == 1 && mode != Mode.TRANSFORM) {
                            val p = event.changes.first { it.pressed }.position
                            if (!moved && (p - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (moved) ed.dragGesture(mode, p)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })
                    ed.endGesture(mode, moved)
                    if (mode == Mode.TRANSFORM) {
                        val v = velocity.calculateVelocity()
                        ed.fling(v.x, v.y)
                    }
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
            for (b in ed.boards) {
                artPaint.color = b.bg ?: android.graphics.Color.WHITE
                nc.drawRect(b.rect, artPaint)
            }
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
            namePaint.textSize = 12.sp.toPx()
            for ((i, b) in ed.boards.withIndex()) {
                val tl = ed.toScreen(b.rect.left, b.rect.top)
                val br = ed.toScreen(b.rect.right, b.rect.bottom)
                val on = i == ed.activeBoard
                frame.color = if (on) android.graphics.Color.rgb(37, 99, 235) else android.graphics.Color.argb(120, 128, 128, 128)
                frame.strokeWidth = if (on) 3f else 1.5f
                nc.drawRect(tl.x, tl.y, br.x, br.y, frame)
                namePaint.color = if (Pal.dark) android.graphics.Color.rgb(209, 213, 219) else android.graphics.Color.rgb(75, 85, 99)
                nc.drawText(b.name, tl.x, tl.y - 8f, namePaint)
            }
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
                ed.skewHandles()?.let { (a, b) ->
                    for (p in listOf(a, b)) {
                        val d = CPath().apply { moveTo(p.x, p.y - 13f); lineTo(p.x + 13f, p.y); lineTo(p.x, p.y + 13f); lineTo(p.x - 13f, p.y); close() }
                        drawPath(d, Color.White)
                        drawPath(d, Accent, style = Stroke(width = 3f))
                    }
                }
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
        val sketch = ed.sketchPoints()
        if (sketch.size > 1) {
            val route = CPath()
            val s0 = ed.toScreen(sketch[0].x, sketch[0].y)
            route.moveTo(s0.x, s0.y)
            for (i in 1 until sketch.size) {
                val q = ed.toScreen(sketch[i].x, sketch[i].y)
                route.lineTo(q.x, q.y)
            }
            drawPath(route, Accent, style = Stroke(width = 5f))
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
private fun TopBar(ed: Editor, onUpdate: () -> Unit, onFile: (FileOp) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Bar).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Logoxels", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink, modifier = Modifier.weight(1f).padding(start = 4.dp))
        Box {
            TextButton({ menu = true }) { Text("File", fontSize = 12.sp) }
            DropdownMenu(menu, { menu = false }) {
                for ((label, op) in listOf(
                    "Import to board" to FileOp.IMPORT, "Open project" to FileOp.OPEN, "Save project" to FileOp.SAVE,
                    "Export SVG" to FileOp.SVG, "Export PNG" to FileOp.PNG, "Export PDF" to FileOp.PDF,
                    "Export PSD (layers)" to FileOp.PSD, "Export EPS" to FileOp.EPS, "Export AI" to FileOp.AI,
                )) {
                    DropdownMenuItem({ Text(label, fontSize = 14.sp) }, { menu = false; onFile(op) })
                }
                DropdownMenuItem({ Text("Check for updates", fontSize = 14.sp) }, { menu = false; onUpdate() })
            }
        }
        IconBtn(G.Theme, true) { Pal.dark = !Pal.dark }
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

private class ToolEntry(val g: G, val label: String, val tool: Tool)

/** One dial button: a single tool, or a named group whose tools open as extra rows. */
private class ToolGroupDef(val name: String, val entries: List<ToolEntry>)

private class PanelDef(val g: G, val label: String, val panel: Panel)

private fun oneTool(g: G, label: String, tool: Tool) = ToolGroupDef(label, listOf(ToolEntry(g, label, tool)))

private val dialTools = listOf(
    oneTool(G.Select, "Select", Tool.SELECT),
    ToolGroupDef(
        "Shapes",
        listOf(
            ToolEntry(G.Rect, "Rectangle", Tool.RECT), ToolEntry(G.Oval, "Oval", Tool.ELLIPSE),
            ToolEntry(G.Polygon, "Polygon", Tool.POLYGON), ToolEntry(G.Star, "Star", Tool.STAR),
            ToolEntry(G.Line, "Line", Tool.LINE),
        ),
    ),
    ToolGroupDef("Draw", listOf(ToolEntry(G.Pen, "Pen", Tool.PEN), ToolEntry(G.Sketch, "Sketch", Tool.SKETCH))),
    oneTool(G.Text, "Text", Tool.TEXT),
    ToolGroupDef(
        "Edit",
        listOf(
            ToolEntry(G.Nodes, "Nodes", Tool.NODES), ToolEntry(G.Corner, "Corners", Tool.CORNERS),
            ToolEntry(G.Scissors, "Scissors", Tool.SCISSORS), ToolEntry(G.Knife, "Knife", Tool.KNIFE),
        ),
    ),
    oneTool(G.Eyedropper, "Eyedrop", Tool.EYEDROPPER),
)

private val dialPanels = listOf(
    PanelDef(G.Style, "Style", Panel.STYLE), PanelDef(G.Union, "Combine", Panel.COMBINE),
    PanelDef(G.Arrange, "Arrange", Panel.ARRANGE), PanelDef(G.Layers, "Layers", Panel.LAYERS),
    PanelDef(G.Grid, "Grid", Panel.GRID), PanelDef(G.Library, "Library", Panel.ICONS),
    PanelDef(G.Trace, "Trace", Panel.TRACE), PanelDef(G.Artboard, "Canvas", Panel.ARTBOARD),
)

private class DialRow(val g: G, val label: String, val active: Boolean, val small: Boolean = false, val onClick: () -> Unit)

private val OnAccent: Color get() = if (Pal.dark) Color(0xFF0B1220) else Color.White

/**
 * Floating tool menu. The round button can be dragged anywhere on the board; tapping it opens the tools and
 * panels as a column of round buttons. Picking one closes the menu; tapping anywhere else closes it and
 * returns to the Select tool.
 */
@Composable
private fun ToolDial(ed: Editor, panel: Panel, onPanel: (Panel) -> Unit) {
    val fabPx = with(LocalDensity.current) { 56.dp.toPx() }
    val marginPx = with(LocalDensity.current) { 16.dp.toPx() }
    val gapPx = with(LocalDensity.current) { 10.dp.toPx() }.roundToInt()
    var open by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf<String?>(null) }
    var pos by remember { mutableStateOf<Offset?>(null) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    fun dismiss() {
        open = false
        group = null
        ed.tool = Tool.SELECT
    }
    fun pickTool(t: Tool) {
        ed.tool = t
        open = false
        group = null
    }
    val rows = ArrayList<DialRow>()
    for (d in dialTools) {
        val single = d.entries.singleOrNull()
        if (single != null) {
            rows.add(DialRow(single.g, single.label, ed.tool == single.tool) { pickTool(single.tool) })
        } else {
            val act = d.entries.firstOrNull { it.tool == ed.tool }
            rows.add(DialRow((act ?: d.entries[0]).g, d.name, act != null || group == d.name) { group = if (group == d.name) null else d.name })
            if (group == d.name) for (e in d.entries) rows.add(DialRow(e.g, e.label, ed.tool == e.tool, small = true) { pickTool(e.tool) })
        }
    }
    for (p in dialPanels) rows.add(DialRow(p.g, p.label, panel == p.panel) { onPanel(p.panel); open = false; group = null })
    val activeGlyph = dialTools.flatMap { it.entries }.firstOrNull { it.tool == ed.tool }?.g ?: G.Select
    val fab = pos ?: Offset(area.width - fabPx - marginPx, area.height - fabPx - marginPx)
    val known = area.width > 0
    // labels sit on the side towards the middle of the board, and the column opens towards it
    val right = !known || fab.x + fabPx / 2f > area.width / 2f
    val down = known && fab.y + fabPx / 2f < area.height / 2f
    Box(Modifier.fillMaxSize().onSizeChanged { area = it }) {
        if (open) Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); dismiss() } })
        Layout(
            content = {
                if (open) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(4.dp),
                        horizontalAlignment = if (right) Alignment.End else Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (r in if (down) rows else rows.asReversed()) DialRowView(r, right)
                    }
                }
                Box(
                    Modifier
                        .size(56.dp)
                        .shadow(6.dp, CircleShape)
                        .background(Accent, CircleShape)
                        .clip(CircleShape)
                        .clickable { if (open) dismiss() else open = true }
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val cur = pos ?: Offset(area.width - fabPx - marginPx, area.height - fabPx - marginPx)
                                pos = Offset(
                                    (cur.x + drag.x).coerceIn(0f, (area.width - fabPx).coerceAtLeast(0f)),
                                    (cur.y + drag.y).coerceIn(0f, (area.height - fabPx).coerceAtLeast(0f)),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Glyph(if (open) G.Close else activeGlyph, OnAccent) }
            },
            modifier = Modifier.fillMaxSize(),
        ) { ms, c ->
            val side = fabPx.roundToInt()
            val fabM = ms.last().measure(Constraints.fixed(side, side))
            val f = pos ?: Offset(c.maxWidth - fabPx - marginPx, c.maxHeight - fabPx - marginPx)
            val fx = f.x.roundToInt().coerceIn(0, (c.maxWidth - side).coerceAtLeast(0))
            val fy = f.y.roundToInt().coerceIn(0, (c.maxHeight - side).coerceAtLeast(0))
            val room = if (down) c.maxHeight - fy - side else fy
            val stack = if (ms.size > 1) ms.first().measure(Constraints(maxWidth = c.maxWidth, maxHeight = (room - gapPx).coerceAtLeast(0))) else null
            layout(c.maxWidth, c.maxHeight) {
                if (stack != null) {
                    val sx = (if (right) fx + side - stack.width else fx).coerceIn(0, (c.maxWidth - stack.width).coerceAtLeast(0))
                    stack.place(sx, if (down) fy + side + gapPx else fy - gapPx - stack.height)
                }
                fabM.place(fx, fy)
            }
        }
    }
}

@Composable
private fun DialRowView(r: DialRow, labelLeft: Boolean) {
    val d = if (r.small) 40.dp else 48.dp
    val circle = Modifier
        .size(d)
        .shadow(4.dp, CircleShape)
        .background(Accent, CircleShape)
        .then(if (r.active) Modifier.border(3.dp, Ink, CircleShape) else Modifier)
        .clip(CircleShape)
        .clickable(onClick = r.onClick)
    val labelShape = RoundedCornerShape(8.dp)
    Row(
        Modifier.padding(horizontal = if (r.small) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (labelLeft) DialLabel(r, labelShape)
        Box(circle, contentAlignment = Alignment.Center) { Glyph(r.g, OnAccent) }
        if (!labelLeft) DialLabel(r, labelShape)
    }
}

@Composable
private fun DialLabel(r: DialRow, shape: RoundedCornerShape) {
    Text(
        r.label,
        fontSize = 12.sp,
        color = Ink,
        fontWeight = if (r.active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .shadow(2.dp, shape)
            .background(Bar, shape)
            .clip(shape)
            .clickable(onClick = r.onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// ---------------- panels ----------------

@Composable
private fun PanelBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PanelBg)
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        content = content,
    )
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
}

@Composable
private fun Swatch(color: Int?, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) Accent else Line
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
            Modifier.size(36.dp).clip(CircleShape).border(1.dp, Line, CircleShape).clickable { open = true },
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
private fun ArtboardPanel(ed: Editor) {
    ed.version
    val w = ed.artboard.width()
    val h = ed.artboard.height()
    PanelBox {
        Label("Artboards (tap one on the canvas or here to work on it)")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ed.boards.forEachIndexed { i, b ->
                FilterChip(i == ed.activeBoard, { ed.selectBoard(i) }, { Text(b.name, fontSize = 12.sp) })
            }
            FilterChip(false, { ed.addBoard(w, h) }, { Text("Add artboard", fontSize = 12.sp) })
            FilterChip(false, { ed.fitAll() }, { Text("Show all", fontSize = 12.sp) })
            if (ed.boards.size > 1) FilterChip(false, { ed.removeBoard(ed.activeBoard) }, { Text("Delete this one", fontSize = 12.sp) })
        }
        Label("Size of the selected artboard")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Square" to (1000f to 1000f), "Landscape" to (1600f to 900f), "Portrait" to (900f to 1600f),
                "Banner" to (1500f to 500f), "App icon" to (512f to 512f),
            ).forEach { (name, size) ->
                FilterChip(w == size.first && h == size.second, { ed.setArtboard(size.first, size.second); ed.fitView() }, { Text(name, fontSize = 12.sp) })
            }
        }
        SliderRow("Width", w, 100f..4000f, 0, ed::fitView) { ed.setArtboard(it, h) }
        SliderRow("Height", h, 100f..4000f, 0, ed::fitView) { ed.setArtboard(w, it) }
        Label("Background (none stays transparent in PNG, PSD and SVG)")
        ColorRow(ed.background, true) { ed.chooseBackground(it) }
    }
}

@Composable
private fun SketchPanel(ed: Editor) {
    ed.version
    PanelBox {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(ed.sketchSnap, { ed.sketchSnap = !ed.sketchSnap }, { Text("Straighten shapes", fontSize = 12.sp) })
        }
        Label(
            ed.sketchNote?.let { "Recognised: $it. If that is not what you meant, undo, or turn Straighten off to only smooth your line." }
                ?: "Draw with your finger: circles, ovals, rectangles, triangles, polygons, stars, lines and outlines are recognised and tidied.",
        )
    }
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
        if (first.stroke != null) {
            Label("Stroke style")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Solid", "Dashed", "Dotted").forEachIndexed { i, n -> FilterChip(first.dash == i, { ed.setDash(i) }, { Text(n, fontSize = 12.sp) }) }
                listOf("Round ends", "Flat ends", "Square ends").forEachIndexed { i, n -> FilterChip(first.cap == i, { ed.setCap(i) }, { Text(n, fontSize = 12.sp) }) }
                listOf("Round joins", "Sharp joins", "Bevel joins").forEachIndexed { i, n -> FilterChip(first.join == i, { ed.setJoin(i) }, { Text(n, fontSize = 12.sp) }) }
            }
        }
        SliderRow("Opacity %", first.opacity * 100f, 0f..100f, 0, ed::endSlide) { ed.setOpacity(it / 100f) }
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
            FilterChip(ed.traceSmooth, { ed.tuneSmooth(!ed.traceSmooth) }, { Text("Smooth curves", fontSize = 12.sp) })
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
private fun LayersPanel(ed: Editor) {
    ed.version
    val rowH = 44.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragKey by remember { mutableStateOf<Long?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    PanelBox {
        if (ed.items.isEmpty()) {
            Label("No shapes yet.")
            return@PanelBox
        }
        // top layer first; a row follows the finger while its handle is dragged and the others make room
        val units = ed.layerUnits().asReversed()
        val from = units.indexOfFirst { it[0].id == dragKey }
        val target = if (from < 0) -1 else (from + (dragDy / rowPx).roundToInt()).coerceIn(0, units.lastIndex)
        for ((d, unit) in units.withIndex()) {
            val item = unit.last()
            val key = unit[0].id
            val on = item.id in ed.selection
            val shift = when {
                key == dragKey -> dragDy
                from in 0 until target && d in (from + 1)..target -> -rowPx
                target in 0 until from && d in target until from -> rowPx
                else -> 0f
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowH)
                    .zIndex(if (key == dragKey) 1f else 0f)
                    .graphicsLayer { translationY = shift }
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on || key == dragKey) SelBg else Color.Transparent)
                    .clickable { ed.selectFromLayers(item) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(rowH)
                        .pointerInput(key) {
                            detectVerticalDragGestures(
                                onDragStart = { dragKey = key; dragDy = 0f },
                                onVerticalDrag = { change, dy -> change.consume(); dragDy += dy },
                                onDragEnd = {
                                    val now = ed.layerUnits().asReversed()
                                    val f = now.indexOfFirst { it[0].id == key }
                                    if (f >= 0) {
                                        val t = (f + (dragDy / rowPx).roundToInt()).coerceIn(0, now.lastIndex)
                                        ed.moveLayerTo(now[f].last(), now.lastIndex - t)
                                    }
                                    dragKey = null
                                    dragDy = 0f
                                },
                                onDragCancel = { dragKey = null; dragDy = 0f },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.Grip, Muted) }
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(item.fill ?: item.stroke ?: 0xFF9CA3AF.toInt()))
                        .border(1.dp, Line, RoundedCornerShape(4.dp)),
                )
                Text(
                    if (unit.size > 1) "Group of ${unit.size} shapes" else item.label(),
                    fontSize = 12.sp,
                    color = if (item.visible) Ink else Muted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton({ ed.toggleVisible(item) }, Modifier.size(40.dp)) {
                    Glyph(if (item.visible) G.Eye else G.EyeOff, if (item.visible) Ink else Muted)
                }
                IconButton({ ed.toggleLock(item) }, Modifier.size(40.dp)) {
                    Glyph(if (item.locked) G.Lock else G.Unlock, if (item.locked) Ink else Muted)
                }
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
        val sel = ed.selectedItems()
        var dist by remember { mutableFloatStateOf(10f) }
        Label("Outline and offset (+ grows, - shrinks)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act("Outline stroke", sel.any { it.stroke != null && it.strokeWidth > 0f && it.image == null }) { ed.outlineStroke() }
            Act("Apply offset", sel.any { it.fill != null && it.image == null }) { ed.offsetPath(dist) }
        }
        SliderRow("Offset", dist, -100f..100f, onEnd = {}) { dist = it }
    }
}

/** A number box that applies its value when the keyboard is dismissed with Done or the box loses focus. */
@Composable
private fun NumField(label: String, value: Float, modifier: Modifier, clearAfter: Boolean = false, onCommit: (Float) -> Unit) {
    fun fmt(v: Float) = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
    var text by remember(value) { mutableStateOf(fmt(value)) }
    fun commit() {
        val v = text.replace(',', '.').toFloatOrNull()
        if (v == null || v.isNaN() || v.isInfinite()) { text = fmt(value); return }
        if (text != fmt(value)) onCommit(v)
        if (clearAfter) text = fmt(value)
    }
    OutlinedTextField(
        text, { text = it }, modifier.onFocusChanged { if (!it.isFocused) commit() },
        label = { Text(label, fontSize = 12.sp) },
        textStyle = TextStyle(fontSize = 12.sp, color = Ink),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
    )
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
        val r = ed.selectionRect()
        Label("Position and size (from artboard corner)")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val m = Modifier.weight(1f)
            NumField("X", r?.left ?: 0f, m) { ed.setSelectionRect(it, null, null, null) }
            NumField("Y", r?.top ?: 0f, m) { ed.setSelectionRect(null, it, null, null) }
            NumField("W", r?.width() ?: 0f, m) { ed.setSelectionRect(null, null, it, null) }
            NumField("H", r?.height() ?: 0f, m) { ed.setSelectionRect(null, null, null, it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("Turn by (deg)", 0f, Modifier.width(140.dp), clearAfter = true) { ed.rotateSelection(it) }
            Text("Keeps ratio when Ratio is on.", fontSize = 12.sp, color = Muted)
        }
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
            Act("Copy", n > 0) { ed.copySelected() }
            Act("Cut", n > 0) { ed.cutSelected() }
            Act("Paste", ed.canPaste) { ed.paste() }
            Act("Zoom to fit", n > 0) { ed.fitSelection() }
            Act("Select all", true) { ed.selectAll() }
            Act("Delete", n > 0) { ed.deleteSelected() }
        }
    }
}


// ---------------- quick access ----------------

@Composable
private fun QPill(content: @Composable () -> Unit) {
    Row(
        Modifier
            .shadow(8.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Bar)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun QBtn(g: G, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val tint = if (enabled) Ink else Muted
    Column(
        Modifier
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) SelBg else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(g, tint)
        Text(label, fontSize = 12.sp, color = tint, maxLines = 1)
    }
}

@Composable
private fun QDivider() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(Line))
}

/** Shown after a double-tap on a shape; stays while anything is selected. */
@Composable
private fun QuickBar(ed: Editor, modifier: Modifier) {
    ed.version
    val n = ed.selection.size
    val show = ed.quickOn && n > 0 && ed.tool == Tool.SELECT
    LaunchedEffect(n == 0) { if (n == 0) ed.quickOn = false }
    var open by remember { mutableStateOf("") }
    LaunchedEffect(show) { if (!show) open = "" }
    val units = if (show) ed.selectedUnits().size else 0
    fun pick(k: String) { open = if (open == k) "" else k }
    AnimatedVisibility(
        show, modifier.padding(8.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (open.isNotEmpty()) QPill {
                when (open) {
                    "align" -> {
                        QBtn(G.AlignL, "Left") { ed.align(AlignTo.LEFT) }
                        QBtn(G.AlignC, "Center") { ed.align(AlignTo.CENTER) }
                        QBtn(G.AlignR, "Right") { ed.align(AlignTo.RIGHT) }
                        QBtn(G.AlignT, "Top") { ed.align(AlignTo.TOP) }
                        QBtn(G.AlignM, "Middle") { ed.align(AlignTo.MIDDLE) }
                        QBtn(G.AlignB, "Bottom") { ed.align(AlignTo.BOTTOM) }
                    }
                    "rotate" -> {
                        QBtn(G.RotL, "Rotate left") { ed.rotate90(false) }
                        QBtn(G.RotR, "Rotate right") { ed.rotate90(true) }
                        QBtn(G.FlipV, "Flip vertical") { ed.flip(false) }
                        QBtn(G.FlipH, "Flip horizontal") { ed.flip(true) }
                    }
                    "order" -> {
                        QBtn(G.ToFront, "To front") { ed.reorder(front = true, toEnd = true) }
                        QBtn(G.Forward, "Forward") { ed.reorder(front = true, toEnd = false) }
                        QBtn(G.Backward, "Backward") { ed.reorder(front = false, toEnd = false) }
                        QBtn(G.ToBack, "To back") { ed.reorder(front = false, toEnd = true) }
                    }
                    "group" -> {
                        QBtn(G.Group, "Group") { ed.group() }
                        QBtn(G.Ungroup, "Ungroup", ed.selectedItems().any { it.groupId != 0L }) { ed.ungroup() }
                    }
                    "clip" -> {
                        QBtn(G.Clip, "Clip") { ed.clipToTop() }
                        QBtn(G.Unclip, "Unclip", ed.canUnclip) { ed.unclip() }
                    }
                    "dist" -> {
                        QBtn(G.DistH, "Horizontal") { ed.distribute(true) }
                        QBtn(G.DistV, "Vertical") { ed.distribute(false) }
                    }
                }
            }
            QPill {
                QBtn(G.AlignL, "Align", active = open == "align") { pick("align") }
                QBtn(G.RotR, "Rotate", active = open == "rotate") { pick("rotate") }
                QBtn(G.Arrange, "Order", active = open == "order") { pick("order") }
                if (n >= 2) {
                    QBtn(G.Group, "Group", active = open == "group") { pick("group") }
                    QBtn(G.Clip, "Clip", active = open == "clip") { pick("clip") }
                }
                if (units >= 3) QBtn(G.DistH, "Distribute", active = open == "dist") { pick("dist") }
                QDivider()
                QBtn(G.Dup, "Duplicate") { ed.duplicateSelected() }
                QBtn(G.Copy, "Copy") { ed.copySelected() }
                QBtn(G.Paste, "Paste", ed.canPaste) { ed.paste() }
                QBtn(G.Zoom, "Zoom") { ed.fitSelection() }
                QBtn(G.SelAll, "Select all") { ed.selectAll() }
                QBtn(G.Trash, "Delete") { ed.deleteSelected() }
            }
        }
    }
}

/** Shown after a long-press on an artboard. */
@Composable
private fun BoardBar(ed: Editor, modifier: Modifier) {
    ed.version
    val i = ed.boardMenu
    val moving = ed.boardMoveOn
    val show = (i != null && i in ed.boards.indices) || moving
    val name = if (show) ed.boards.getOrNull(ed.activeBoard)?.name.orEmpty() else ""
    AnimatedVisibility(show, modifier.padding(8.dp), enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
        QPill {
            Text(name, fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
            if (moving) {
                Text("Drag to move", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(end = 6.dp))
                QBtn(G.Check, "Done") { ed.boardMoveOn = false }
            } else {
                QBtn(G.Move, "Move") { ed.boardMenu = null; ed.boardMoveOn = true }
                QBtn(G.Dup, "Duplicate") { ed.duplicateBoard(ed.activeBoard) }
                QBtn(G.Paste, "Paste", ed.canPaste) { ed.paste() }
                QBtn(G.Trash, "Delete", ed.boards.size > 1) { ed.removeBoard(ed.activeBoard) }
            }
        }
    }
}
