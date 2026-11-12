package com.zdearo.vibealarm

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.util.UUID

/**
 * EXPERIMENTAL: quando o alarme do relógio começa, ele manda /beacon/start. O sistema acorda este serviço (sem
 * app aberto nem serviço em primeiro plano) e o celular passa a anunciar um farol Bluetooth LE; o relógio mede o
 * RSSI (força do sinal) para saber se a pessoa se afastou do celular, que fica num ponto fixo.
 */
class BeaconService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "mensagem ${event.path} recebida (processo vivo há ${SystemClock.elapsedRealtime() - processStart} ms)")
        when (event.path) {
            "/beacon/start" -> Beacon.start(applicationContext)
            "/beacon/stop" -> Beacon.stop()
        }
    }

    private companion object {
        const val TAG = "Beacon"
        val processStart = SystemClock.elapsedRealtime()
    }
}

/** Fica num objeto para o anúncio sobreviver ao fim do serviço enquanto o processo existir. */
object Beacon {
    private const val TAG = "Beacon"

    // Mesmo UUID do relógio (watch/.../Beacon.kt).
    private val UUID_BEACON: UUID = UUID.fromString("7f0c0001-5a1d-4b6e-9d52-8a1f00ab51e5")
    private const val MAX_MS = 180_000 // o Android limita o anúncio a 180 s; depois disso para sozinho

    private var advertiser: BluetoothLeAdvertiser? = null
    private var startedAt = 0L
    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settings: AdvertiseSettings) {
            Log.d(TAG, "anunciando")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "falha ao anunciar: $errorCode")
            advertiser = null
        }
    }

    fun start(context: Context) {
        // Depois dos 180 s o anúncio para sozinho, mas `advertiser` continua preenchido: sem checar o tempo, o próximo start era ignorado.
        if (advertiser != null && SystemClock.elapsedRealtime() - startedAt < MAX_MS) return
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "sem permissão BLUETOOTH_ADVERTISE")
            return
        }
        advertiser = context.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeAdvertiser ?: return
        startedAt = SystemClock.elapsedRealtime()
        advertiser?.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .setTimeout(MAX_MS)
                .build(),
            AdvertiseData.Builder().setIncludeDeviceName(false).addServiceUuid(ParcelUuid(UUID_BEACON)).build(),
            callback,
        )
    }

    fun stop() {
        advertiser?.stopAdvertising(callback)
        advertiser = null
        Log.d(TAG, "parou de anunciar")
    }
}
