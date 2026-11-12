package com.zdearo.vibealarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reagenda depois de reiniciar ou de mudar hora/fuso: o AlarmManager perde os alarmes no reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AlarmScheduler.reschedule(context)
}
