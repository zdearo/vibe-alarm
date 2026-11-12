package com.zdearo.vibealarm

/** Padrão de vibração que cresce em intensidade, duração do pulso e frequência (pausas menores). */
object VibrationRamp {
    class Pattern(val timings: LongArray, val amplitudes: IntArray, val repeatIndex: Int)

    // Amplitude máxima por intensidade do alarme: suave, normal, intenso.
    private val PEAKS = intArrayOf(120, 200, 255)

    // Pausa mínima do ciclo máximo: é a janela em que dá para "escutar" o movimento sem o motor atrapalhar.
    private const val MIN_GAP_MS = 1200f

    // Cada degrau (pausa + pulso) dura ~2,5 s em média nesta curva.
    private const val AVG_STEP_MS = 2525

    /** Sobe em cerca de [rampSeconds]; depois repete o último pulso (máximo) até parar. */
    fun build(rampSeconds: Int, intensity: Int): Pattern {
        val steps = (rampSeconds * 1000 / AVG_STEP_MS).coerceAtLeast(5)
        val peak = PEAKS[intensity.coerceIn(0, 2)]
        val timings = LongArray(steps * 2)
        val amplitudes = IntArray(steps * 2)
        for (i in 0 until steps) {
            val f = i / (steps - 1f)
            timings[i * 2] = lerp(3000f, MIN_GAP_MS, f).toLong() // pausa (amplitude 0)
            timings[i * 2 + 1] = lerp(150f, 700f, f).toLong() // pulso
            amplitudes[i * 2 + 1] = lerp(30f, peak.toFloat(), f).toInt()
        }
        return Pattern(timings, amplitudes, repeatIndex = steps * 2 - 2)
    }

    /**
     * Mesmo padrão, mas começando [elapsedMs] depois do início. Serve para religar a vibração no ponto
     * em que estava depois que o sistema a cancela, em vez de recomeçar do silêncio.
     */
    fun resume(p: Pattern, elapsedMs: Long): Pattern {
        var t = 0L
        for (i in p.timings.indices) {
            if (elapsedMs >= t + p.timings[i]) {
                t += p.timings[i]
                continue
            }
            val rest = t + p.timings[i] - elapsedMs
            if (i <= p.repeatIndex) {
                val timings = p.timings.copyOfRange(i, p.timings.size).also { it[0] = rest }
                return Pattern(timings, p.amplitudes.copyOfRange(i, p.amplitudes.size), p.repeatIndex - i)
            }
            // No pulso final do ciclo máximo: termina o pulso e volta ao ciclo (pausa + pulso).
            val last = p.timings.size - 1
            return Pattern(
                longArrayOf(rest, p.timings[last - 1], p.timings[last]),
                intArrayOf(p.amplitudes[last], 0, p.amplitudes[last]),
                repeatIndex = 1,
            )
        }
        // Já passou da subida: só o ciclo máximo, em loop.
        val r = p.repeatIndex
        return Pattern(p.timings.copyOfRange(r, p.timings.size), p.amplitudes.copyOfRange(r, p.amplitudes.size), repeatIndex = 0)
    }

    // Folgas para a latência do motor: o começo da pausa ainda "toca" (reverbera) e o fim pode adiantar.
    private const val GUARD_START_MS = 300L
    private const val GUARD_END_MS = 100L

    /**
     * Instante [elapsedMs] (desde o início do padrão) cai numa pausa limpa, sem o motor? O movimento medido
     * fora dessas janelas é do próprio relógio vibrando, não da pessoa.
     */
    fun isQuiet(p: Pattern, elapsedMs: Long): Boolean {
        val total = p.timings.sum()
        var e = elapsedMs
        var from = 0
        if (e >= total) { // depois da subida só o ciclo final (pausa + pulso) se repete
            from = p.repeatIndex
            e = (e - total) % p.timings.copyOfRange(from, p.timings.size).sum()
        }
        var t = 0L
        for (i in from until p.timings.size) {
            val end = t + p.timings[i]
            if (e < end) return p.amplitudes[i] == 0 && e - t >= GUARD_START_MS && end - e > GUARD_END_MS
            t = end
        }
        return false
    }

    private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
}
