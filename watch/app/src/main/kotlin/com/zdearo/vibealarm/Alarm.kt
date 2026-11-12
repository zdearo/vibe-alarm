package com.zdearo.vibealarm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Alarme vindo do celular. days usa java.time.DayOfWeek (1 = segunda ... 7 = domingo);
 * vazio significa "toca uma vez", no instante absoluto [at].
 */
data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val label: String,
    val enabled: Boolean,
    val intensity: Int, // 0 suave, 1 normal, 2 intenso
    val rampSeconds: Int,
    val walkSeconds: Int, // segundos de caminhada para encerrar; não há botão de parar
    val timeoutMin: Int,
    val at: Long,
) {
    companion object {
        /** Lança se o JSON for inválido: quem chama não deve gravar nada nesse caso. */
        fun listFromJson(json: String): List<Alarm> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }

        // Valores fora da faixa são limitados: a lista cruza a Data Layer e não pode quebrar o agendamento.
        private fun fromJson(o: JSONObject): Alarm {
            val days = o.getJSONArray("days")
            return Alarm(
                id = o.getString("id"),
                hour = o.getInt("hour").coerceIn(0, 23),
                minute = o.getInt("minute").coerceIn(0, 59),
                days = (0 until days.length()).map { days.getInt(it) }.filter { it in 1..7 }.toSet(),
                label = o.optString("label"),
                enabled = o.getBoolean("enabled"),
                intensity = o.optInt("intensity", 1).coerceIn(0, 2),
                rampSeconds = o.optInt("rampSeconds", 40).coerceIn(15, 120),
                // Listas antigas tinham "steps" (~2 s de caminhada por passo). Mínimo 5 s: sem meta o alarme só pararia pelo limite.
                walkSeconds = o.optInt("walkSeconds", o.optInt("steps", 10) * 2).coerceIn(5, 120),
                timeoutMin = o.optInt("timeoutMin", 10).coerceIn(1, 30),
                at = o.optLong("at", 0),
            )
        }
    }
}
