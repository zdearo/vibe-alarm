package com.zdearo.vibealarm

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Duas páginas, como no mockup: "Próximo" (horário, rótulo e "Pular este") e "Ajustes" (teste de vibração e
 * estado). Passa de uma para a outra deslizando na vertical. Medidas em dp (ver [Ui]) e larguras como fração
 * da tela, para nada ser cortado pela borda redonda.
 */
class MainActivity : Activity() {
    private var k = 1f
    private var screenW = 0
    private var screenH = 0
    private var page = 0
    private lateinit var pages: LinearLayout
    private lateinit var time: TextView
    private lateinit var sub: TextView
    private lateinit var skip: TextView
    private lateinit var syncValue: TextView
    private lateinit var permValue: TextView
    private lateinit var dots: List<View>
    private lateinit var swipe: GestureDetector
    private var nextAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        k = Ui.scale(this)
        screenW = resources.displayMetrics.widthPixels
        screenH = resources.displayMetrics.heightPixels
        if (intent.data?.scheme == "pulso") openFix(intent.data?.lastPathSegment) else requestPermissions(
            buildList {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray(),
            0,
        )

        pages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nextPage(), LinearLayout.LayoutParams(-1, screenH))
            addView(settingsPage(), LinearLayout.LayoutParams(-1, screenH))
        }
        dots = List(2) { View(this).apply { layoutParams = LinearLayout.LayoutParams(px(4f), px(4f)).apply { topMargin = px(3f); bottomMargin = px(3f) } } }
        val dotColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            dots.forEach { addView(it) }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Ui.INK)
            addView(pages, FrameLayout.LayoutParams(-1, screenH * 2))
            addView(dotColumn, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = px(8f) })
        })
        swipe = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vy) < abs(vx) || abs(vy) < 300) return false
                go(if (vy < 0) 1 else 0)
                return true
            }
        })
        paintDots()
    }

    /**
     * Vem do botão da checagem de boa-noite no celular. Neste Galaxy Watch a única tela de bateria que abre por
     * intent é a "Bateria" da Samsung (onde fica "Aplicativos suspensos", mais abaixo; a lista em si não é exportada).
     * Para o resto só existe "detalhes do app": bateria/sobreposição/notificação específicas viram stub ou não abrem.
     */
    private fun openFix(kind: String?) {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        try {
            startActivity(if (kind == "battery" || kind == "suspended") Intent("com.samsung.android.action.OPEN_BATTERY_MAIN") else details)
        } catch (e: ActivityNotFoundException) {
            startActivity(details) // relógio que não é Samsung
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipe.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // Página 1 do mockup: horário, rótulo em muted e o botão "Pular este".
    private fun nextPage(): View {
        time = label(40f, Ui.TEXT, Ui.display(this), spacing = -0.04f)
        sub = label(14f, Ui.MUTED, Ui.body(this, 400))
        skip = label(16f, Ui.TEXT, Ui.body(this, 600)).apply {
            background = pill(Ui.SURFACE)
            setOnClickListener {
                AlarmStore.setSkipUntil(this@MainActivity, nextAt)
                AlarmScheduler.reschedule(this@MainActivity)
                refresh()
            }
            text = "Pular este"
        }
        return column().apply {
            addView(time)
            addView(sub, margin(top = 6f))
            addView(skip, LinearLayout.LayoutParams((screenW * 0.6f).toInt(), px(44f)).apply { topMargin = px(16f) })
        }
    }

    // Página 2 do mockup: só linhas de estado, sem ação.
    private fun settingsPage(): View {
        syncValue = label(13f, Ui.MUTED, Ui.body(this, 400))
        permValue = label(13f, Ui.MUTED, Ui.body(this, 400))
        return column().apply {
            addView(row("Sincronizado", syncValue))
            addView(row("Permissões", permValue))
        }
    }

    private fun row(name: String, value: TextView) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(12f), 0, px(12f), 0)
        background = pill(Ui.SURFACE)
        val nameView = label(13f, Ui.TEXT, Ui.body(this@MainActivity, 600)).apply {
            text = name
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        addView(nameView, LinearLayout.LayoutParams(0, -2, 1f))
        addView(value)
        layoutParams = LinearLayout.LayoutParams((screenW * 0.74f).toInt(), px(40f)).apply { topMargin = px(6f) }
    }

    private fun refresh() {
        val next = Schedule.next(AlarmStore.alarms(this), System.currentTimeMillis(), AlarmStore.skipUntil(this))
        if (next == null) {
            nextAt = 0
            time.text = "--:--"
            sub.text = "Nenhum alarme"
            skip.visibility = View.GONE
        } else {
            nextAt = next.at
            time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(next.at))
            sub.text = listOf(next.alarm.label, inLabel(next.at - System.currentTimeMillis())).filter { it.isNotBlank() }.joinToString(" · ")
            skip.visibility = View.VISIBLE
        }
        val synced = AlarmStore.syncedAt(this)
        syncValue.text = when {
            synced == 0L -> "–"
            isToday(synced) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(synced))
            else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(synced))
        }
        // Nomeia o que falta (cabe uma palavra na linha); o celular mostra a lista completa na checagem de boa-noite.
        val missing = listOfNotNull(
            "atividade".takeIf { checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED },
            "tela".takeIf { !Settings.canDrawOverlays(this) }, // sem isso a tela "Despertando" não abre no disparo
        )
        val ok = missing.isEmpty()
        permValue.text = when (missing.size) {
            0 -> "ok"
            1 -> "falta ${missing[0]}"
            else -> "faltam ${missing.size}"
        }
        permValue.setTextColor(if (ok) Ui.MUTED else Ui.TEXT)
        permValue.typeface = Ui.body(this, if (ok) 400 else 600)
    }

    private fun isToday(t: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = t }
        val b = Calendar.getInstance()
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun go(p: Int) {
        page = p
        pages.animate().translationY(-p * screenH.toFloat()).setDuration(250).start()
        paintDots()
    }

    private fun paintDots() = dots.forEachIndexed { i, d ->
        d.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i == page) Ui.TEXT else Ui.SURFACE) }
    }

    private fun inLabel(ms: Long): String {
        val min = ms / 60_000
        return if (min >= 60) "em ${min / 60} h ${min % 60} min" else "em $min min"
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }

    private fun label(sizeDp: Float, color: Int, face: Typeface, spacing: Float = 0f) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeDp * k)
        setTextColor(color)
        typeface = face
        letterSpacing = spacing
        includeFontPadding = false
        gravity = Gravity.CENTER
        maxLines = 1
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = px(24f).toFloat()
    }

    private fun margin(top: Float = 0f, bottom: Float = 0f) =
        LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(top); bottomMargin = px(bottom) }

    private fun px(v: Float) = (v * k).toInt()
}
