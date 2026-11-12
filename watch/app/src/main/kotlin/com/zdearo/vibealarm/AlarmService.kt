package com.zdearo.vibealarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.ParcelUuid
import com.google.android.gms.wearable.Wearable
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Executa um alarme: vibra em rampa, acende a tela e mede quanto tempo a pessoa caminha. Não há botão de
 * parar de propósito: só a caminhada encerra (e o limite de segurança, para o caso de o sensor falhar).
 *
 * O sensor de passos da Samsung não serve aqui: precisa de ~12 s de caminhada limpa para "aquecer" e fica
 * mudo enquanto o motor vibra. O acelerômetro lido nas pausas da vibração funciona (ver MotionDetector).
 */
class AlarmService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenOff: BroadcastReceiver? = null
    private var ramp: VibrationRamp.Pattern? = null
    private var startedAt = 0L
    private var goal = 0
    private var motion = MotionDetector()
    private var rssiSum = 0f
    private var rssiSumSq = 0f
    private var rssiN = 0
    private var gate = ConnectionGate()
    private var shownWaiting = false
    private var firstSignalAt = 0L
    private var scanner: BluetoothLeScanner? = null
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (firstSignalAt == 0L) {
                firstSignalAt = SystemClock.elapsedRealtime()
                Log.d(TAG, "primeiro sinal do celular ${firstSignalAt - startedAt} ms depois do início do alarme")
            }
            val r = result.rssi.toFloat()
            rssiSum += r; rssiSumSq += r * r; rssiN++
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "escuta do celular falhou: $errorCode")
        }
    }
    private var shown = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // O Android exige startForeground logo após startForegroundService, mesmo que depois a gente desista.
        startForegroundNotification()
        // Um alarme anterior pode ainda estar "ativo" (a vibração pode ter sido cancelada pelo sistema).
        // O novo alarme substitui o antigo em vez de ser ignorado.
        release()

        val test = intent?.getBooleanExtra(EXTRA_TEST, false) == true
        val alarm = if (test) TEST_ALARM else AlarmStore.alarms(this).firstOrNull { it.id == intent?.getStringExtra(EXTRA_ID) }
        if (alarm == null) { // alarme apagado entre o agendamento e o disparo
            stopAlarm()
            return START_NOT_STICKY
        }
        val timeoutMs = if (test) TEST_MS else alarm.timeoutMin * 60_000L
        if (test) begin(alarm, true, timeoutMs) else startIfWorn(alarm, timeoutMs)
        return START_NOT_STICKY
    }

    /**
     * Com o relógio fora do pulso a vibração não acorda ninguém: não dispara e avisa por notificação.
     * Na dúvida (sem sensor, sem resposta em 1,5 s) o alarme toca: errar para o lado de tocar é o seguro.
     */
    private fun startIfWorn(alarm: Alarm, timeoutMs: Long) {
        val sensors = getSystemService(SensorManager::class.java)
        val offbody = sensors.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)
        var decided = false
        val decide = fun(worn: Boolean) {
            if (decided) return
            decided = true
            handler.removeCallbacksAndMessages(null)
            if (worn) begin(alarm, false, timeoutMs) else skipNotWorn(alarm)
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                sensors.unregisterListener(this)
                decide(event.values[0] != 0f) // 0 = fora do pulso, 1 = no pulso
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (offbody == null || !sensors.registerListener(listener, offbody, SensorManager.SENSOR_DELAY_NORMAL)) {
            decide(true)
            return
        }
        handler.postDelayed({ sensors.unregisterListener(listener); decide(true) }, 1_500)
    }

    private fun skipNotWorn(alarm: Alarm) {
        Log.w(TAG, "Relógio fora do pulso: alarme das %02d:%02d não disparou".format(alarm.hour, alarm.minute))
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID + 1,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Alarme não tocou")
                .setContentText("%02d:%02d: o relógio estava fora do pulso".format(alarm.hour, alarm.minute))
                .build(),
        )
        stopAlarm()
    }

    private fun begin(alarm: Alarm, test: Boolean, timeoutMs: Long) {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vibealarm:alarm")
            .apply { acquire(timeoutMs + 5_000) }

        ramp = VibrationRamp.build(alarm.rampSeconds, alarm.intensity).also {
            startedAt = SystemClock.elapsedRealtime()
            vibrate(it)
        }

        goal = alarm.walkSeconds
        if (goal > 0) {
            val sensors = getSystemService(SensorManager::class.java)
            // O acelerômetro só conta nas pausas da vibração: o motor do relógio balança o sensor.
            sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                ?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                ?: Log.w(TAG, "Sem acelerômetro: só o limite de segurança encerra")
            sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                ?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            notifyPhone("/beacon/start")
            startPhoneScan()
            motion = MotionDetector().also { m ->
                var f: MotionDetector.Features? = null
                m.onFeatures = { f = it }
                m.onWindow = { mean, seconds ->
                    // EXPERIMENTAL: RSSI do farol do celular. O ponto X é o sinal nas 3 primeiras janelas do alarme (pessoa
                    // ainda deitada); d é a variação em relação a X. Pouca variação sugere braço mexendo no mesmo lugar.
                    val rssi = if (rssiN > 0) rssiSum / rssiN else Float.NaN
                    val rssiStd = if (rssiN > 0) kotlin.math.sqrt(maxOf(0f, rssiSumSq / rssiN - rssi * rssi)) else 0f
                    gate.onWindow(if (rssiN > 0) rssi else null)
                    val baseRssi = gate.baseline
                    Log.d(
                        TAG,
                        "janela média=%.2f caminhando=%d s v=%.2f h=%.2f gyro=%.2f rssi=%.0f rstd=%.1f rd=%+.1f dB n=%d trava=%s".format(
                            mean, seconds, f?.vertical ?: 0f, f?.horizontal ?: 0f, f?.gyro ?: 0f,
                            rssi, rssiStd, rssi - baseRssi, rssiN, gate.state,
                        ),
                    )
                    rssiSum = 0f; rssiSumSq = 0f; rssiN = 0
                }
            }
        }
        shown = 0

        val label = if (test) now() else "%02d:%02d".format(alarm.hour, alarm.minute)
        AlarmState.set(true, 0, goal, label)
        showScreen()

        // O botão lateral faz o Android cancelar a vibração (CANCELLED_BY_SCREEN_OFF): religa no ponto em que estava.
        screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler.postDelayed(::resume, RESUME_DELAY_MS)
            }
        }
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)

        handler.postDelayed(::stopAlarm, timeoutMs)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            motion.onGyro(event.values[0], event.values[1], event.values[2], isQuiet(event.timestamp))
            return
        }
        val progress = motion.onSample(event.timestamp, event.values[0], event.values[1], event.values[2], isQuiet(event.timestamp))
        if (progress >= goal && !gate.blocks()) {
            release()
            notifyPhone("/beacon/stop")
            AlarmState.complete() // a tela anima até 100% e fecha sozinha
            stopSelf()
        } else {
            // Meta de caminhada atingida, mas a conexão com o celular ainda não variou: segura o anel cheio e avisa.
            val shownNow = minOf(progress, goal)
            val waiting = progress >= goal
            if (shownNow != shown || waiting != shownWaiting) {
                shown = shownNow
                shownWaiting = waiting
                AlarmState.set(true, shownNow, goal, AlarmState.timeLabel, waiting)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    // EXPERIMENTAL: pede ao celular (por mensagem da Data Layer) para começar ou parar de anunciar o farol.
    // O serviço do celular é acordado pelo sistema: não precisa de app aberto nem de serviço em primeiro plano.
    private fun notifyPhone(path: String) {
        val t0 = SystemClock.elapsedRealtime()
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) Log.w(TAG, "nenhum celular conectado para $path")
                nodes.forEach { node ->
                    Wearable.getMessageClient(this).sendMessage(node.id, path, ByteArray(0))
                        .addOnSuccessListener { Log.d(TAG, "mensagem $path entregue ao celular em ${SystemClock.elapsedRealtime() - t0} ms") }
                        .addOnFailureListener { Log.w(TAG, "mensagem $path falhou: ${it.message}") }
                }
            }
            .addOnFailureListener { Log.w(TAG, "sem lista de nós para $path: ${it.message}") }
    }

    // EXPERIMENTAL: escuta o farol Bluetooth LE do app do celular e acumula o RSSI até a próxima janela.
    private fun startPhoneScan() {
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null || !adapter.isEnabled || checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "sem escuta do celular (Bluetooth desligado ou sem permissão)")
            return
        }
        scanner = adapter.bluetoothLeScanner
        scanner?.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(Beacon.UUID)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback,
        )
    }

    /** O motor está parado nesse instante do sensor? (o timestamp do evento usa o relógio elapsedRealtime) */
    private fun isQuiet(sensorTimeNanos: Long): Boolean {
        val r = ramp ?: return false
        return VibrationRamp.isQuiet(r, sensorTimeNanos / 1_000_000 - startedAt)
    }

    private fun resume() {
        val r = ramp ?: return
        vibrate(VibrationRamp.resume(r, SystemClock.elapsedRealtime() - startedAt))
        showScreen()
    }

    private fun vibrate(p: VibrationRamp.Pattern) {
        // USAGE_ALARM evita que o modo Não Perturbe silencie a vibração.
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java).vibrate(
            VibrationEffect.createWaveform(p.timings, p.amplitudes, p.repeatIndex),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
    }

    /** Acende a tela e abre a tela "Despertando". Em segundo plano quem abre é o full-screen intent da notificação. */
    @Suppress("DEPRECATION")
    private fun showScreen() {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "vibealarm:screen")
            .acquire(5_000)
        try {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível abrir a tela do alarme", e)
        }
    }

    /** Desfaz tudo que um alarme em andamento criou: vibração, sensor, timeout, receiver, wake lock e passos. */
    private fun release() {
        handler.removeCallbacksAndMessages(null)
        getSystemService(Vibrator::class.java).cancel()
        getSystemService(SensorManager::class.java).unregisterListener(this)
        screenOff?.let { runCatching { unregisterReceiver(it) } }
        screenOff = null
        wakeLock?.takeIf { it.isHeld }?.release()
        motion = MotionDetector()
        runCatching { scanner?.stopScan(scanCallback) }
        scanner = null
        rssiSum = 0f; rssiSumSq = 0f; rssiN = 0
        gate = ConnectionGate()
        shownWaiting = false
        firstSignalAt = 0L
    }

    private fun stopAlarm() {
        release()
        notifyPhone("/beacon/stop")
        AlarmState.set(false)
        stopSelf()
    }

    override fun onDestroy() {
        release()
        if (!AlarmState.done) AlarmState.set(false) // se terminou por meta, a tela ainda está animando
        super.onDestroy()
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            // Importância alta é exigida para o full-screen intent; continua sem som nem vibração próprios.
            NotificationChannel(CHANNEL_ID, "Alarme", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(false)
                setSound(null, null)
            },
        )
        val screen = PendingIntent.getActivity(this, 0, Intent(this, AlarmActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Levante-se para parar")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setFullScreenIntent(screen, true)
            .build()
    }

    private fun startForegroundNotification() =
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)

    private fun now() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TEST = "test"
        private const val TAG = "AlarmService"
        private const val CHANNEL_ID = "alarm_high"
        private const val NOTIFICATION_ID = 1
        private const val TEST_MS = 15_000L
        private const val RESUME_DELAY_MS = 500L

        // Teste de vibração da tela do relógio: rampa curta, sem meta de caminhada, termina em 15 s.
        private val TEST_ALARM = Alarm("test", 0, 0, emptySet(), "Teste", true, 1, 10, 0, 1, 0)
    }
}
