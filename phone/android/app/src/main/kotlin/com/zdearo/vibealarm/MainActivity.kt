package com.zdearo.vibealarm

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/** Ponte entre o Flutter e a Data Layer: envia a lista em /alarms e lê a confirmação do relógio em /ack. */
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "vibealarm/sync").setMethodCallHandler { call, result ->
            when (call.method) {
                "send" -> send(call.argument<String>("json")!!, call.argument<Number>("ts")!!.toLong(), result)
                "status" -> status(result)
                "openBluetooth" -> {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    result.success(null)
                }
                "openOnWatch" -> openOnWatch(call.argument<String>("kind")!!, result)
                else -> result.notImplemented()
            }
        }
    }

    private fun send(json: String, ts: Long, result: MethodChannel.Result) {
        val req = PutDataMapRequest.create("/alarms").apply {
            dataMap.putString("json", json)
            dataMap.putLong("ts", ts) // o relógio devolve esse carimbo no /ack
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(req)
            .addOnSuccessListener { result.success(null) }
            .addOnFailureListener { result.error("SEND", it.message, null) }
    }

    /** Abre no relógio o MainActivity do Pulso com pulso://fix/<tipo>; ele leva à tela de ajuste certa. */
    private fun openOnWatch(kind: String, result: MethodChannel.Result) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnFailureListener { result.error("OPEN", it.message, null) }
            .addOnSuccessListener { nodes ->
                val node = nodes.firstOrNull() ?: return@addOnSuccessListener result.error("OPEN", "Relógio não encontrado", null)
                val intent = Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(Uri.parse("pulso://fix/$kind"))
                val done = RemoteActivityHelper(this, ContextCompat.getMainExecutor(this)).startRemoteActivity(intent, node.id)
                done.addListener({
                    try {
                        done.get()
                        result.success(null)
                    } catch (e: Exception) {
                        result.error("OPEN", "O relógio não abriu a tela", null)
                    }
                }, ContextCompat.getMainExecutor(this))
            }
    }

    private fun status(result: MethodChannel.Result) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnFailureListener { result.error("STATUS", it.message, null) }
            .addOnSuccessListener { nodes ->
                val names = nodes.map { it.displayName }
                Wearable.getDataClient(this).getDataItems(Uri.parse("wear://*/ack"))
                    .addOnFailureListener { result.success(mapOf("nodes" to names, "ack" to null)) }
                    .addOnSuccessListener { buffer ->
                        val ack = buffer.firstOrNull()?.let { item ->
                            val m = DataMapItem.fromDataItem(item).dataMap
                            mapOf(
                                "sentTs" to m.getLong("sentTs"),
                                "ts" to m.getLong("ts"),
                                "count" to m.getInt("count"),
                                "level" to m.getInt("level"),
                                "activity" to m.getBoolean("activity"),
                                "powersave" to if (m.containsKey("powersave")) m.getBoolean("powersave") else null,
                                // Relógio com versão antiga não manda estes: null = desconhecido, não "negado".
                                "overlay" to if (m.containsKey("overlay")) m.getBoolean("overlay") else null,
                                "notifications" to if (m.containsKey("notifications")) m.getBoolean("notifications") else null,
                                "scan" to if (m.containsKey("scan")) m.getBoolean("scan") else null,
                                "charging" to if (m.containsKey("charging")) m.getBoolean("charging") else null,
                            )
                        }
                        buffer.release()
                        result.success(mapOf("nodes" to names, "ack" to ack))
                    }
            }
    }
}
