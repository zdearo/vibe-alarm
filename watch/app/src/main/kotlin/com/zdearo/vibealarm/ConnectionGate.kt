package com.zdearo.vibealarm

import kotlin.math.abs

/**
 * Trava de liberação única pela conexão com o celular, que fica num ponto fixo.
 *
 * O ponto X é o sinal Bluetooth (RSSI) nas primeiras [BASE_WINDOWS] janelas com sinal, com a pessoa ainda no
 * lugar. A trava abre quando o sinal varia [dbThreshold] dB ou mais em relação a X (para mais ou para menos,
 * pois aproximar e afastar valem igual) por [windows] janelas seguidas, e então fica aberta. Mexer só o braço
 * no mesmo lugar não varia tanto; andar pelo cômodo varia.
 *
 * Sem sinal do celular (sem linha de base), a trava não vale e o alarme para só pela caminhada. Se o sinal some
 * por [windows] janelas seguidas depois da linha de base, conta como ter se afastado (e também abre).
 */
class ConnectionGate(private val dbThreshold: Float = DB_THRESHOLD, private val windows: Int = WINDOWS) {
    private val base = ArrayList<Float>()
    private var variationStreak = 0
    private var lostStreak = 0

    var open = false
        private set

    val hasBaseline: Boolean get() = base.size >= BASE_WINDOWS

    /** RSSI médio do ponto X, ou NaN antes de existir. */
    val baseline: Float get() = if (base.isEmpty()) Float.NaN else base.average().toFloat()

    /** A trava está impedindo o alarme de parar? Só se há linha de base e ela ainda não abriu. */
    fun blocks(): Boolean = hasBaseline && !open

    val state: String get() = when {
        !hasBaseline -> "sem-linha-de-base"
        open -> "aberta"
        else -> "fechada"
    }

    /** [rssi] é a média da janela, ou null se nenhuma amostra do celular chegou nela. */
    fun onWindow(rssi: Float?) {
        if (open) return
        if (rssi == null) {
            variationStreak = 0
            if (hasBaseline && ++lostStreak >= windows) open = true
            return
        }
        lostStreak = 0
        if (!hasBaseline) {
            base.add(rssi)
            return
        }
        variationStreak = if (abs(rssi - baseline) >= dbThreshold) variationStreak + 1 else 0
        if (variationStreak >= windows) open = true
    }

    companion object {
        // Medido: só o braço variou até 11 dB; andando, 15–16 janelas seguidas acima de 12 dB (uma rodada de cada).
        const val DB_THRESHOLD = 12f
        const val WINDOWS = 3
        const val BASE_WINDOWS = 3
    }
}
