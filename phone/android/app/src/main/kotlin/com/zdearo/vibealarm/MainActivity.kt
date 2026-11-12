package com.zdearo.vibealarm

import android.net.Uri
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
                                "count" to m.getInt("count"),
                                "level" to m.getInt("level"),
                                "activity" to m.getBoolean("activity"),
                                "battery" to m.getBoolean("battery"),
                            )
                        }
                        buffer.release()
                        result.success(mapOf("nodes" to names, "ack" to ack))
                    }
            }
    }
}
