package com.zdearo.vibealarm

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.UserActivityInfo
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * EXPERIMENTAL: registra cada mudança do estado de atividade do Health Services (dormindo, passivo, exercício) com a
 * hora em que chegou e a hora em que o estado mudou, para medir a velocidade da detecção de sono. O arquivo
 * (filesDir/sleep_log.txt) é lido por adb. O Health Services aciona este serviço sem o app estar aberto.
 */
class SleepStateService : PassiveListenerService() {
    override fun onUserActivityInfoReceived(info: UserActivityInfo) {
        val line = "chegou=%s estado=%s mudou_em=%s".format(
            LocalDateTime.now().withNano(0),
            info.userActivityState,
            info.stateChangeTime.atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0),
        )
        Log.d("SLEEPLOG", line)
        File(filesDir, "sleep_log.txt").appendText(line + "\n")
    }
}
