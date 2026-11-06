package ng.pixelslimited.logoxels.engine.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage5Test {
    @Test fun parseSquareAndCurve() {
        val cs = PathParse.parse("M0 0 L10 0 L10 10 L0 10 Z M20 20 C21 22 23 22 24 20")
        assertEquals(2, cs.size)
        assertTrue(cs[0].closed)
        assertEquals(4, cs[0].segs.size)
        assertEquals(false, cs[1].closed)
        assertEquals("M0 0L10 0L10 10L0 10L0 0Z", PathData.of(listOf(cs[0])))
    }

    @Test fun traceBlockHasFourCorners() {
        val loops = Trace.loops(5, 5) { x, y -> x in 1..3 && y in 1..3 }
        assertEquals(1, loops.size)
        assertEquals(4, loops[0].size)
    }

    @Test fun traceHoleGivesTwoLoops() {
        val loops = Trace.loops(5, 5) { x, y -> !(x == 2 && y == 2) }
        assertEquals(2, loops.size)
    }

    @Test fun traceDiagonalTouchTerminates() {
        val loops = Trace.loops(2, 2, 0.0) { x, y -> x == y }
        assertTrue(loops.isNotEmpty())
    }

    @Test fun wrapFitsWords() {
        val words = listOf("aa", "bb", "cc")
        assertEquals(2, TextWrap.fit(words, 0, 5f, 1f) { 2f })
        assertEquals(0, TextWrap.fit(words, 0, 1f, 1f) { 2f })
        assertEquals(1, TextWrap.fit(words, 2, 5f, 1f) { 2f })
    }

    @Test fun gapsAroundObstacle() {
        val g = TextWrap.largestGap(0f, 100f, listOf(60f to 80f))
        assertEquals(0f to 60f, g)
        assertNull(TextWrap.largestGap(0f, 10f, listOf(-5f to 20f)))
        assertNotNull(TextWrap.largestGap(0f, 10f, emptyList()))
    }

    @Test fun warpIdentityAndArch() {
        val p = P(50.0, 100.0)
        assertEquals(p.y, Warper.map(Warp.NONE, 1.0, p, 0.0, 100.0, 0.0, 20.0).y, 1e-9)
        assertEquals(100.0 - 25.0, Warper.map(Warp.ARCH, 1.0, p, 0.0, 100.0, 0.0, 20.0).y, 1e-9)
        assertEquals(100.0, Warper.map(Warp.ARCH, 1.0, P(0.0, 100.0), 0.0, 100.0, 0.0, 20.0).y, 1e-9)
    }

    @Test fun warpKeepsContourCount() {
        val sq = Contour(listOf(Seg.line(P(0.0, 0.0), P(10.0, 0.0)), Seg.line(P(10.0, 0.0), P(0.0, 10.0))), true)
        val out = Warper.apply(listOf(sq), Warp.WAVE, 0.5)
        assertEquals(1, out.size)
        assertEquals(16, out[0].segs.size)
    }
}

class SmoothTraceTest {
    @Test fun squareKeepsCorners() {
        val sq = listOf(P(0.0, 0.0), P(10.0, 0.0), P(10.0, 10.0), P(0.0, 10.0))
        val c = Trace.smoothContour(sq)
        assertEquals(4, c.segs.size)
        assertEquals(0.0, c.segs[0].p1.y, 1e-9)
        assertEquals(0.0, c.segs[0].p2.y, 1e-9)
    }

    @Test fun octagonIsTangentContinuous() {
        val oct = List(8) { i -> val a = Math.toRadians(i * 45.0); P(10 * Math.cos(a), 10 * Math.sin(a)) }
        val c = Trace.smoothContour(oct)
        for (i in c.segs.indices) {
            val into = (c.segs[i].p3 - c.segs[i].p2).unit()
            val out = (c.segs[(i + 1) % 8].p1 - c.segs[(i + 1) % 8].p0).unit()
            assertEquals(1.0, into dot out, 1e-9)
        }
    }
}
