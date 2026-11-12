package com.zdearo.vibealarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationRampTest {
    private val p = VibrationRamp.build(40, 2)
    private val gaps = p.timings.filterIndexed { i, _ -> i % 2 == 0 }
    private val pulses = p.timings.filterIndexed { i, _ -> i % 2 == 1 }
    private val amps = p.amplitudes.filterIndexed { i, _ -> i % 2 == 1 }

    @Test fun arraysHaveSameSize() = assertEquals(p.timings.size, p.amplitudes.size)

    @Test fun amplitudeStaysInRangeAndEndsAtMax() {
        assertTrue(p.amplitudes.all { it in 0..255 })
        assertEquals(255, amps.last())
    }

    @Test fun intensityAndDurationGrowWhileGapsShrink() {
        assertTrue(amps.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(pulses.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(gaps.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test fun repeatsFromLastGapSoMaxPulseLoops() {
        assertEquals(p.timings.size - 2, p.repeatIndex)
        assertEquals(0, p.amplitudes[p.repeatIndex])
    }

    @Test fun intensityCapsThePeak() {
        assertEquals(120, VibrationRamp.build(40, 0).amplitudes.max())
        assertEquals(200, VibrationRamp.build(40, 1).amplitudes.max())
    }

    @Test fun resumeMidRampKeepsTheRestOfTheCurve() {
        // 4,0 s: a pausa de 3 s e o pulso de 150 ms acabaram; estamos dentro da 2ª pausa.
        val r = VibrationRamp.resume(p, 4_000)
        assertEquals(p.timings[2] - (4_000 - p.timings[0] - p.timings[1]), r.timings[0])
        assertEquals(p.timings.size - 2, r.timings.size)
        assertEquals(p.repeatIndex - 2, r.repeatIndex)
        assertTrue(r.amplitudes.contentEquals(p.amplitudes.copyOfRange(2, p.amplitudes.size)))
    }

    @Test fun resumeAfterTheRampLoopsOnlyTheMaxCycle() {
        val r = VibrationRamp.resume(p, 10 * 60_000L)
        assertEquals(2, r.timings.size)
        assertEquals(0, r.repeatIndex)
        assertEquals(255, r.amplitudes.last())
    }

    @Test fun resumeInsideTheFinalPulseFinishesItThenLoops() {
        val endOfRamp = p.timings.sum()
        val r = VibrationRamp.resume(p, endOfRamp - 100) // faltam 100 ms do último pulso
        assertEquals(100L, r.timings[0])
        assertEquals(1, r.repeatIndex)
        assertEquals(0, r.amplitudes[r.repeatIndex])
    }

    @Test fun quietOnlyInsideGapsAwayFromTheMotor() {
        // A 1ª pausa vai de 0 a 3000 ms: o começo (reverberação) e o fim ficam de fora.
        assertTrue(!VibrationRamp.isQuiet(p, 100))
        assertTrue(VibrationRamp.isQuiet(p, 1_500))
        assertTrue(!VibrationRamp.isQuiet(p, 2_950))
        assertTrue(!VibrationRamp.isQuiet(p, p.timings[0] + 50)) // durante o primeiro pulso
    }

    @Test fun everyGapKeepsARealQuietWindowEvenAtTheMaxCycle() {
        val total = p.timings.sum()
        // No ciclo final, a pausa precisa de folga útil (> 0,4 s) para medir.
        val quietMs = (total until total + 4_000).count { VibrationRamp.isQuiet(p, it) }
        assertTrue(quietMs in 1_000..2_400) // 2 ciclos de ~0,8 s
    }

    @Test fun rampLengthFollowsRequestedSeconds() {
        val total = { s: Int -> VibrationRamp.build(s, 1).timings.sum() }
        assertTrue(total(15) < total(40) && total(40) < total(120))
        assertTrue(total(40) in 30_000L..50_000L)
    }
}
