package com.zdearo.vibealarm

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.PassiveListenerConfig

/**
 * DIAGNÓSTICO (temporário): registra o [SleepStateService] no Health Services para ele receber as mudanças de estado
 * (dormindo/passivo) em segundo plano. Abrir só por adb: `am start -n com.zdearo.vibealarm/.HealthDiagActivity`.
 * O registro persiste depois que a tela fecha; um reinício do relógio o apaga e exige abrir de novo.
 */
class HealthDiagActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = HealthServices.getClient(this).passiveMonitoringClient
        val config = PassiveListenerConfig.builder()
            .setDataTypes(emptySet())
            .setShouldUserActivityInfoBeRequested(true)
            .build()
        client.setPassiveListenerServiceAsync(SleepStateService::class.java, config)
        Log.d("HEALTHDIAG", "serviço de estado de sono registrado")
        window.decorView.postDelayed({ finish() }, 3_000)
    }
}
