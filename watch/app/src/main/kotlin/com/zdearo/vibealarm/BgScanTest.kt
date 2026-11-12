package com.zdearo.vibealarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EXPERIMENTAL: testa se o relógio lê o farol do celular a cada 30 s em segundo plano (AlarmManager + varredura de
 * 8 s), sem serviço em primeiro plano. Cada rodada grava uma linha em filesDir/bgscan_log.txt (lida por run-as).
 * Iniciar: am broadcast -n com.zdearo.vibealarm/.BgScanTest -a com.zdearo.vibealarm.BGSCAN_START
 * Remover depois do teste.
 */
class BgScanTest : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deadline = if (intent.action == ACTION_START) {
            SystemClock.elapsedRealtime() + DURATION_MS
        } else {
            intent.getLongExtra(EXTRA_DEADLINE, 0L)
        }
        val pending = goAsync()
        if (SystemClock.elapsedRealtime() >= deadline) {
            log(context, "fim do teste")
            pending.finish()
            return
        }
        schedule(context, deadline)
        // Reanuncia o farol no celular: o anúncio dele para sozinho depois de 180 s.
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { Wearable.getMessageClient(context).sendMessage(it.id, "/beacon/start", ByteArray(0)) }
        }
        scan(context, pending)
    }

    private fun scan(context: Context, pending: BroadcastReceiver.PendingResult) {
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            log(context, "sem varredura (Bluetooth desligado ou sem permissão)")
            pending.finish()
            return
        }
        val rssis = mutableListOf<Int>()
        var failed = 0
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                rssis += result.rssi
            }
            override fun onScanFailed(errorCode: Int) {
                failed = errorCode
            }
        }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(Beacon.UUID)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { scanner.stopScan(callback) }
            val resumo = if (rssis.isEmpty()) "nenhum sinal" else "n=${rssis.size} rssi=${rssis.minOrNull()}..${rssis.maxOrNull()} média=${rssis.average().toInt()}"
            log(context, "varredura $resumo${if (failed != 0) " falha=$failed" else ""}")
            pending.finish()
        }, SCAN_MS)
    }

    private fun schedule(context: Context, deadline: Long) {
        val intent = Intent(context, BgScanTest::class.java)
            .setAction(ACTION_TICK)
            .putExtra(EXTRA_DEADLINE, deadline)
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + PERIOD_MS, pi)
    }

    private fun log(context: Context, msg: String) {
        val line = "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} $msg"
        Log.d(TAG, line)
        File(context.filesDir, "bgscan_log.txt").appendText(line + "\n")
    }

    private companion object {
        const val TAG = "BGSCAN"
        const val ACTION_START = "com.zdearo.vibealarm.BGSCAN_START"
        const val ACTION_TICK = "com.zdearo.vibealarm.BGSCAN_TICK"
        const val EXTRA_DEADLINE = "deadline"
        const val PERIOD_MS = 30_000L
        const val SCAN_MS = 8_000L
        const val DURATION_MS = 10 * 60_000L
    }
}
