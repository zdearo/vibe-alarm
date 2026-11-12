package com.zdearo.vibealarm

import java.time.Instant
import java.time.ZoneId

object Schedule {
    class Next(val alarm: Alarm, val at: Long)

    /** Próximo disparo entre os alarmes ativos; [skipUntil] exclui tudo até esse instante ("pular este"). */
    fun next(alarms: List<Alarm>, now: Long, skipUntil: Long, zone: ZoneId = ZoneId.systemDefault()): Next? {
        val after = maxOf(now, skipUntil)
        return alarms.filter { it.enabled }
            .mapNotNull { a -> nextFor(a, after, zone)?.let { Next(a, it) } }
            .minByOrNull { it.at }
    }

    private fun nextFor(a: Alarm, after: Long, zone: ZoneId): Long? {
        if (a.days.isEmpty()) return a.at.takeIf { it > after }
        val start = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        for (i in 0..7L) {
            val date = start.plusDays(i)
            if (date.dayOfWeek.value !in a.days) continue
            val t = date.atTime(a.hour, a.minute).atZone(zone).toInstant().toEpochMilli()
            if (t > after) return t
        }
        return null
    }
}
