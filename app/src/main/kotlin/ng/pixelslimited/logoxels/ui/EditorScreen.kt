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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ng.pixelslimited.logoxels.engine.AlignTo
import ng.pixelslimited.logoxels.engine.Editor
import ng.pixelslimited.logoxels.engine.Kind
import ng.pixelslimited.logoxels.engine.Mode
import ng.pixelslimited.logoxels.engine.PfOp
import ng.pixelslimited.logoxels.engine.Tool
import kotlin.math.roundToInt
import android.graphics.Paint as APaint

internal enum class Panel { NONE, STYLE, COMBINE, ARRANGE }

private val Ink = Color(0xFF111827)
private val Muted = Color(0xFF9CA3AF)
private val Accent = Color(0xFF2563EB)
private val palette = listOf(
    0xFF111827, 0xFFFFFFFF, 0xFFEF4444, 0xFFF97316, 0xFFFACC15, 0xFF22C55E,
    0xFF14B8A6, 0xFF1E88E5, 0xFF6366F1, 0xFFA855F7, 0xFFEC4899, 0xFF78716C,
).map { it.toInt() }

@Composable
fun EditorScreen(ed: Editor) {
    var panel by remember { mutableStateOf(Panel.NONE) }
    Column(Modifier.fillMaxSize().background(Color(0xFFECEFF1)).safeDrawingPadding()) {
        TopBar(ed)
        Box(Modifier.weight(1f).fillMaxWidth()) { EditorCanvas(ed) }
        when (panel) {
            Panel.STYLE -> StylePanel(ed)
            Panel.COMBINE -> CombinePanel(ed)
            Panel.ARRANGE -> ArrangePanel(ed)
            Panel.NONE -> Unit
        }
        BottomBar(ed, panel) { panel = if (panel == it) Panel.NONE else it }
    }
}

// ---------------- canvas ----------------

@Composable
private fun EditorCanvas(ed: Editor) {
    val handleR = with(LocalDensity.current) { 24.dp.toPx() }
    val fillPaint = remember { APaint(APaint.ANTI_ALIAS_FLAG) }
    val strokePaint = remember {
        APaint(APaint.ANTI_ALIAS_FLAG).apply {
            style = APaint.Style.STROKE
            strokeJoin = APaint.Join.ROUND
            strokeCap = APaint.Cap.ROUND
        }
    }
    val artPaint = remember { APaint().apply { color = android.graphics.Color.WHITE } }
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
            for (item in ed.items) {
                val rp = item.renderPath()
                item.fill?.let { fillPaint.color = it; nc.drawPath(rp, fillPaint) }
                item.stroke?.let {
                    if (item.strokeWidth > 0f) {
                        strokePaint.color = it
                        strokePaint.strokeWidth = item.strokeWidth
                        nc.drawPath(rp, strokePaint)
                    }
                }
            }
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
    }
}

// ---------------- bars ----------------

@Composable
private fun TopBar(ed: Editor) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Logoxels", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink, modifier = Modifier.weight(1f).padding(start = 4.dp))
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
        VerticalDivider(Modifier.height(36.dp).padding(horizontal = 4.dp))
        ToolBtn(G.Style, "Style", panel == Panel.STYLE) { onPanel(Panel.STYLE) }
        ToolBtn(G.Union, "Combine", panel == Panel.COMBINE) { onPanel(Panel.COMBINE) }
        ToolBtn(G.Arrange, "Arrange", panel == Panel.ARRANGE) { onPanel(Panel.ARRANGE) }
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
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowNone) Swatch(null, current == null) { onPick(null) }
        palette.forEach { c -> Swatch(c, current == c) { onPick(c) } }
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
        ColorRow(first.fill, true) { ed.setFill(it) }
        Label("Stroke")
        ColorRow(first.stroke, true) { ed.setStroke(it) }
        SliderRow("Stroke width", first.strokeWidth, 0f..60f, 0, ed::endSlide) { ed.setStrokeWidth(it) }
        SliderRow("Round corners", first.corner, 0f..300f, 0, ed::endSlide) { ed.setCorner(it) }
        if (sel.size == 1 && (first.kind == Kind.POLYGON || first.kind == Kind.STAR)) {
            SliderRow("Sides", first.sides.toFloat(), 3f..12f, 8, ed::endSlide) { ed.setSides(it.roundToInt()) }
        }
        if (sel.size == 1 && first.kind == Kind.STAR) {
            SliderRow("Inner size %", first.inner * 100f, 20f..90f, 0, ed::endSlide) { ed.setInner(it / 100f) }
        }
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
