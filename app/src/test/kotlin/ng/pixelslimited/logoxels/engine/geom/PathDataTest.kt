package ng.pixelslimited.logoxels.engine.geom

import org.junit.Assert.assertEquals
import org.junit.Test

class PathDataTest {
    private fun p(x: Double, y: Double) = P(x, y)

    @Test fun formatsNumbers() {
        assertEquals("0", PathData.num(0.0))
        assertEquals("0", PathData.num(-0.001))
        assertEquals("12", PathData.num(12.0))
        assertEquals("1.5", PathData.num(1.5))
        assertEquals("-3.25", PathData.num(-3.25))
        assertEquals("0.05", PathData.num(0.05))
    }

    @Test fun squareAndCurve() {
        val sq = Contour(listOf(
            Seg.line(p(0.0, 0.0), p(10.0, 0.0)),
            Seg.line(p(10.0, 0.0), p(10.0, 10.0)),
            Seg.line(p(10.0, 10.0), p(0.0, 0.0)),
        ), true)
        assertEquals("M0 0L10 0L10 10L0 0Z", PathData.of(listOf(sq)))
        val cv = Contour(listOf(Seg.cubic(p(0.0, 0.0), p(1.0, 2.0), p(3.0, 2.0), p(4.0, 0.0))), false)
        assertEquals("M0 0C1 2 3 2 4 0", PathData.of(listOf(cv)))
    }
}
