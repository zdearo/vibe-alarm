package com.zdearo.vibealarm

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Mede quanto tempo a pessoa caminha, pelo acelerômetro, olhando só as pausas da vibração.
 *
 * O motor do próprio relógio balança o acelerômetro e cega o sensor de passos da Samsung, por isso só
 * amostras em "janela silenciosa" ([quiet] = true) contam. Cada janela é "ativa" quando a variação média
 * da aceleração nela passa de [threshold].
 *
 * O resultado é um crédito em segundos, que se comporta em tempo real:
 *  - janela ativa: sobe o tempo decorrido desde a janela anterior (o 1º segundo ganha um bônus, porque o
 *    sensor só enxerga a caminhada ~1 s depois que ela começa);
 *  - janela fraca até [graceMs] depois da última ativa: o número para de subir, e o tempo fica "pendente".
 *    Se a próxima janela for ativa, o pendente é devolvido (passos suaves, braço parado, alternam janelas
 *    fortes e fracas); se for fraca de novo, o pendente se perde;
 *  - fraca fora da tolerância: o crédito desce no mesmo ritmo. Parar de verdade para o número e logo o faz
 *    descer, e virar na cama (rajadas isoladas) sobe um pouco e volta a zero, sem acumular.
 */
class MotionDetector(
    private val threshold: Float = THRESHOLD,
    graceMs: Long = GRACE_MS,
    private val minSamples: Int = MIN_SAMPLES,
) {
    private val graceNs = graceMs * 1_000_000

    /** Segundos de caminhada acumulados. */
    val seconds: Int get() = credit.toInt()

    var onWindow: ((mean: Float, seconds: Int) -> Unit)? = null

    /** Sinais por janela, só para o log de calibração (o giro é o único que entra na conta). */
    class Features(val vertical: Float, val horizontal: Float, val gyro: Float)

    var onFeatures: ((Features) -> Unit)? = null

    private var credit = 0.0
    private var pending = 0.0 // tempo de janelas fracas ainda sem confirmação
    private var gravity = 0f
    private val gVec = FloatArray(3)
    private var gVecInit = false
    private var sumVert = 0f
    private var sumHoriz = 0f
    private var sumGyro = 0f
    private var nGyro = 0
    private var sum = 0f
    private var n = 0
    private var windowStart = 0L
    private var lastT = 0L
    private var prevEnd = -1L
    private var lastActiveEnd = Long.MIN_VALUE / 2

    /** Alimenta uma amostra (m/s², timestamp em ns); [quiet] diz se o motor está parado. Devolve [seconds]. */
    fun onSample(tNanos: Long, x: Float, y: Float, z: Float, quiet: Boolean): Int {
        if (!quiet) {
            closeWindow()
            return seconds
        }
        val m = sqrt(x * x + y * y + z * z)
        // A gravidade só é estimada com o motor parado: a vibração a distorceria.
        gravity = if (gravity == 0f) m else gravity + ALPHA * (m - gravity)
        if (n == 0) windowStart = tNanos
        lastT = tNanos
        sum += abs(m - gravity)
        n++
        // Aceleração sem a gravidade, separada em componente vertical (ao longo da gravidade) e horizontal.
        if (!gVecInit) {
            gVec[0] = x; gVec[1] = y; gVec[2] = z; gVecInit = true
        } else {
            gVec[0] += ALPHA * (x - gVec[0]); gVec[1] += ALPHA * (y - gVec[1]); gVec[2] += ALPHA * (z - gVec[2])
        }
        val dx = x - gVec[0]; val dy = y - gVec[1]; val dz = z - gVec[2]
        val gn = sqrt(gVec[0] * gVec[0] + gVec[1] * gVec[1] + gVec[2] * gVec[2])
        if (gn > 0f) {
            val v = (dx * gVec[0] + dy * gVec[1] + dz * gVec[2]) / gn
            sumVert += abs(v)
            sumHoriz += sqrt(maxOf(0f, dx * dx + dy * dy + dz * dz - v * v))
        }
        return seconds
    }

    /** Giroscópio (rad/s); também só vale em janela silenciosa. */
    fun onGyro(wx: Float, wy: Float, wz: Float, quiet: Boolean) {
        if (!quiet) return
        sumGyro += sqrt(wx * wx + wy * wy + wz * wz)
        nGyro++
    }

    private fun closeWindow() {
        if (n >= minSamples) {
            val mean = sum / n
            // Tempo coberto por esta janela: desde o fim da anterior (ou a duração dela, se for a 1ª).
            val dt = min(if (prevEnd < 0) lastT - windowStart else lastT - prevEnd, MAX_DT_NS) / 1e9
            val gyro = if (nGyro > 0) sumGyro / nGyro else 0f
            // Girar o braço dá a mesma aceleração que andar, mas faz o pulso girar bem mais rápido
            // (medido: andando 0,3–1,2 rad/s; em círculos 1,4–3,4). Quanto mais gira, menos a janela vale.
            val w = if (nGyro == 0) 1.0 else ((GYRO_ZERO - gyro) / (GYRO_ZERO - GYRO_FULL)).toDouble().coerceIn(0.0, 1.0)
            if (mean > threshold) {
                if (credit == 0.0 && pending == 0.0) credit += BONUS_S * w
                credit += (pending + dt) * w // devolve o que ficou segurando: era caminhada mesmo
                pending = 0.0
                lastActiveEnd = lastT
            } else if ((credit > 0 || pending > 0) && lastT - lastActiveEnd <= graceNs) {
                pending += dt // 1ª fraca depois de uma forte: o número para de subir, mas ainda não desce
            } else {
                credit = maxOf(0.0, credit - dt) // parado de verdade: desce em tempo real
                pending = 0.0
            }
            prevEnd = lastT
            onFeatures?.invoke(Features(sumVert / n, sumHoriz / n, gyro))
            onWindow?.invoke(mean, seconds)
        }
        sum = 0f
        n = 0
        sumVert = 0f
        sumHoriz = 0f
        sumGyro = 0f
        nGyro = 0
    }

    companion object {
        // Medido no relógio: parado 0,03–0,39 m/s² (mexidas isoladas); andando 0,25–2,6 (braço mais parado dá o
        // valor baixo). O log "janela" mostra a média de cada pausa para recalibrar.
        const val THRESHOLD = 0.40f
        const val GRACE_MS = 2_500L
        const val MIN_SAMPLES = 15 // ~0,3 s a 50 Hz
        const val GYRO_FULL = 1.2f // até aqui a janela vale por inteiro (rad/s)
        const val GYRO_ZERO = 2.6f // daqui para cima não vale nada (braço girando)
        private const val BONUS_S = 1.0
        private const val MAX_DT_NS = 4_000_000_000L // um buraco longo de leitura não vale como caminhada
        private const val ALPHA = 0.05f
    }
}
