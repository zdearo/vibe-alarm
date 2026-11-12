package com.zdearo.vibealarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionGateTest {
    private fun gateWithBaseline(rssi: Float = -60f) = ConnectionGate().also { g -> repeat(3) { g.onWindow(rssi) } }

    @Test fun withoutAnySignalTheGateDoesNotApplyAndAlarmStopsByWalkingAlone() {
        val g = ConnectionGate()
        repeat(10) { g.onWindow(null) }
        assertFalse(g.blocks())
        assertEquals("sem-linha-de-base", g.state)
    }

    @Test fun afterTheBaselineTheGateBlocksUntilTheConnectionVaries() {
        val g = gateWithBaseline()
        assertTrue(g.blocks())
        assertEquals("fechada", g.state)
    }

    @Test fun movingTheArmAroundTheSamePlaceNeverOpens() {
        val g = gateWithBaseline(-76f)
        // Só o braço: +11 dB no máximo, por várias janelas seguidas.
        listOf(6, 3, 9, 9, 6, 6, 7, 5, 5, 2, 2, -1, 4, 1, 3, 2, 8, 9, 10, 11, 9, 11, 11, 10, 8).forEach { g.onWindow(-76f + it) }
        assertTrue(g.blocks())
    }

    @Test fun walkingAwayOpensAfterThreeWindowsInARow() {
        val g = gateWithBaseline()
        g.onWindow(-72f) // 12 dB abaixo do ponto X
        g.onWindow(-73f)
        assertTrue(g.blocks()) // só 2 janelas seguidas
        g.onWindow(-74f) // 3ª janela seguida com 12 dB ou mais
        assertFalse(g.blocks())
    }

    @Test fun approachingTheSamePointOpensJustAsMuch() {
        val g = gateWithBaseline(-80f)
        repeat(3) { g.onWindow(-65f) } // +15 dB
        assertFalse(g.blocks())
    }

    @Test fun aSingleSpikeDoesNotOpen() {
        val g = gateWithBaseline()
        g.onWindow(-40f)
        g.onWindow(-60f)
        g.onWindow(-30f)
        g.onWindow(-60f)
        assertTrue(g.blocks())
    }

    @Test fun onceOpenItStaysOpenEvenIfThePersonReturns() {
        val g = gateWithBaseline()
        repeat(3) { g.onWindow(-80f) }
        assertFalse(g.blocks())
        repeat(5) { g.onWindow(-60f) }
        assertFalse(g.blocks())
    }

    @Test fun losingTheSignalForThreeWindowsCountsAsHavingMovedAway() {
        val g = gateWithBaseline()
        g.onWindow(null)
        g.onWindow(null)
        assertTrue(g.blocks())
        g.onWindow(null)
        assertFalse(g.blocks())
    }

    @Test fun aBriefSignalGapDoesNotOpen() {
        val g = gateWithBaseline()
        g.onWindow(null)
        g.onWindow(-60f)
        g.onWindow(null)
        g.onWindow(-60f)
        assertTrue(g.blocks())
    }
}
