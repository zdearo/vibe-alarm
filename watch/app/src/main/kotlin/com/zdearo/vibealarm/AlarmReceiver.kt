package com.zdearo.vibealarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // startForegroundService é assíncrono: quando onReceive termina, o wake lock do AlarmManager cai e o relógio
        // pode voltar a dormir antes de o serviço subir (medido: 45 s de atraso com o relógio em sono profundo).
        // Este wake lock cobre esse intervalo; o serviço pega o dele em seguida e o deste expira sozinho.
        context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vibealarm:start")
            .acquire(START_LOCK_MS)
        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).putExtra(AlarmService.EXTRA_ID, intent.getStringExtra(AlarmService.EXTRA_ID)),
        )
        AlarmScheduler.reschedule(context)
    }

    private companion object {
        const val START_LOCK_MS = 20_000L
    }
}
