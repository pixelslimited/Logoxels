package ng.pixelslimited.logoxels.engine

import android.graphics.Matrix
import android.graphics.Path
import ng.pixelslimited.logoxels.engine.geom.PathBridge
import ng.pixelslimited.logoxels.engine.geom.PathParse
import java.util.Locale

/** Built-in vector shapes, each drawn in a 100 x 100 box. */
object Icons {
    private fun n(v: Double) = String.format(Locale.US, "%.1f", v)

    /** A closed ellipse as four curves. */
    private fun oval(cx: Double, cy: Double, rx: Double, ry: Double): String {
        val k = 0.5523
        val l = cx - rx; val r = cx + rx; val t = cy - ry; val b = cy + ry
        return "M${n(r)} ${n(cy)} C${n(r)} ${n(cy + k * ry)} ${n(cx + k * rx)} ${n(b)} ${n(cx)} ${n(b)} " +
            "C${n(cx - k * rx)} ${n(b)} ${n(l)} ${n(cy + k * ry)} ${n(l)} ${n(cy)} " +
            "C${n(l)} ${n(cy - k * ry)} ${n(cx - k * rx)} ${n(t)} ${n(cx)} ${n(t)} " +
            "C${n(cx + k * rx)} ${n(t)} ${n(r)} ${n(cy - k * ry)} ${n(r)} ${n(cy)} Z "
    }

    private fun circle(cx: Double, cy: Double, r: Double) = oval(cx, cy, r, r)

    /** Shapes grouped for the library. Outlines with holes use several sub-paths, filled even-odd. */
    val sections: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "Contact" to listOf(
            "Location" to "M50 95 C50 95 18 58 18 38 C18 18 32 5 50 5 C68 5 82 18 82 38 C82 58 50 95 50 95 Z " + circle(50.0, 38.0, 13.0),
            "Phone" to "M24 8 C20 8 8 14 8 26 C8 62 38 92 74 92 C86 92 92 80 92 76 C92 72 90 70 87 68 L73 61 C70 60 67 60 65 62 L58 69 C46 63 37 54 31 42 L38 35 C40 33 40 30 39 27 L32 13 C30 10 27 8 24 8 Z",
            "Email" to "M6 20 L94 20 L94 80 L6 80 Z M10 27 L50 57 L90 27 L90 37 L50 67 L10 37 Z",
            "Website" to circle(50.0, 50.0, 44.0) + circle(50.0, 50.0, 37.0) + oval(50.0, 50.0, 17.0, 36.0) + oval(50.0, 50.0, 11.0, 30.0) +
                "M13 47 L33 47 L33 53 L13 53 Z M39 47 L61 47 L61 53 L39 53 Z M67 47 L87 47 L87 53 L67 53 Z",
        ),
        "People" to listOf(
            "Avatar" to circle(50.0, 50.0, 46.0) + circle(50.0, 50.0, 41.0) + circle(50.0, 38.0, 14.0) +
                "M24 80 C26 64 38 58 50 58 C62 58 74 64 76 80 C68 86 59 88 50 88 C41 88 32 86 24 80 Z",
            "Male" to circle(50.0, 16.0, 10.0) +
                "M32 30 L68 30 C72 30 74 33 74 37 L74 58 L67 58 L67 40 L64 40 L64 92 L54 92 L54 62 L46 62 L46 92 L36 92 L36 40 L33 40 L33 58 L26 58 L26 37 C26 33 28 30 32 30 Z",
            "Female" to circle(50.0, 16.0, 10.0) +
                "M38 30 L62 30 C65 30 67 32 68 35 L82 68 L67 68 L67 92 L57 92 L57 68 L43 68 L43 92 L33 92 L33 68 L18 68 L32 35 C33 32 35 30 38 30 Z",
            "Staff" to circle(50.0, 30.0, 17.0) +
                "M16 94 C16 68 32 56 50 56 C68 56 84 68 84 94 Z M46 59 L54 59 L56 68 L52 88 L48 88 L44 68 Z",
            "Student" to "M50 16 L96 36 L50 56 L4 36 Z M24 50 L50 62 L76 50 L76 68 C76 76 62 82 50 82 C38 82 24 76 24 68 Z M89 42 L93 42 L93 68 L89 68 Z",
            "Nigeria" to "M6 24 L36 24 L36 76 L6 76 Z M64 24 L94 24 L94 76 L64 76 Z",
        ),
        "General" to listOf(
            "Heart" to "M50 88 C20 65 8 45 8 30 C8 15 20 8 32 8 C42 8 48 14 50 20 C52 14 58 8 68 8 C80 8 92 15 92 30 C92 45 80 65 50 88 Z",
            "Cloud" to "M25 75 C10 75 5 60 15 52 C12 38 28 28 40 35 C45 20 70 20 75 38 C92 35 98 55 88 65 C95 75 85 80 75 75 Z",
            "Drop" to "M50 8 C50 8 20 45 20 62 C20 80 33 92 50 92 C67 92 80 80 80 62 C80 45 50 8 50 8 Z",
            "Bolt" to "M58 5 L20 55 L45 55 L38 95 L80 40 L55 40 Z",
            "Crown" to "M10 78 L5 30 L30 50 L50 15 L70 50 L95 30 L90 78 Z",
            "Shield" to "M50 5 L88 18 C88 55 75 80 50 95 C25 80 12 55 12 18 Z",
            "Arrow" to "M5 38 L55 38 L55 15 L95 50 L55 85 L55 62 L5 62 Z",
            "Plus" to "M38 8 L62 8 L62 38 L92 38 L92 62 L62 62 L62 92 L38 92 L38 62 L8 62 L8 38 L38 38 Z",
            "Check" to "M8 55 L22 41 L40 59 L78 18 L92 32 L40 87 Z",
            "Moon" to "M65 8 C35 12 15 35 20 62 C25 85 50 97 75 88 C50 80 40 50 55 28 C58 20 62 14 65 8 Z",
            "Diamond" to "M50 5 L92 38 L50 95 L8 38 Z",
            "Bubble" to "M10 12 L90 12 L90 68 L45 68 L25 90 L28 68 L10 68 Z",
            "Home" to "M50 8 L95 48 L82 48 L82 90 L58 90 L58 62 L42 62 L42 90 L18 90 L18 48 L5 48 Z",
            "Leaf" to "M15 85 C10 40 40 10 90 10 C90 60 60 90 15 85 Z",
            "Ribbon" to "M25 5 L75 5 L75 95 L50 72 L25 95 Z",
        ),
    )

    /** The icon outline in its own 100 x 100 box. */
    fun path(d: String): Path = PathBridge.toPath(PathParse.parse(d)).also {
        if (d.count { c -> c == 'M' } > 1) it.fillType = Path.FillType.EVEN_ODD
    }

    /** The icon scaled to [size] artboard units and centred on ([cx], [cy]). */
    fun placed(d: String, cx: Float, cy: Float, size: Float): Path {
        val p = path(d)
        val m = Matrix()
        m.setScale(size / 100f, size / 100f)
        m.postTranslate(cx - size / 2f, cy - size / 2f)
        p.transform(m)
        return p
    }
}
