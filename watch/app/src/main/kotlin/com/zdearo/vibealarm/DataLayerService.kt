package com.zdearo.vibealarm

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/** Recebe a lista de alarmes do celular em /alarms, guarda, reagenda e confirma em /ack. */
class DataLayerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (e in events) {
            if (e.type != DataEvent.TYPE_CHANGED || e.dataItem.uri.path != "/alarms") continue
            val map = DataMapItem.fromDataItem(e.dataItem).dataMap
            val json = map.getString("json") ?: continue
            val count = try {
                Alarm.listFromJson(json).size
            } catch (ex: Exception) {
                Log.w(TAG, "Lista inválida ignorada", ex)
                continue // lista antiga continua valendo
            }
            AlarmStore.save(this, json)
            AlarmScheduler.reschedule(this)
            sendAck(map.getLong("ts"), count)
        }
    }

    private fun sendAck(sentTs: Long, count: Int) {
        val power = getSystemService(PowerManager::class.java)
        val level = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val req = PutDataMapRequest.create("/ack").apply {
            dataMap.putLong("sentTs", sentTs) // eco do carimbo do celular: ele compara com o que enviou
            dataMap.putLong("ts", System.currentTimeMillis())
            dataMap.putInt("count", count)
            dataMap.putInt("level", level)
            dataMap.putBoolean("activity", granted(Manifest.permission.ACTIVITY_RECOGNITION))
            dataMap.putBoolean("powersave", power.isPowerSaveMode) // economia de energia ligada limita o app em segundo plano
            dataMap.putBoolean("overlay", Settings.canDrawOverlays(this@DataLayerService)) // sem isso a tela "Despertando" não abre
            dataMap.putBoolean("notifications", getSystemService(NotificationManager::class.java).areNotificationsEnabled())
            dataMap.putBoolean("scan", granted(Manifest.permission.BLUETOOTH_SCAN)) // só a trava pelo celular depende disso
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(req)
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "DataLayerService"
    }
}
