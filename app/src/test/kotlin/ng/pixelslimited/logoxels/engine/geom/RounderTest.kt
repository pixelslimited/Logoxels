package ng.pixelslimited.logoxels.engine.geom

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RounderTest {
    private fun square(s: Double = 200.0) = Contour(
        listOf(
            Seg.line(P(0.0, 0.0), P(s, 0.0)),
            Seg.line(P(s, 0.0), P(s, s)),
            Seg.line(P(s, s), P(0.0, s)),
            Seg.line(P(0.0, s), P(0.0, 0.0)),
        ),
        true,
    )

    private fun assertContinuous(c: Contour) {
        val n = c.segs.size
        for (i in 0 until n) {
            val a = c.segs[i]
            val b = c.segs[(i + 1) % n]
            assertTrue("gap at $i", (a.p3 - b.p0).len() < 1e-6)
            val ang = acos((a.tangent(1.0) dot b.tangent(0.0)).coerceIn(-1.0, 1.0))
            assertTrue("kink at $i: $ang", ang < 1e-6)
        }
    }

    @Test
    fun squareCornersBecomeCircularArcs() {
        val out = Rounder.round(listOf(square()), { 40.0 })[0]
        assertEquals(8, out.segs.size)
        val centres = listOf(P(40.0, 40.0), P(160.0, 40.0), P(160.0, 160.0), P(40.0, 160.0))
        for (s in out.segs.filter { !it.line }) {
            for (i in 0..20) {
                val p = s.at(i / 20.0)
                val err = centres.minOf { abs((p - it).len() - 40.0) }
                assertTrue("radial error $err", err < 0.1)
            }
        }
        assertContinuous(out)
    }

    @Test
    fun curvedSideIsKeptAndJoinsAreSmooth() {
        val top = Seg.cubic(P(0.0, 0.0), P(60.0, -120.0), P(140.0, -120.0), P(200.0, 0.0))
        val c = Contour(
            listOf(
                top,
                Seg.line(P(200.0, 0.0), P(200.0, 200.0)),
                Seg.line(P(200.0, 200.0), P(0.0, 200.0)),
                Seg.line(P(0.0, 200.0), P(0.0, 0.0)),
            ),
            true,
        )
        val out = Rounder.round(listOf(c), { 30.0 })[0]
        assertEquals(8, out.segs.size)
        assertTrue(out.segs.any { !it.line })
        assertContinuous(out)
    }

    @Test
    fun hugeRadiusIsCappedWithoutOverlapOrNaN() {
        val c = Contour(
            listOf(
                Seg.line(P(0.0, 0.0), P(100.0, 0.0)),
                Seg.line(P(100.0, 0.0), P(50.0, 80.0)),
                Seg.line(P(50.0, 80.0), P(0.0, 0.0)),
            ),
            true,
        )
        val out = Rounder.round(listOf(c), { 10000.0 })[0]
        for (s in out.segs) for (p in listOf(s.p0, s.p1, s.p2, s.p3)) {
            assertTrue(!p.x.isNaN() && !p.y.isNaN())
            assertTrue(p.x > -1.0 && p.x < 101.0 && p.y > -1.0 && p.y < 81.0)
        }
        assertContinuous(out)
    }

    @Test
    fun onlyTheRequestedCornerIsRounded() {
        val out = Rounder.round(listOf(square()), { if (it == 2) 30.0 else 0.0 })[0]
        assertEquals(5, out.segs.size)
        assertEquals(1, out.segs.count { !it.line })
        // the rounded corner is the bottom-right one (node 2 starts at (200,200))
        val arc = out.segs.first { !it.line }
        assertTrue(min((arc.p0 - P(200.0, 170.0)).len(), (arc.p0 - P(170.0, 200.0)).len()) < 1e-6)
    }

    @Test
    fun openPathKeepsItsEndsSharp() {
        val c = Contour(
            listOf(Seg.line(P(0.0, 0.0), P(100.0, 0.0)), Seg.line(P(100.0, 0.0), P(100.0, 100.0))),
            false,
        )
        val out = Rounder.round(listOf(c), { 20.0 })[0]
        assertEquals(3, out.segs.size)
        assertTrue((out.segs.first().p0 - P(0.0, 0.0)).len() < 1e-9)
        assertTrue((out.segs.last().p3 - P(100.0, 100.0)).len() < 1e-9)
    }

    @Test
    fun nodeNumberingMatchesCornerCount() {
        assertEquals(4, Rounder.nodes(listOf(square())).size)
    }
}
