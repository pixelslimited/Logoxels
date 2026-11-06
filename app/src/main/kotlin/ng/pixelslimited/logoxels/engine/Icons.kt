package ng.pixelslimited.logoxels.engine

import android.graphics.Matrix
import android.graphics.Path
import ng.pixelslimited.logoxels.engine.geom.PathBridge
import ng.pixelslimited.logoxels.engine.geom.PathParse

/** Built-in vector shapes, each drawn in a 100 x 100 box. */
object Icons {
    val all: List<Pair<String, String>> = listOf(
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
        "Pin" to "M50 95 C50 95 18 58 18 38 C18 18 32 5 50 5 C68 5 82 18 82 38 C82 58 50 95 50 95 Z",
        "Bubble" to "M10 12 L90 12 L90 68 L45 68 L25 90 L28 68 L10 68 Z",
        "Home" to "M50 8 L95 48 L82 48 L82 90 L58 90 L58 62 L42 62 L42 90 L18 90 L18 48 L5 48 Z",
        "Leaf" to "M15 85 C10 40 40 10 90 10 C90 60 60 90 15 85 Z",
        "Ribbon" to "M25 5 L75 5 L75 95 L50 72 L25 95 Z",
    )

    /** The icon outline in its own 100 x 100 box. */
    fun path(d: String): Path = PathBridge.toPath(PathParse.parse(d))

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
