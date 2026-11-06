package ng.pixelslimited.logoxels.engine.geom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodesTest {
    private fun square() = Contour(
        listOf(
            Seg.line(P(0.0, 0.0), P(100.0, 0.0)),
            Seg.line(P(100.0, 0.0), P(100.0, 100.0)),
            Seg.line(P(100.0, 100.0), P(0.0, 100.0)),
            Seg.line(P(0.0, 100.0), P(0.0, 0.0)),
        ),
        true,
    )

    private fun curve() = Contour(
        listOf(
            Seg.cubic(P(0.0, 0.0), P(30.0, -80.0), P(70.0, -80.0), P(100.0, 0.0)),
            Seg.line(P(100.0, 0.0), P(0.0, 0.0)),
        ),
        true,
    )

    @Test
    fun roundTripKeepsGeometry() {
        val nps = Nodes.fromContours(listOf(curve()))
        assertEquals(2, nps[0].nodes.size)
        val back = Nodes.toContours(nps)[0]
        assertEquals(2, back.segs.size)
        assertFalse(back.segs[0].line)
        assertTrue(back.segs[1].line)
        for (i in 0..10) {
            val t = i / 10.0
            assertTrue((back.segs[0].at(t) - curve().segs[0].at(t)).len() < 1e-9)
        }
    }

    @Test
    fun insertOnLineAddsAStraightPoint() {
        val nps = Nodes.fromContours(listOf(square()))
        val idx = Nodes.insert(nps[0], 0, 0.5)
        assertEquals(1, idx)
        assertEquals(5, nps[0].nodes.size)
        assertTrue((nps[0].nodes[1].p - P(50.0, 0.0)).len() < 1e-9)
        assertTrue(Nodes.toContours(nps)[0].segs.all { it.line })
    }

    @Test
    fun insertOnCurveDoesNotChangeTheShape() {
        val orig = curve().segs[0]
        val nps = Nodes.fromContours(listOf(curve()))
        Nodes.insert(nps[0], 0, 0.5)
        val segs = Nodes.toContours(nps)[0].segs
        assertEquals(3, segs.size)
        // first half at its midpoint equals the original at t = 0.25
        assertTrue((segs[0].at(0.5) - orig.at(0.25)).len() < 1e-9)
        assertTrue((segs[1].at(0.5) - orig.at(0.75)).len() < 1e-9)
    }

    @Test
    fun nearestFindsTheOutline() {
        val nps = Nodes.fromContours(listOf(square()))
        val hit = Nodes.nearest(nps, P(50.0, -7.0))!!
        assertEquals(0, hit.seg)
        assertEquals(7.0, hit.dist, 1e-6)
        assertEquals(0.5, hit.t, 1e-3)
    }

    @Test
    fun cuttingAClosedShapeGivesOneOpenPathStartingAndEndingAtThePoint() {
        val nps = Nodes.fromContours(listOf(square()))
        val parts = Nodes.cut(nps[0], 1)
        assertEquals(1, parts.size)
        assertFalse(parts[0].closed)
        assertEquals(5, parts[0].nodes.size)
        assertTrue((parts[0].nodes.first().p - P(100.0, 0.0)).len() < 1e-9)
        assertTrue((parts[0].nodes.last().p - P(100.0, 0.0)).len() < 1e-9)
    }

    @Test
    fun cuttingAnOpenPathInTheMiddleGivesTwoPaths() {
        val open = Contour(
            listOf(Seg.line(P(0.0, 0.0), P(10.0, 0.0)), Seg.line(P(10.0, 0.0), P(20.0, 0.0))),
            false,
        )
        val nps = Nodes.fromContours(listOf(open))
        assertEquals(3, nps[0].nodes.size)
        val parts = Nodes.cut(nps[0], 1)
        assertEquals(2, parts.size)
        assertEquals(2, parts[0].nodes.size)
        assertEquals(2, parts[1].nodes.size)
    }

    @Test
    fun deleteRefusesToLeaveTooFewPoints() {
        val tri = Contour(
            listOf(
                Seg.line(P(0.0, 0.0), P(10.0, 0.0)),
                Seg.line(P(10.0, 0.0), P(0.0, 10.0)),
                Seg.line(P(0.0, 10.0), P(0.0, 0.0)),
            ),
            true,
        )
        val nps = Nodes.fromContours(listOf(tri))
        assertFalse(Nodes.delete(nps[0], 0))
        val sq = Nodes.fromContours(listOf(square()))
        assertTrue(Nodes.delete(sq[0], 0))
        assertEquals(3, sq[0].nodes.size)
    }

    @Test
    fun smoothPointGetsCollinearHandles() {
        val nps = Nodes.fromContours(listOf(square()))
        Nodes.makeSmooth(nps[0], 1)
        val nd = nps[0].nodes[1]
        val a = (nd.p - nd.hin).unit()
        val b = (nd.hout - nd.p).unit()
        assertTrue((a dot b) > 0.999)
        Nodes.makeCorner(nps[0], 1)
        assertTrue(Nodes.same(nd.hin, nd.p) && Nodes.same(nd.hout, nd.p))
    }
}
