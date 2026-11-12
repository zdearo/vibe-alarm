package com.zdearo.vibealarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MotionDetectorTest {
    private val hz = 50
    private var t = 0L // relógio simulado, em ns

    /** Uma janela de [ms] a 50 Hz: oscilação de 2 Hz de amplitude [amp] sobre a gravidade. */
    private fun window(d: MotionDetector, ms: Int, amp: Float, quiet: Boolean, gyro: Float = 0f): Int {
        var s = 0
        for (i in 0 until ms * hz / 1000) {
            t += 1_000_000_000L / hz
            s = d.onSample(t, 0f, 0f, 9.81f + amp * sin(2 * PI * 2 * i / hz).toFloat(), quiet)
            d.onGyro(gyro, 0f, 0f, quiet)
        }
        return s
    }

    /** [cycles] ciclos de pausa de 1 s (silenciosa) + pulso de 1 s (motor ligado, com [motorAmp] de ruído). Cada ciclo dura 2 s. */
    private fun cycles(d: MotionDetector, cycles: Int, quietAmp: Float, motorAmp: Float, gyro: Float = 0f): Int {
        var s = 0
        repeat(cycles) {
            window(d, 1000, quietAmp, quiet = true, gyro = gyro)
            s = window(d, 1000, motorAmp, quiet = false)
        }
        return s
    }

    @Test fun deviceAtRestNeverCounts() {
        assertEquals(0, cycles(MotionDetector(), 20, quietAmp = 0f, motorAmp = 0f))
    }

    @Test fun motorNoiseOutsideQuietWindowsIsIgnored() {
        // O relógio parado, só o motor balançando forte durante os pulsos: não pode contar.
        assertEquals(0, cycles(MotionDetector(), 20, quietAmp = 0f, motorAmp = 5f))
    }

    @Test fun walkingAccumulatesAboutOneSecondPerSecondWithAStartBonus() {
        val s = cycles(MotionDetector(), 12, quietAmp = 3f, motorAmp = 5f) // 24 s andando
        assertTrue("seconds=$s", s in 22..26)
    }

    @Test fun walkingWithLittleWristRotationCountsInFull() {
        val s = cycles(MotionDetector(), 12, quietAmp = 3f, motorAmp = 5f, gyro = 0.6f)
        assertTrue("seconds=$s", s in 22..26)
    }

    @Test fun movingTheArmInCirclesHasTheSameAccelerationButFastWristRotationAndDoesNotCount() {
        val s = cycles(MotionDetector(), 25, quietAmp = 3f, motorAmp = 5f, gyro = 3.0f) // 50 s girando
        assertTrue("seconds=$s", s <= 1)
    }

    @Test fun partialRotationCountsPartially() {
        val s = cycles(MotionDetector(), 12, quietAmp = 3f, motorAmp = 5f, gyro = 1.9f) // peso ~0,5
        assertTrue("seconds=$s", s in 8..16)
    }

    @Test fun oneWeakWindowInTheMiddleOfWalkingStillCounts() {
        val d = MotionDetector()
        cycles(d, 6, 3f, 5f)
        cycles(d, 1, 0f, 5f) // uma janela fraca (braço parado)
        assertTrue(cycles(d, 6, 3f, 5f) >= 24)
    }

    @Test fun stoppingHoldsTheCountAtOnceThenLowersItInRealTime() {
        val d = MotionDetector()
        val walking = cycles(d, 8, 3f, 5f) // ~17 s
        val afterOne = cycles(d, 1, 0f, 0f) // 1ª janela parada: para de subir, não desce ainda
        val afterTwo = cycles(d, 1, 0f, 0f)
        val afterThree = cycles(d, 1, 0f, 0f)
        assertEquals(walking, afterOne)
        assertTrue("$afterTwo < $afterOne", afterTwo < afterOne)
        assertTrue("$afterThree < $afterTwo", afterThree < afterTwo)
        assertTrue(afterThree > 0) // desceu, mas não zerou de uma vez
    }

    @Test fun aWeakWindowIsRefundedWhenTheNextOneIsStrong() {
        val d = MotionDetector()
        cycles(d, 6, 3f, 5f)
        val held = cycles(d, 1, 0f, 5f) // fraca: segura
        val after = cycles(d, 1, 3f, 5f) // forte: devolve a fraca e soma a sua
        assertEquals(held + 4, after) // 2 s da fraca devolvida + 2 s da forte
    }

    @Test fun walkingAgainAfterAStopContinuesFromTheLoweredCount() {
        val d = MotionDetector()
        cycles(d, 8, 3f, 5f)
        val low = cycles(d, 3, 0f, 0f)
        assertTrue(cycles(d, 3, 3f, 5f) > low)
    }

    @Test fun aLongStopDrainsToZero() {
        val d = MotionDetector()
        cycles(d, 6, 3f, 5f)
        assertEquals(0, cycles(d, 12, 0f, 0f))
    }

    @Test fun isolatedBurstsInBedNeverPileUp() {
        val d = MotionDetector()
        var peak = 0
        d.onWindow = { _, s -> peak = maxOf(peak, s) }
        repeat(6) { // uma rajada e quatro janelas paradas (10 s), repetido
            cycles(d, 1, quietAmp = 3f, motorAmp = 0f)
            cycles(d, 4, quietAmp = 0f, motorAmp = 0f)
        }
        assertTrue("pico=$peak", peak <= 6)
        assertEquals(0, d.seconds)
    }

    @Test fun veryShortWindowsAreDiscarded() {
        val d = MotionDetector()
        repeat(20) {
            window(d, 100, 5f, quiet = true) // 5 amostras < mínimo
            window(d, 100, 0f, quiet = false)
        }
        assertEquals(0, d.seconds)
    }
}
