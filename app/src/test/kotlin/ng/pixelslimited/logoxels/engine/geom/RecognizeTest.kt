package ng.pixelslimited.logoxels.engine.geom

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizeTest {
    private val rnd = Random(7)

    private fun noisy(pts: List<P>, level: Double): List<P> {
        val d = hypot(pts.maxOf { it.x } - pts.minOf { it.x }, pts.maxOf { it.y } - pts.minOf { it.y })
        return pts.map { P(it.x + rnd.nextGaussian() * level * d, it.y + rnd.nextGaussian() * level * d) }
    }

    private fun outline(v: List<P>, per: Int = 20): List<P> {
        val out = ArrayList<P>()
        for (i in v.indices) {
            val a = v[i]
            val b = v[(i + 1) % v.size]
            for (t in 0 until per) out.add(a.lerp(b, t.toDouble() / per))
        }
        out.add(v[0])
        return out
    }

    private fun ngon(n: Int, r: Double, rot: Double = 0.0) = List(n) { P(r * cos(rot + 2 * PI * it / n), r * sin(rot + 2 * PI * it / n)) }

    private fun star(n: Int, r: Double, ri: Double) = List(2 * n) {
        val a = -PI / 2 + it * PI / n
        val rr = if (it % 2 == 0) r else ri
        P(rr * cos(a), rr * sin(a))
    }

    @Test fun circle() {
        val pts = List(62) { P(100 * cos(it / 60.0 * 2 * PI * 1.03), 100 * sin(it / 60.0 * 2 * PI * 1.03)) }
        val s = Recognize.shape(noisy(pts, 0.003)) as Shape.Oval
        assertEquals(s.rx, s.ry, 1e-9)
        assertEquals(100.0, s.rx, 6.0)
    }

    @Test fun oval() {
        val pts = List(61) { P(150 * cos(it / 60.0 * 2 * PI * 1.02), 80 * sin(it / 60.0 * 2 * PI * 1.02)) }
        val s = Recognize.shape(noisy(pts, 0.003)) as Shape.Oval
        assertTrue(s.rx > s.ry * 1.5)
    }

    @Test fun rectangleStraightens() {
        val s = Recognize.shape(noisy(outline(listOf(P(0.0, 0.0), P(200.0, 5.0), P(198.0, 120.0), P(-3.0, 115.0))), 0.003)) as Shape.Rect
        assertEquals(0.0, s.angle, 1e-9)
        assertEquals(200.0, s.w, 12.0)
        assertEquals(118.0, s.h, 12.0)
    }

    @Test fun squareIsSquare() {
        val s = Recognize.shape(noisy(outline(listOf(P(0.0, 0.0), P(100.0, 0.0), P(100.0, 100.0), P(0.0, 100.0))), 0.003)) as Shape.Rect
        assertEquals(s.w, s.h, 1e-9)
    }

    @Test fun regularPolygons() {
        for (n in listOf(5, 6)) {
            val s = Recognize.shape(noisy(outline(ngon(n, 100.0, -PI / 2)), 0.003)) as Shape.Regular
            assertEquals(n, s.sides)
        }
    }

    @Test fun stars() {
        for (n in listOf(4, 5)) {
            val s = Recognize.shape(noisy(outline(star(n, 100.0, if (n == 5) 45.0 else 40.0)), 0.003)) as Shape.Star
            assertEquals(n, s.points)
        }
    }

    @Test fun lineAndSnap() {
        val pts = List(40) { P(it * 5.0, it * 0.4) }
        val s = Recognize.shape(noisy(pts, 0.002)) as Shape.Line
        assertEquals(s.a.y, s.b.y, 1e-9)
    }

    @Test fun lShapeBecomesStraightPolygon() {
        val l = listOf(P(0.0, 0.0), P(0.0, 100.0), P(60.0, 100.0), P(60.0, 70.0), P(30.0, 70.0), P(30.0, 0.0))
        val s = Recognize.shape(noisy(outline(l), 0.003)) as Shape.Poly
        assertEquals(6, s.pts.size)
    }

    @Test fun heartIsSmoothedNotPolygon() {
        val pts = List(71) {
            val t = it * 2 * PI / 70
            P(16 * Math.pow(sin(t), 3.0), -(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)))
        }
        assertTrue(Recognize.shape(pts) is Shape.Curve)
    }
}
