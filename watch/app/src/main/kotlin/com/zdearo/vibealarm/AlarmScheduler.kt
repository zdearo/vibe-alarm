package com.zdearo.vibealarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object AlarmScheduler {
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** Reagenda o próximo disparo a partir da lista guardada. Chamar após sincronizar, disparar, pular e reiniciar. */
    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val next = Schedule.next(AlarmStore.alarms(context), System.currentTimeMillis(), AlarmStore.skipUntil(context))
        val fire = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, AlarmReceiver::class.java).putExtra(AlarmService.EXTRA_ID, next?.alarm?.id),
            FLAGS,
        )
        if (next == null) {
            am.cancel(fire)
            return
        }
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), FLAGS)
        // setAlarmClock é isento de Doze e permite iniciar o foreground service em segundo plano.
        am.setAlarmClock(AlarmManager.AlarmClockInfo(next.at, show), fire)
    }
}
